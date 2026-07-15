package com.balatromobilemodmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.domain.matchKeys
import com.balatromobilemodmanager.domain.matchesLocal
import com.balatromobilemodmanager.domain.simplifiedModKey
import com.balatromobilemodmanager.installer.LocalModStatus
import com.balatromobilemodmanager.storage.TreeValidation
import com.balatromobilemodmanager.ui.theme.BmmColor
import androidx.compose.ui.graphics.Color

// --- Dependency helpers ---

internal data class DependencyStatus(
    val steamoddedInstalled: Boolean,
    val amuletInstalled: Boolean,
)

internal fun CatalogMod.missingDependencies(dependencies: DependencyStatus): List<String> = buildList {
    if (requiresSteamodded && !dependencies.steamoddedInstalled) add("Steamodded")
    if (requiresTalisman && !dependencies.amuletInstalled) add("Amulet")
}

internal fun List<CatalogMod>.findDependencyMod(name: String): CatalogMod? {
    val searchName = if (name.equals("Talisman", ignoreCase = true)) "Amulet" else name
    return firstOrNull { it.title.equals(searchName, ignoreCase = true) }
        ?: firstOrNull { it.folderName.equals(if (searchName == "Steamodded") "smods" else searchName, ignoreCase = true) }
}

// --- CatalogMod extensions ---

internal fun CatalogMod.managedManifest(state: MainUiState): ManagedInstallManifest? {
    val keys = matchKeys()
    return state.managedInstalls.firstOrNull { manifest ->
        manifest.modId.equals(id, ignoreCase = true) ||
            manifest.folderName.simplifiedModKey() in keys
    }
}

internal fun CatalogMod.isInstalled(state: MainUiState): Boolean {
    val validation = state.validation as? TreeValidation.Valid
    return managedManifest(state) != null ||
        state.localMods.any { matchesLocal(it) } ||
        validation?.modFolderNames?.any { matchesLocal(LocalModStatus(it, enabled = true)) } == true
}

internal fun CatalogMod.shortDescription(): String = summary.cleanMarkdown()
    .ifBlank { description.cleanMarkdown() }
    .ifBlank { "Sem descricao no index." }

internal fun CatalogMod.accentColor(): Color {
    return when {
        categories.any { it.equals("API", ignoreCase = true) } -> BmmColor.Blue
        categories.any { it.equals("Joker", ignoreCase = true) } -> BmmColor.Amber
        categories.any { it.equals("Quality of Life", ignoreCase = true) } -> BmmColor.Green
        categories.any { it.equals("Resource Packs", ignoreCase = true) } -> BmmColor.Danger
        else -> BmmColor.PanelRaised
    }
}

// --- String helpers ---

internal fun formatDownloads(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}.${(value % 1_000_000) / 100_000}M downloads"
    value >= 1_000 -> "${value / 1_000}K downloads"
    value > 0 -> "$value downloads"
    else -> "downloads n/a"
}

internal fun String.shortSyncStamp(): String {
    if (isBlank()) return "not synced"
    return replace("T", " ").substringBefore(".").removeSuffix("Z").take(16)
}

internal fun String.cleanMarkdown(): String = replace(Regex("""!\[[^]]*\]\([^)]*\)"""), "")
    .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
    .replace(Regex("""[#*_>`~-]+"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()

internal fun String.readableMarkdown(): String = replace("\r\n", "\n")
    .replace(Regex("""!\[[^]]*\]\([^)]*\)"""), "")
    .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
    .lines().map { it.trim().replace(Regex("""^#{1,6}\s*"""), "").replace(Regex("""^[*-]\s+"""), "- ").replace(Regex("""[`*_>]+"""), "") }
    .joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n").trim()

internal fun String.redactPrivatePaths(): String = replace(Regex("""/data/data/[^\s:]+/cache/[^\s:]+"""), "[private cache]")
    .replace(Regex("""C:\\[^\s:]+"""), "[private cache]")

internal fun Uri.redacted(): String {
    val text = toString()
    return if (text.length <= 36) text else text.take(18) + "..." + text.takeLast(12)
}

// --- Context helpers ---

internal fun Context.openUrl(url: String) {
    if (!url.startsWith("https://")) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// --- TreeValidation helpers ---

internal fun TreeValidation.Valid.hasSteamoddedFolder(): Boolean = modFolderNames.any { folder ->
    val n = folder.lowercase()
    n == "smods" || n == "steamodded" || n.startsWith("smods-") || n.startsWith("steamodded-")
}

internal fun TreeValidation.Valid.hasAmuletCompatibleFolder(): Boolean = modFolderNames.any { folder ->
    folder.equals("Talisman", ignoreCase = true) || folder.startsWith("Talisman-", ignoreCase = true) ||
    folder.equals("Amulet", ignoreCase = true) || folder.startsWith("Amulet-", ignoreCase = true)
}

internal fun String.readablePath(): String {
    if (isBlank()) return "Not selected"
    val decoded = Uri.decode(this)
    val docId = decoded.substringAfter("/document/", "").ifBlank { decoded.substringAfter("/tree/", "") }
    return docId.ifBlank { decoded }.substringAfter("primary:")
}

internal fun Context.openModsFolder(uriString: String) {
    if (uriString.isBlank()) return
    val treeUri = Uri.parse(uriString)
    val root = DocumentFile.fromTreeUri(this, treeUri)
    val modsUri = root
        ?.findFile("ASET")
        ?.takeIf { it.isDirectory }
        ?.findFile("Mods")
        ?.takeIf { it.isDirectory }
        ?.uri
        ?: treeUri
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(modsUri, "vnd.android.document/directory")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        startActivity(intent)
    }.onFailure {
        runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra("android.provider.extra.INITIAL_URI", modsUri)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            startActivity(intent)
        }
    }
}
