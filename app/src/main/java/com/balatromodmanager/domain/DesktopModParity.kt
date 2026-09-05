package com.balatromodmanager.domain

import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.ManagedInstallManifest
import com.balatromodmanager.installer.LocalModStatus

enum class ModPrimaryAction(val label: String) {
    Download("Download"),
    Update("Update"),
    GetOfficial("Get official"),
    Installed("Installed"),
}

fun resolveModPrimaryAction(
    installed: Boolean,
    hasUpdate: Boolean,
    canGetOfficial: Boolean,
): ModPrimaryAction = when {
    !installed -> ModPrimaryAction.Download
    hasUpdate -> ModPrimaryAction.Update
    canGetOfficial -> ModPrimaryAction.GetOfficial
    else -> ModPrimaryAction.Installed
}

fun List<CatalogMod>.findCatalogForLocal(local: LocalModStatus): CatalogMod? {
    val declaredId = local.declaredId.simplifiedModKey()
    if (declaredId.isNotBlank()) {
        firstOrNull { mod ->
            mod.id.simplifiedModKey() == declaredId ||
                mod.id.substringAfter('@').simplifiedModKey() == declaredId
        }?.let { return it }
    }

    val folder = local.folderName.simplifiedModKey()
    firstOrNull { mod ->
        folder.isNotBlank() && listOf(mod.folderName, mod.installFolder)
            .any { it.simplifiedModKey() == folder }
    }?.let { return it }

    val title = local.title.simplifiedModKey()
    val author = local.author.simplifiedModKey()
    return firstOrNull { mod ->
        title.isNotBlank() && mod.title.simplifiedModKey() == title &&
            (author.isBlank() || mod.author.simplifiedModKey().contains(author))
    } ?: firstOrNull { mod ->
        mod.matchKeys().any { it in local.matchKeys() }
    }
}

/** Mirrors the desktop BMM rule: any non-empty catalog version change is an update. */
fun CatalogMod.hasUpdateFor(manifest: ManagedInstallManifest): Boolean {
    val catalogVersion = version.trim()
    val installedVersion = manifest.version.trim()
    return catalogVersion.isNotBlank() &&
        installedVersion.isNotBlank() &&
        !catalogVersion.equals(installedVersion, ignoreCase = true)
}

fun CatalogMod.matchesLocal(local: LocalModStatus): Boolean {
    val localKeys = local.matchKeys()
    return localKeys.isNotEmpty() && matchKeys().any { it in localKeys }
}

fun CatalogMod.matchKeys(): Set<String> {
    return listOf(id, id.substringAfter('@'), title, folderName, installFolder)
        .map { it.simplifiedModKey() }
        .filter { it.isNotBlank() }
        .toSet()
}

fun LocalModStatus.matchKeys(): Set<String> {
    return listOf(declaredId, folderName, title)
        .map { it.simplifiedModKey() }
        .filter { it.isNotBlank() }
        .toSet()
}

fun String.simplifiedModKey(): String = lowercase().replace(Regex("""[^a-z0-9]+"""), "")
