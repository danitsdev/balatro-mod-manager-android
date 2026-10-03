package com.balatromodmanager.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogSnapshot(
    val schemaVersion: Int,
    val source: String,
    val generatedAt: String,
    val mods: List<CatalogMod>,
)

@Serializable
data class ThunderstorePackage(
    val name: String = "",
    @SerialName("full_name")
    val fullName: String = "",
    val owner: String = "",
    @SerialName("package_url")
    val packageUrl: String = "",
    @SerialName("date_updated")
    val dateUpdated: String = "",
    @SerialName("uuid4")
    val uuid: String = "",
    @SerialName("is_deprecated")
    val isDeprecated: Boolean = false,
    val categories: List<String> = emptyList(),
    val versions: List<ThunderstorePackageVersion> = emptyList(),
)

@Serializable
data class ThunderstorePackageVersion(
    val description: String = "",
    val icon: String = "",
    @SerialName("version_number")
    val versionNumber: String = "",
    val dependencies: List<String> = emptyList(),
    @SerialName("download_url")
    val downloadUrl: String = "",
    val downloads: Long = 0,
    @SerialName("date_created")
    val dateCreated: String = "",
    @SerialName("file_size")
    val fileSize: Long = 0,
    @SerialName("website_url")
    val websiteUrl: String = "",
)

@Serializable
data class CatalogVersion(
    val versionNumber: String = "",
    val description: String = "",
    val downloadUrl: String = "",
    val dependencies: List<String> = emptyList(),
    val dateCreated: String = "",
    val downloads: Long = 0,
    val fileSize: Long = 0,
    val readme: String = "",
    val readmeLoaded: Boolean = false,
    val hasFullReadme: Boolean = false,
)

data class CatalogReadmeResult(
    val markdown: String,
    val available: Boolean,
    val isFresh: Boolean = true,
)

@Serializable
data class CatalogMod(
    val id: String,
    val title: String,
    val author: String,
    val categories: List<String> = emptyList(),
    val repo: String = "",
    val downloadUrl: String = "",
    val folderName: String = "",
    val version: String = "",
    val requiresSteamodded: Boolean = false,
    val requiresAmulet: Boolean = false,
    val requiredPackages: List<String> = emptyList(),
    val lastUpdated: Long = 0,
    val downloadsTotal: Long = 0,
    val thumbnailUrl: String = "",
    val summary: String = "",
    val description: String = "",
    val packageUuid: String = "",
    val versions: List<CatalogVersion> = emptyList(),
    val requestedVersionNumber: String? = null,
) {
    val installFolder: String
        get() = folderName.ifBlank { title }.sanitizeFolderName()

    val supportsAutomaticInstall: Boolean
        get() {
            if (!downloadUrl.startsWith("https://", ignoreCase = true)) return false

            // GitHub/codeload archive URLs commonly end in a tag or commit rather
            // than ".zip". The installer validates the downloaded ZIP contents
            // before staging, so the URL suffix is not a reliable safety check.
            val path = downloadUrl.substringBefore('?').substringBefore('#').lowercase()
            return UnsupportedArchiveSuffixes.none { suffix -> path.endsWith(suffix) }
        }

    val searchableText: String
        get() = buildString {
            append(title).append(' ')
            append(author).append(' ')
            append(summary).append(' ')
            append(description.take(300))
        }
}

fun CatalogMod.forVersion(version: CatalogVersion): CatalogMod = copy(
    version = version.versionNumber,
    downloadUrl = version.downloadUrl,
    requiredPackages = version.dependencies,
    requiresSteamodded = version.dependencies.any { it.isSteamoddedDependency() },
    requiresAmulet = version.dependencies.any { it.isAmuletDependency() },
    requestedVersionNumber = version.versionNumber,
)

private fun String.isSteamoddedDependency(): Boolean =
    startsWith("Steamodded-Steamodded-", ignoreCase = true) ||
        startsWith("Steamopollys-Steamodded-", ignoreCase = true)

private fun String.isAmuletDependency(): Boolean =
    startsWith("just_frostice482-Amulet-", ignoreCase = true) ||
        startsWith("MathIsFun0-Talisman-", ignoreCase = true)

private val UnsupportedArchiveSuffixes = listOf(
    ".7z",
    ".rar",
    ".tar",
    ".tar.gz",
    ".tgz",
)

internal fun List<CatalogMod>.deduplicatedCatalog(): List<CatalogMod> {
    val byTitle = linkedMapOf<String, CatalogMod>()
    for (incoming in this) {
        val key = incoming.title.trim().lowercase()
        val existing = byTitle[key]
        byTitle[key] = if (existing == null) incoming else preferredDuplicate(existing, incoming)
    }
    return byTitle.values.toList()
}

private fun preferredDuplicate(existing: CatalogMod, incoming: CatalogMod): CatalogMod {
    return when {
        incoming.isCanonicalSteamoddedPackage() != existing.isCanonicalSteamoddedPackage() ->
            if (incoming.isCanonicalSteamoddedPackage()) incoming else existing
        incoming.downloadsTotal != existing.downloadsTotal ->
            if (incoming.downloadsTotal > existing.downloadsTotal) incoming else existing
        incoming.hasCatalogThumbnail() != existing.hasCatalogThumbnail() ->
            if (incoming.hasCatalogThumbnail()) incoming else existing
        incoming.lastUpdated != existing.lastUpdated ->
            if (incoming.lastUpdated > existing.lastUpdated) incoming else existing
        else -> incoming
    }
}

private fun CatalogMod.isCanonicalSteamoddedPackage(): Boolean =
    author.equals("Steamodded", ignoreCase = true) && title.equals("Steamodded", ignoreCase = true)

private fun CatalogMod.hasCatalogThumbnail(): Boolean = thumbnailUrl.trim().isNotEmpty()

@Serializable
data class ManagedInstallManifest(
    val schemaVersion: Int = 1,
    val modId: String,
    val title: String,
    val folderName: String,
    val version: String,
    val sourceUrl: String,
    val installedAtEpochMs: Long,
    val fileCount: Int,
    val totalBytes: Long,
)

fun String.sanitizeFolderName(): String {
    val cleaned = trim()
        .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
        .trim('.', ' ')
    return cleaned.ifBlank { "UnknownMod" }.take(80)
}
