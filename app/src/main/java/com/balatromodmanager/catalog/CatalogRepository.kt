package com.balatromodmanager.catalog

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant

class CatalogRepository(
    private val appContext: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    private val readmeCacheMutex = Mutex()

    suspend fun loadLocalSnapshot(): CatalogSnapshot = withContext(Dispatchers.IO) {
        loadCachedSnapshotOrEmpty()
    }

    fun isCacheStale(maxAgeMs: Long = CACHE_MAX_AGE_MS): Boolean {
        val cached = cacheFile()
        if (!cached.exists()) return true
        val needsFrameworkMigration = runCatching {
            json.decodeFromString(CatalogSnapshot.serializer(), cached.readText()).mods
        }.getOrDefault(emptyList()).let { mods ->
            mods.any { it.author.equals("Steamopollys", ignoreCase = true) && it.title.equals("Steamodded", ignoreCase = true) } &&
                mods.none { it.author.equals("Steamodded", ignoreCase = true) && it.title.equals("Steamodded", ignoreCase = true) }
        }
        if (needsFrameworkMigration) return true
        val ageMs = System.currentTimeMillis() - cached.lastModified()
        return ageMs < 0 || ageMs >= maxAgeMs
    }

    suspend fun refreshSnapshot(): CatalogSnapshot = withContext(Dispatchers.IO) {
        loadCachedSnapshotOrEmpty()
        val snapshot = fetchOnlineCatalog()
        writeCacheAtomically(snapshot)
        snapshot
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (file.exists()) file.delete()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        if (temporary.exists()) temporary.delete()
        readmeCacheDirectory().deleteRecursively()
        appContext.imageLoader.memoryCache?.clear()
        appContext.imageLoader.diskCache?.clear()
    }

    suspend fun readCachedVersionReadme(
        mod: CatalogMod,
        versionNumber: String,
    ): CatalogReadmeResult? = withContext(Dispatchers.IO) {
        val cacheFile = readmeCacheFile(mod, versionNumber)
        val markdown = readmeCacheMutex.withLock {
            cacheFile.takeIf(File::exists)?.readText()
        } ?: return@withContext null
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        CatalogReadmeResult(
            markdown = markdown,
            available = markdown.isNotBlank(),
            isFresh = age in 0 until README_CACHE_MAX_AGE_MS,
        )
    }

    suspend fun refreshModDetails(mod: CatalogMod, selectedVersion: String): CatalogMod =
        withContext(Dispatchers.IO) {
            val latest = if (mod.packageUuid.isNotBlank()) {
                fetchPackage(mod.packageUuid).toCatalogMod()
                    ?: throw IllegalStateException("${mod.title} is no longer available on Thunderstore.")
            } else mod
            var refreshed = latest
            listOf(latest.version, selectedVersion).distinct().forEach { versionNumber ->
                if (refreshed.versions.any { it.versionNumber == versionNumber }) {
                    val readme = loadVersionReadme(refreshed, versionNumber, forceRefresh = true)
                    refreshed = refreshed.withReadme(versionNumber, readme.markdown)
                }
            }
            refreshed
        }

    suspend fun loadVersionReadme(
        mod: CatalogMod,
        versionNumber: String,
        forceRefresh: Boolean = false,
    ): CatalogReadmeResult = withContext(Dispatchers.IO) {
        val cached = readCachedVersionReadme(mod, versionNumber)
        val cacheFile = readmeCacheFile(mod, versionNumber)
        if (!forceRefresh && cached?.isFresh == true) return@withContext cached

        try {
            val markdown = fetchReadme(mod.author, mod.title, versionNumber)
            readmeCacheMutex.withLock {
                cacheFile.parentFile?.mkdirs()
                val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                temporary.writeText(markdown)
                if (!temporary.renameTo(cacheFile)) {
                    temporary.copyTo(cacheFile, overwrite = true)
                    temporary.delete()
                }
            }
            CatalogReadmeResult(markdown, available = markdown.isNotBlank())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cached ?: CatalogReadmeResult(markdown = "", available = false)
        }
    }

    private fun CatalogMod.withReadme(versionNumber: String, markdown: String): CatalogMod = copy(
        versions = versions.map { version ->
            if (version.versionNumber == versionNumber) {
                version.copy(
                    readme = markdown,
                    readmeLoaded = true,
                    hasFullReadme = markdown.isNotBlank(),
                )
            } else version
        },
    )

    suspend fun resolveForInstall(mod: CatalogMod): CatalogMod = withContext(Dispatchers.IO) {
        if (mod.packageUuid.isBlank()) {
            if (mod.downloadUrl.startsWith("https://", ignoreCase = true)) return@withContext mod
            throw IllegalStateException("Thunderstore did not provide a download for ${mod.title}.")
        }
        val latestPackage = fetchPackage(mod.packageUuid).toCatalogMod()
            ?: throw IllegalStateException("${mod.title} is no longer available on Thunderstore.")
        val targetVersion = mod.requestedVersionNumber?.let { requested ->
            latestPackage.versions.firstOrNull { it.versionNumber == requested }
                ?: throw IllegalStateException("Version ${mod.requestedVersionNumber} of ${mod.title} is no longer available on Thunderstore.")
        } ?: latestPackage.versions.firstOrNull()
        targetVersion?.let(latestPackage::forVersion) ?: latestPackage
    }

    private fun loadCachedSnapshotOrEmpty(): CatalogSnapshot {
        val cached = cacheFile()
        if (cached.exists()) {
            runCatching {
                json.decodeFromString(CatalogSnapshot.serializer(), cached.readText())
            }.onSuccess { snapshot ->
                if (snapshot.schemaVersion == CATALOG_SCHEMA_VERSION && snapshot.source == THUNDERSTORE_PACKAGE_API) {
                    return snapshot.deduplicated()
                }
            }
            // The cache belongs to a different catalog schema or is unreadable.
            cached.delete()
        }
        return emptySnapshot()
    }

    private fun emptySnapshot() = CatalogSnapshot(
        schemaVersion = CATALOG_SCHEMA_VERSION,
        source = THUNDERSTORE_PACKAGE_API,
        generatedAt = "",
        mods = emptyList(),
    )

    private fun writeCacheAtomically(snapshot: CatalogSnapshot) {
        val target = cacheFile()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(json.encodeToString(CatalogSnapshot.serializer(), snapshot))
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun fetchOnlineCatalog(): CatalogSnapshot {
        val onlineMods = fetchPackages().mapNotNull { packageInfo ->
            if (packageInfo.isDeprecated) return@mapNotNull null
            packageInfo.toCatalogMod()
        }

        check(onlineMods.isNotEmpty()) { "Thunderstore returned no active Balatro packages." }

        return CatalogSnapshot(
            schemaVersion = CATALOG_SCHEMA_VERSION,
            source = THUNDERSTORE_PACKAGE_API,
            generatedAt = Instant.now().toString(),
            mods = onlineMods.deduplicatedCatalog(),
        )
    }

    private fun CatalogSnapshot.deduplicated(): CatalogSnapshot = copy(
        mods = mods
            .filterNot { it.id.lowercase() in UNAVAILABLE_ON_MOBILE }
            .deduplicatedCatalog(),
    )

    private fun fetchPackages(): List<ThunderstorePackage> {
        val connection = openGet(THUNDERSTORE_PACKAGE_API)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Thunderstore catalog returned HTTP $status.")
            }
            return connection.inputStream.use { input ->
                json.decodeFromString(ListSerializer(ThunderstorePackage.serializer()), input.bufferedReader().readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchPackage(uuid: String): ThunderstorePackage {
        val url = "$THUNDERSTORE_PACKAGE_API${uuid.encodedPathSegment()}/"
        val connection = openGet(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Thunderstore package details returned HTTP $status.")
            }
            return connection.inputStream.use { input ->
                json.decodeFromString(ThunderstorePackage.serializer(), input.bufferedReader().readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchReadme(namespace: String, packageName: String, version: String): String {
        val url = buildString {
            append(THUNDERSTORE_README_API)
            append(namespace.encodedPathSegment()).append('/')
            append(packageName.encodedPathSegment()).append('/')
            append(version.encodedPathSegment()).append("/readme/")
        }
        val connection = openGet(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Thunderstore README returned HTTP $status.")
            }
            return connection.inputStream.use { input ->
                json.decodeFromString(ThunderstoreReadmeResponse.serializer(), input.bufferedReader().readText())
                    .markdown
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openGet(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json")
    }

    private fun ThunderstorePackage.toCatalogMod(): CatalogMod? {
        if (isDeprecated) return null
        if (isUnavailableOnMobile()) return null
        // Thunderstore and r2modman treat the first version as the package's
        // current version; keeping all displayed metadata tied to it avoids a
        // stale description/icon when the active flag lags behind the index.
        val latest = versions.firstOrNull() ?: return null
        val title = name.trim().ifBlank { return null }
        val authorName = owner.trim()
        val packageId = catalogId()
        if (packageId.isBlank()) return null

        val description = latest.description.trim()
        val dependencies = latest.dependencies
        val icon = latest.icon
        val lastUpdated = dateUpdated.toEpochMillis().takeIf { it > 0 }
            ?: latest.dateCreated.toEpochMillis()

        return CatalogMod(
            id = packageId,
            title = title,
            author = authorName,
            categories = categories,
            repo = latest.websiteUrl.ifBlank { packageUrl },
            downloadUrl = latest.downloadUrl,
            folderName = title,
            version = latest.versionNumber,
            requiresSteamodded = dependencies.any { it.isSteamoddedDependency() },
            requiresAmulet = dependencies.any { it.isAmuletDependency() },
            requiredPackages = dependencies.distinct(),
            lastUpdated = lastUpdated,
            downloadsTotal = versions.sumOf { it.downloads.coerceAtLeast(0) },
            thumbnailUrl = icon,
            summary = description.previewParagraph(),
            description = description,
            packageUuid = uuid,
            versions = versions.map { version ->
                CatalogVersion(
                    versionNumber = version.versionNumber,
                    description = version.description.trim(),
                    downloadUrl = version.downloadUrl,
                    dependencies = version.dependencies,
                    dateCreated = version.dateCreated,
                    downloads = version.downloads,
                    fileSize = version.fileSize,
                )
            },
        )
    }

    private fun ThunderstorePackage.catalogId(): String {
        val packageName = name.trim()
        val packageOwner = owner.trim()
        return when {
            packageOwner.isNotBlank() && packageName.isNotBlank() -> "$packageOwner@$packageName"
            fullName.isNotBlank() -> fullName
            else -> packageName
        }
    }

    private fun ThunderstorePackage.isUnavailableOnMobile(): Boolean {
        val packageId = "${owner.trim()}@${name.trim()}".lowercase()
        return packageId in UNAVAILABLE_ON_MOBILE
    }

    private fun String.isSteamoddedDependency(): Boolean =
        startsWith("Steamodded-Steamodded-", ignoreCase = true) ||
            startsWith("Steamopollys-Steamodded-", ignoreCase = true)

    private fun String.isAmuletDependency(): Boolean =
        startsWith("just_frostice482-Amulet-", ignoreCase = true) ||
            startsWith("MathIsFun0-Talisman-", ignoreCase = true)

    private fun String.previewParagraph(): String {
        val candidates = split(Regex("\\n\\s*\\n"))
        return candidates.asSequence().mapNotNull { block ->
            val text = block.lineSequence()
                .map(String::trim)
                .filter { line ->
                    line.isNotBlank() &&
                        !line.startsWith('#') &&
                        !line.startsWith("![") &&
                        !line.startsWith("<") &&
                        !line.startsWith("```") &&
                        !line.startsWith("~~~") &&
                        !line.matches(Regex("[-*_]{3,}")) &&
                        !line.startsWith("| ") &&
                        !line.startsWith("[!") &&
                        !line.removePrefix("> ").startsWith("[!")
                }
                .map { line ->
                    line.removePrefix("> ")
                        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "")
                        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                        .replace(Regex("<[^>]+>"), "")
                        .replace(Regex("^\\s*[-*+]\\s+"), "")
                        .replace(Regex("^\\s*\\d+[.)]\\s+"), "")
                        .replace(Regex("[`*_~]"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                .filter(String::isNotBlank)
                .joinToString(" ")
                .take(180)
                .trim()
            text.takeIf(String::isNotBlank)
        }.firstOrNull().orEmpty()
    }

    private fun String.toEpochMillis(): Long =
        runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0)

    private fun String.encodedPathSegment(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

    @kotlinx.serialization.Serializable
    private data class ThunderstoreReadmeResponse(val markdown: String = "")

    private fun cacheFile(): File = File(appContext.filesDir, "catalog-cache.json")

    private fun readmeCacheFile(mod: CatalogMod, versionNumber: String): File {
        val key = "${mod.id.lowercase()}@$versionNumber"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(readmeCacheDirectory(), "$digest.md")
    }

    private fun readmeCacheDirectory(): File = File(appContext.filesDir, "readme-cache")

    private companion object {
        const val THUNDERSTORE_PACKAGE_API = "https://thunderstore.io/c/balatro/api/v1/package/"
        const val THUNDERSTORE_README_API = "https://thunderstore.io/api/experimental/package/"
        const val CATALOG_SCHEMA_VERSION = 7
        const val CACHE_MAX_AGE_MS = 60L * 60L * 1_000L
        const val README_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        const val USER_AGENT = "BalatroModManagerAndroid/0.2.1"
        // Keep this list explicit: package categories do not reliably describe mobile compatibility.
        val UNAVAILABLE_ON_MOBILE = setOf(
            "ebkr@r2modman",
            "kesomannen@galemodmanager",
            "thunderstore@lovely",
            "balatromultiplayer@multiplayerapi", // startup and auth both require Steam (G.STEAM / Steam ticket)
            "balatromultiplayer@multiplayerspeedrun", // depends on MultiplayerAPI
            "dshad@balatrovs", // Thunderstore archive is Windows-only; Android build is a separate GitHub release
        )
    }
}
