package com.balatromodmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.ManagedInstallManifest
import com.balatromodmanager.domain.matchKeys
import com.balatromodmanager.domain.matchesLocal
import com.balatromodmanager.domain.simplifiedModKey
import com.balatromodmanager.installer.LocalModStatus
import com.balatromodmanager.storage.TreeValidation
import com.balatromodmanager.ui.theme.BmmColor
import androidx.compose.ui.graphics.Color

internal data class DependencyStatus(
    val steamoddedInstalled: Boolean,
    val amuletInstalled: Boolean,
    val lovelyInstalled: Boolean = true,
    val installedPackageIds: Set<String> = emptySet(),
    val installedFolderNames: Set<String> = emptySet(),
)

internal fun String.isBundledLovelyDependency(): Boolean =
    startsWith("Thunderstore-Lovely-", ignoreCase = true)

internal fun String.canonicalSteamoddedDependency(catalogMods: List<CatalogMod>): String {
    val legacyPrefix = "Steamopollys-Steamodded-"
    if (!startsWith(legacyPrefix, ignoreCase = true)) return this

    val currentPackage = catalogMods.firstOrNull {
        it.author.equals("Steamodded", ignoreCase = true) && it.title.equals("Steamodded", ignoreCase = true)
    }
    return if (currentPackage != null && currentPackage.version.isNotBlank()) {
        "${currentPackage.author}-${currentPackage.title}-${currentPackage.version}"
    } else {
        "Steamodded-Steamodded-${substring(legacyPrefix.length)}"
    }
}

internal data class PackageDependency(
    val identifier: String,
    val title: String,
    val version: String,
    val catalogMod: CatalogMod?,
    val installed: Boolean,
)

internal fun CatalogMod.resolveDependencies(
    catalogMods: List<CatalogMod>,
    status: DependencyStatus,
): List<PackageDependency> {
    val identifiers = requiredPackages.map { it.canonicalSteamoddedDependency(catalogMods) }.toMutableList()
    if (identifiers.isEmpty()) {
        if (requiresSteamodded) identifiers += "Steamodded-Steamodded"
        if (requiresAmulet) identifiers += "just_frostice482-Amulet"
    }

    return identifiers.distinct().mapNotNull { identifier ->
        val isSteamodded = identifier.startsWith("Steamodded-Steamodded", ignoreCase = true) ||
            identifier.startsWith("Steamopollys-Steamodded", ignoreCase = true)
        val isAmulet = identifier.startsWith("just_frostice482-Amulet", ignoreCase = true) ||
            identifier.startsWith("MathIsFun0-Talisman", ignoreCase = true)
        val isLovely = identifier.isBundledLovelyDependency()
        val dependencyMod = catalogMods.mapNotNull { candidate ->
            val packagePrefix = "${candidate.author}-${candidate.title}-"
            if (identifier.startsWith(packagePrefix, ignoreCase = true)) packagePrefix to candidate else null
        }.maxByOrNull { (packagePrefix, _) -> packagePrefix.length }?.second ?: when {
            isSteamodded -> catalogMods.findDependencyMod("Steamodded")
            isAmulet -> catalogMods.findDependencyMod("Amulet")
            else -> null
        }
        val packagePrefixes = buildList {
            dependencyMod?.let { add("${it.author}-${it.title}-") }
            when {
                identifier.startsWith("Steamopollys-Steamodded-", ignoreCase = true) -> add("Steamopollys-Steamodded-")
                identifier.startsWith("Steamodded-Steamodded-", ignoreCase = true) -> add("Steamodded-Steamodded-")
                identifier.startsWith("just_frostice482-Amulet-", ignoreCase = true) -> add("just_frostice482-Amulet-")
                identifier.startsWith("MathIsFun0-Talisman-", ignoreCase = true) -> add("MathIsFun0-Talisman-")
                isLovely -> add("Thunderstore-lovely-")
            }
        }
        val packagePrefix = packagePrefixes.firstOrNull { identifier.startsWith(it, ignoreCase = true) }
        val version = packagePrefix?.let { identifier.substring(it.length) }.orEmpty()
        val installed = when {
            isSteamodded -> status.steamoddedInstalled
            isAmulet -> status.amuletInstalled
            isLovely -> status.lovelyInstalled
            dependencyMod != null -> status.installedPackageIds.any { it.equals(dependencyMod.id, ignoreCase = true) } ||
                dependencyMod.installFolder.equalsAny(status.installedFolderNames)
            else -> false
        }

        PackageDependency(
            identifier = identifier,
            title = dependencyMod?.title ?: when {
                isSteamodded -> "Steamodded"
                isAmulet -> "Amulet"
                isLovely -> "Lovely (included with LMM)"
                else -> identifier
            },
            version = version,
            catalogMod = dependencyMod,
            installed = installed,
        )
    }
}

internal fun CatalogMod.missingDependencies(
    dependencies: DependencyStatus,
    catalogMods: List<CatalogMod> = emptyList(),
): List<PackageDependency> = resolveDependencies(catalogMods, dependencies).filterNot { it.installed }

private fun String.equalsAny(values: Set<String>): Boolean = values.any { equals(it, ignoreCase = true) }

internal fun List<CatalogMod>.findDependencyMod(name: String): CatalogMod? {
    val searchName = if (name.equals("Talisman", ignoreCase = true)) "Amulet" else name
    if (searchName.equals("Steamodded", ignoreCase = true)) {
        firstOrNull { it.author.equals("Steamodded", ignoreCase = true) && it.title.equals("Steamodded", ignoreCase = true) }
            ?.let { return it }
        return null
    }
    return firstOrNull { it.title.equals(searchName, ignoreCase = true) }
        ?: firstOrNull { it.folderName.equals(if (searchName == "Steamodded") "smods" else searchName, ignoreCase = true) }
}

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
    .ifBlank { "No description in the catalog." }

internal fun CatalogMod.accentColor(): Color {
    return when {
        categories.any { it.equals("API", ignoreCase = true) } -> BmmColor.Blue
        categories.any { it.equals("Joker", ignoreCase = true) } -> BmmColor.Amber
        categories.any { it.equals("Quality of Life", ignoreCase = true) } -> BmmColor.Green
        categories.any { it.equals("Resource Packs", ignoreCase = true) } -> BmmColor.Danger
        else -> BmmColor.PanelRaised
    }
}

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

internal fun String.redactPrivatePaths(): String = replace(Regex("""/data/data/[^\s:]+/cache/[^\s:]+"""), "[private cache]")
    .replace(Regex("""C:\\[^\s:]+"""), "[private cache]")

internal fun Context.openUrl(url: String) {
    if (!url.startsWith("https://")) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

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
