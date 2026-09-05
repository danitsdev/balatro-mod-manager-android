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
data class BmiModsPage(
    val items: List<BmiModItem> = emptyList(),
    @SerialName("next_cursor")
    val nextCursor: String? = null,
)

@Serializable
data class BmiModItem(
    val id: String? = null,
    @SerialName("dir_name")
    val dirName: String? = null,
    val name: String? = null,
    val author: String? = null,
    val version: String? = null,
    val summary: String? = null,
    val description: String? = null,
    @SerialName("description_html")
    val descriptionHtml: String? = null,
    val homepage: String? = null,
    val repo: String? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("folder_name")
    val folderName: String? = null,
    @SerialName("updated_at")
    val updatedAt: Long? = 0,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    val categories: List<String>? = emptyList(),
    @SerialName("requires_steamodded")
    val requiresSteamodded: Boolean? = false,
    @SerialName("requires_talisman")
    val requiresTalisman: Boolean? = false,
    val downloads: CatalogDownloads? = CatalogDownloads(),
)

@Serializable
data class BmiDownloadResponse(
    @SerialName("download_url")
    val downloadUrl: String = "",
    val url: String = "",
)

@Serializable
data class CatalogDownloads(
    val total: Long = 0,
    val today: Long = 0,
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
    val requiresTalisman: Boolean = false,
    val automaticVersionCheck: Boolean = false,
    val lastUpdated: Long = 0,
    val downloadsTotal: Long = 0,
    val downloadsToday: Long = 0,
    val thumbnailUrl: String = "",
    val summary: String = "",
    val description: String = "",
) {
    val installFolder: String
        get() = folderName.ifBlank { title }.sanitizeFolderName()

    val supportsAutomaticInstall: Boolean
        get() {
            if (downloadUrl.startsWith("bmi://", ignoreCase = true)) return true
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
        incoming.downloadsTotal != existing.downloadsTotal ->
            if (incoming.downloadsTotal > existing.downloadsTotal) incoming else existing
        incoming.hasCatalogThumbnail() != existing.hasCatalogThumbnail() ->
            if (incoming.hasCatalogThumbnail()) incoming else existing
        incoming.lastUpdated != existing.lastUpdated ->
            if (incoming.lastUpdated > existing.lastUpdated) incoming else existing
        else -> incoming
    }
}

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
