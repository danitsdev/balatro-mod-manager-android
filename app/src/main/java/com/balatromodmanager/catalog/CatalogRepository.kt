package com.balatromodmanager.catalog

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class CatalogRepository(
    private val appContext: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    suspend fun loadLocalSnapshot(): CatalogSnapshot = withContext(Dispatchers.IO) {
        loadCachedSnapshotOrEmpty()
    }

    fun isCacheStale(maxAgeMs: Long = CACHE_MAX_AGE_MS): Boolean {
        val cached = cacheFile()
        if (!cached.exists()) return true
        val ageMs = System.currentTimeMillis() - cached.lastModified()
        return ageMs < 0 || ageMs >= maxAgeMs
    }

    suspend fun refreshSnapshot(): CatalogSnapshot = withContext(Dispatchers.IO) {
        val base = loadCachedSnapshotOrEmpty()
        val snapshot = fetchOnlineCatalog(base)
        writeCacheAtomically(snapshot)
        snapshot
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (file.exists()) file.delete()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        if (temporary.exists()) temporary.delete()
        appContext.imageLoader.memoryCache?.clear()
        appContext.imageLoader.diskCache?.clear()
    }

    suspend fun hydrateMod(mod: CatalogMod): CatalogMod = withContext(Dispatchers.IO) {
        val id = mod.bmiId()
        if (id.isBlank()) return@withContext mod
        val detail = fetchMod(id).toCatalogMod(existing = mod, useBmiDownloadPlaceholder = true)
        mod.mergeHydrated(detail)
    }

    suspend fun resolveForInstall(mod: CatalogMod): CatalogMod = withContext(Dispatchers.IO) {
        val url = mod.downloadUrl.trim()
        if (url.startsWith("https://")) return@withContext mod

        val id = mod.bmiId()
        if (id.isBlank()) return@withContext mod
        mod.copy(downloadUrl = postDownload(id))
    }

    private fun loadCachedSnapshotOrEmpty(): CatalogSnapshot {
        val cached = cacheFile()
        if (cached.exists()) {
            runCatching {
                return json.decodeFromString(CatalogSnapshot.serializer(), cached.readText()).deduplicated()
            }.onFailure {
                // A corrupt cache should never prevent a fresh BMI synchronization.
                cached.delete()
            }
        }
        return CatalogSnapshot(
            schemaVersion = 3,
            source = BMI_BASE_URL,
            generatedAt = "",
            mods = emptyList(),
        )
    }

    private fun writeCacheAtomically(snapshot: CatalogSnapshot) {
        val target = cacheFile()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(json.encodeToString(CatalogSnapshot.serializer(), snapshot))
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun fetchOnlineCatalog(base: CatalogSnapshot): CatalogSnapshot {
        val baseById = base.mods.associateBy { it.id }.toMutableMap()
        val onlineMods = mutableListOf<CatalogMod>()
        var cursor: String? = null
        do {
            val page = fetchModsPage(cursor)
            page.items.forEach { item ->
                val id = item.id.orEmpty().ifBlank { item.dirName.orEmpty() }
                if (id.isBlank()) return@forEach
                val existing = baseById.remove(id)
                onlineMods += item.toCatalogMod(existing, useBmiDownloadPlaceholder = true)
            }
            cursor = page.nextCursor
        } while (!cursor.isNullOrBlank())

        check(onlineMods.isNotEmpty()) { "BMI returned an empty catalog." }

        return CatalogSnapshot(
            schemaVersion = 3,
            source = BMI_BASE_URL,
            generatedAt = Instant.now().toString(),
            mods = onlineMods.deduplicatedCatalog(),
        )
    }

    private fun CatalogSnapshot.deduplicated(): CatalogSnapshot = copy(mods = mods.deduplicatedCatalog())

    private fun fetchModsPage(cursor: String?): BmiModsPage {
        val params = mutableListOf("limit=200", "sort=downloads_desc")
        if (!cursor.isNullOrBlank()) {
            params += "cursor=${URLEncoder.encode(cursor, Charsets.UTF_8.name())}"
        }
        val url = URL("$BMI_BASE_URL/mods?${params.joinToString("&")}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BalatroModManagerAndroid/0.1")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("BMI catalog returned HTTP $status.")
            }
            return connection.inputStream.use { input ->
                json.decodeFromString(BmiModsPage.serializer(), input.bufferedReader().readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchMod(id: String): BmiModItem {
        val url = URL("$BMI_BASE_URL/mods/${id.encodedPathSegment()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BalatroModManagerAndroid/0.1")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("BMI mod detail returned HTTP $status.")
            }
            return connection.inputStream.use { input ->
                json.decodeFromString(BmiModItem.serializer(), input.bufferedReader().readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun postDownload(id: String): String {
        val url = URL("$BMI_BASE_URL/mods/${id.encodedPathSegment()}/download")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BalatroModManagerAndroid/0.1")
        }
        try {
            val status = connection.responseCode
            if (status == 204) {
                return fetchMod(id).downloadUrl.orEmpty().ifBlank {
                    throw IllegalStateException("BMI did not return a download URL for $id.")
                }
            }
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { input ->
                input.bufferedReader().readText()
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("BMI download returned HTTP $status.")
            }
            val parsed = runCatching {
                json.decodeFromString(BmiDownloadResponse.serializer(), body)
            }.getOrNull()
            return parsed?.downloadUrl?.ifBlank { parsed.url }.orEmpty().ifBlank {
                fetchMod(id).downloadUrl.orEmpty().ifBlank {
                    throw IllegalStateException("BMI did not return a download URL for $id.")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun BmiModItem.toCatalogMod(existing: CatalogMod?, useBmiDownloadPlaceholder: Boolean): CatalogMod {
        val itemId = id.orEmpty()
        val itemDirName = dirName.orEmpty()
        val itemDescriptionHtml = descriptionHtml.orEmpty()
        val itemDescription = description.orEmpty()
        val itemName = name.orEmpty()
        val itemAuthor = author.orEmpty()
        val itemCategories = categories.orEmpty()
        val itemRepo = repo.orEmpty()
        val itemHomepage = homepage.orEmpty()
        val itemDownloadUrl = downloadUrl.orEmpty()
        val itemFolderName = folderName.orEmpty()
        val itemVersion = version.orEmpty()
        val itemThumbnailUrl = thumbnailUrl.orEmpty()
        val itemSummary = summary.orEmpty()

        val normalizedId = itemId.ifBlank { itemDirName }
        val htmlMarkdown = itemDescriptionHtml.htmlToMarkdown()
        val detailDescription = when {
            itemDescription.isBlank() -> htmlMarkdown
            htmlMarkdown.isBlank() -> itemDescription
            itemDescription.plainDescriptionLength() > htmlMarkdown.plainDescriptionLength() * 3 / 2 -> itemDescription
            else -> htmlMarkdown
        }
        val bmiDownload = if (useBmiDownloadPlaceholder && normalizedId.isNotBlank()) "bmi://$normalizedId" else ""
        return CatalogMod(
            id = normalizedId,
            title = itemName.ifBlank { existing?.title ?: normalizedId },
            author = itemAuthor.ifBlank { existing?.author ?: "" },
            categories = itemCategories.ifEmpty { existing?.categories ?: emptyList() },
            repo = itemRepo.ifBlank { itemHomepage }.ifBlank { existing?.repo ?: "" },
            downloadUrl = bmiDownload.ifBlank { itemDownloadUrl.ifBlank { existing?.downloadUrl ?: "" } },
            folderName = itemFolderName.ifBlank { existing?.folderName ?: itemName.ifBlank { normalizedId } },
            version = itemVersion.ifBlank { existing?.version ?: "" },
            requiresSteamodded = (requiresSteamodded == true) || existing?.requiresSteamodded == true,
            requiresTalisman = (requiresTalisman == true) || existing?.requiresTalisman == true,
            automaticVersionCheck = existing?.automaticVersionCheck ?: true,
            lastUpdated = (updatedAt ?: 0).takeIf { it > 0 } ?: existing?.lastUpdated ?: 0,
            downloadsTotal = (downloads?.total ?: 0).takeIf { it > 0 } ?: existing?.downloadsTotal ?: 0,
            downloadsToday = (downloads?.today ?: 0).takeIf { it > 0 } ?: existing?.downloadsToday ?: 0,
            thumbnailUrl = itemThumbnailUrl.normalizedBmiUrl().ifBlank { existing?.thumbnailUrl ?: "" },
            summary = itemSummary.ifBlank { existing?.summary ?: "" },
            description = detailDescription.ifBlank { existing?.description ?: "" },
        )
    }

    private fun CatalogMod.mergeHydrated(detail: CatalogMod): CatalogMod {
        return copy(
            title = detail.title.ifBlank { title },
            author = detail.author.ifBlank { author },
            categories = detail.categories.ifEmpty { categories },
            repo = detail.repo.ifBlank { repo },
            downloadUrl = detail.downloadUrl.ifBlank { downloadUrl },
            folderName = detail.folderName.ifBlank { folderName },
            version = detail.version.ifBlank { version },
            requiresSteamodded = requiresSteamodded || detail.requiresSteamodded,
            requiresTalisman = requiresTalisman || detail.requiresTalisman,
            lastUpdated = detail.lastUpdated.takeIf { it > 0 } ?: lastUpdated,
            downloadsTotal = detail.downloadsTotal.takeIf { it > 0 } ?: downloadsTotal,
            downloadsToday = detail.downloadsToday.takeIf { it > 0 } ?: downloadsToday,
            thumbnailUrl = detail.thumbnailUrl.ifBlank { thumbnailUrl },
            summary = detail.summary.ifBlank { summary },
            description = description.bestStructuredDescription(detail.description),
        )
    }

    private fun String.normalizedBmiUrl(): String {
        val value = trim()
        return when {
            value.startsWith("https://") -> value
            value.startsWith("/") -> BMI_BASE_URL + value.normalizedThumbnailPath()
            else -> ""
        }
    }

    private fun String.normalizedThumbnailPath(): String {
        if (!startsWith("/thumbnails/") || !endsWith(".webp")) return this
        val rawName = removePrefix("/thumbnails/").removeSuffix(".webp")
        val encoded = rawName.encodedPathSegment()
        return "/thumbnails/$encoded.webp"
    }

    private fun String.encodedPathSegment(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun CatalogMod.bmiId(): String {
        return downloadUrl.substringAfter("://").takeIf { downloadUrl.startsWith("bmi://", ignoreCase = true) }
            ?: id.takeIf { it.contains("@") }
            ?: ""
    }

    private fun String.htmlToMarkdown(): String {
        if (isBlank()) return ""
        return Jsoup.parseBodyFragment(this).body().childNodes()
            .joinToString("") { it.toMarkdown() }
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun Node.toMarkdown(): String = when (this) {
        is TextNode -> wholeText
        is Element -> {
            val inner = childNodes().joinToString("") { it.toMarkdown() }.trim()
            when (tagName().lowercase()) {
                "h1" -> "# $inner\n\n"
                "h2" -> "## $inner\n\n"
                "h3" -> "### $inner\n\n"
                "h4", "h5", "h6" -> "#### $inner\n\n"
                "p", "div", "section" -> "$inner\n\n"
                "br" -> "  \n"
                "ul", "ol" -> "$inner\n"
                "li" -> "- $inner\n"
                "strong", "b" -> "**$inner**"
                "em", "i" -> "*$inner*"
                "code" -> "`$inner`"
                "pre" -> "```\n$inner\n```\n\n"
                "blockquote" -> inner.lines().joinToString("\n") { "> $it" } + "\n\n"
                "a" -> "[$inner](${attr("href")})"
                "img" -> "![${attr("alt")}](${attr("src")})\n\n"
                else -> inner
            }
        }
        else -> ""
    }

    private fun String.plainDescriptionLength(): Int = Jsoup.parse(this).text().length

    private fun String.bestStructuredDescription(candidate: String): String {
        if (isBlank()) return candidate
        if (candidate.isBlank()) return this
        val currentStructure = count { it == '#' || it == '\n' } + Regex("""\[[^]]+]\([^)]+\)""").findAll(this).count() * 3
        val candidateStructure = countStructure(candidate)
        return if (candidateStructure > currentStructure) candidate else this
    }

    private fun countStructure(value: String): Int =
        value.count { it == '#' || it == '\n' } + Regex("""\[[^]]+]\([^)]+\)""").findAll(value).count() * 3

    private fun cacheFile(): File = File(appContext.filesDir, "catalog-cache.json")

    private companion object {
        const val BMI_BASE_URL = "https://api-bmi.dasguney.com"
        const val CACHE_MAX_AGE_MS = 60L * 60L * 1_000L
    }
}
