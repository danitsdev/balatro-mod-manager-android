package com.balatromodmanager.installer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalModStatus(
    val folderName: String,
    val enabled: Boolean,
    val title: String = "",
    val author: String = "",
    val version: String = "",
    val declaredId: String = "",
)

class LocalModRepository(
    private val appContext: Context,
    private val managedInstallRepository: ManagedInstallRepository? = null,
) {
    private val steamoddedBlacklist = SteamoddedBlacklistStore(appContext)

    suspend fun list(
        treeUri: Uri,
        folderNames: Set<String>? = null,
    ): List<LocalModStatus> = withContext(Dispatchers.IO) {
        val modsDir = resolveModsDir(treeUri)
            ?: throw IllegalStateException("Could not open ASET/Mods.")
        val blacklistedFolders = steamoddedBlacklist.read(modsDir)
        val managedFolders = managedInstallRepository
            ?.list()
            ?.mapTo(hashSetOf()) { it.folderName.lowercase() }
            .orEmpty()
        val candidates = folderNames?.asSequence()
            ?.mapNotNull { folderName -> modsDir.findFile(folderName) }
            ?: modsDir.listFiles().asSequence()
        candidates
            .filter { it.isDirectory }
            .filterNot { LocalModDetectionPolicy.shouldSkipFolder(it.name.orEmpty()) }
            .mapNotNull { modDir -> readStatus(modDir, blacklistedFolders, managedFolders) }
            .sortedWith(compareBy<LocalModStatus> { !it.enabled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.folderName })
            .toList()
    }

    suspend fun setEnabled(treeUri: Uri, folderName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val modsDir = resolveModsDir(treeUri) ?: throw IllegalStateException("Could not open ASET/Mods.")
        val modDir = modsDir.findFile(folderName)?.takeIf { it.isDirectory }
            ?: throw IllegalStateException("$folderName was not found in ASET/Mods.")
        setEnabled(modsDir, modDir, folderName, enabled)
    }

    suspend fun setEnabled(treeUri: Uri, folderNames: List<String>, enabled: Boolean) = withContext(Dispatchers.IO) {
        val modsDir = resolveModsDir(treeUri) ?: throw IllegalStateException("Could not open ASET/Mods.")
        val failures = mutableListOf<String>()
        folderNames.distinct().forEach { folderName ->
            try {
                val modDir = modsDir.findFile(folderName)?.takeIf { it.isDirectory }
                    ?: throw IllegalStateException("$folderName was not found in ASET/Mods.")
                setEnabled(modsDir, modDir, folderName, enabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                failures += folderName
            }
        }
        if (failures.isNotEmpty()) {
            throw IllegalStateException("Could not change: ${failures.joinToString()}.")
        }
    }

    suspend fun remove(treeUri: Uri, folderName: String) = withContext(Dispatchers.IO) {
        if (folderName.isBlank() || folderName.contains('/') || folderName.contains('\\')) {
            throw IllegalArgumentException("Invalid mod folder name.")
        }
        if (folderName.startsWith("BMMM-STAGING-") || folderName.startsWith("BMMM-BACKUP-")) {
            throw IllegalArgumentException("Internal manager folders cannot be removed by this action.")
        }
        val modsDir = resolveModsDir(treeUri) ?: throw IllegalStateException("Could not open ASET/Mods.")
        val modDir = modsDir.findFile(folderName)?.takeIf { it.isDirectory }
            ?: throw IllegalStateException("$folderName was not found in ASET/Mods.")
        if (!modDir.deleteRecursivelySaf()) {
            throw IllegalStateException("Could not remove $folderName from ASET/Mods.")
        }
        steamoddedBlacklist.remove(modsDir, folderName)
    }

    private fun setEnabled(modsDir: DocumentFile, modDir: DocumentFile, folderName: String, enabled: Boolean) {
        if (enabled) {
            collectSubdirs(modDir).forEach { dir ->
                val ignore = dir.findFile(LOVELY_IGNORE) ?: return@forEach
                if (!ignore.delete()) {
                    throw IllegalStateException("Could not remove .lovelyignore for $folderName.")
                }
            }
            steamoddedBlacklist.remove(modsDir, folderName)
        } else {
            if (modDir.findFile(LOVELY_IGNORE) == null) {
                val file = modDir.createFile("application/octet-stream", LOVELY_IGNORE)
                    ?: throw IllegalStateException("Could not create .lovelyignore for $folderName.")
                appContext.contentResolver.openOutputStream(file.uri)?.use { output ->
                    output.write(byteArrayOf())
                } ?: throw IllegalStateException("Could not write .lovelyignore for $folderName.")
            }
        }
    }

    private fun resolveModsDir(treeUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return null
        val aset = root.findFile("ASET")?.takeIf { it.isDirectory } ?: return null
        return aset.findFile("Mods")?.takeIf { it.isDirectory }
    }

    private fun readStatus(
        modDir: DocumentFile,
        blacklistedFolders: Set<String>,
        managedFolders: Set<String>,
    ): LocalModStatus? {
        val folderName = modDir.name.orEmpty()
        val scan = scanModDirectory(modDir)
        val metadata = readMetadata(scan.topLevelEntries)
        val topLevelNames = scan.topLevelEntries.mapTo(hashSetOf()) { it.name.orEmpty() }
        if (!LocalModDetectionPolicy.hasModEvidence(
                folderName = folderName,
                topLevelNames = topLevelNames,
                metadata = metadata,
                managed = folderName.lowercase() in managedFolders,
            )
        ) return null
        return LocalModStatus(
            folderName = folderName,
            enabled = !scan.hasLovelyIgnore && folderName.lowercase() !in blacklistedFolders,
            title = metadata.title,
            author = metadata.author,
            version = metadata.version,
            declaredId = metadata.declaredId,
        )
    }

    private fun scanModDirectory(modDir: DocumentFile): ModDirectoryScan {
        val topLevelEntries = modDir.listFiles().toList()
        var hasLovelyIgnore = topLevelEntries.any { it.isFile && it.name.equals(LOVELY_IGNORE, ignoreCase = true) }
        val stack = ArrayDeque<DocumentFile>()
        topLevelEntries.filter { it.isDirectory }.forEach(stack::add)
        while (!hasLovelyIgnore && stack.isNotEmpty()) {
            val entries = stack.removeLast().listFiles().toList()
            hasLovelyIgnore = entries.any { it.isFile && it.name.equals(LOVELY_IGNORE, ignoreCase = true) }
            if (!hasLovelyIgnore) entries.filter { it.isDirectory }.forEach(stack::add)
        }
        return ModDirectoryScan(topLevelEntries, hasLovelyIgnore)
    }

    private fun collectSubdirs(root: DocumentFile): List<DocumentFile> {
        val out = mutableListOf(root)
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            dir.listFiles()
                .filter { it.isDirectory }
                .forEach { child ->
                    out += child
                    stack.add(child)
                }
        }
        return out
    }

    private fun readMetadata(entries: List<DocumentFile>): LocalModMetadata {
        return entries
            .filter { it.isFile }
            .mapNotNull { file ->
                val priority = metadataPriority(file.name.orEmpty())
                if (priority == Int.MAX_VALUE) null else priority to file
            }
            .sortedBy { it.first }
            .take(MAX_METADATA_CANDIDATES)
            .firstNotNullOfOrNull { (_, file) ->
                val text = readSmallText(file) ?: return@firstNotNullOfOrNull null
                LocalModMetadataParser.parse(file.name.orEmpty(), text).takeIf { it.hasAnyValue }
            }
            ?: LocalModMetadata()
    }

    private fun metadataPriority(name: String): Int {
        return when {
            name.equals("manifest.json", ignoreCase = true) -> 0
            name.equals("metadata.json", ignoreCase = true) -> 1
            name.endsWith(".json", ignoreCase = true) -> 2
            name.equals("lovely.toml", ignoreCase = true) -> 3
            name.endsWith(".toml", ignoreCase = true) -> 4
            name.endsWith(".lua", ignoreCase = true) -> 5
            else -> Int.MAX_VALUE
        }
    }

    private fun readSmallText(file: DocumentFile): String? {
        val length = file.length()
        if (length > MAX_METADATA_BYTES) return null
        return appContext.contentResolver.openInputStream(file.uri)?.use { input ->
            input.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val builder = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count == -1) break
                    builder.append(buffer, 0, count)
                    if (builder.length > MAX_METADATA_BYTES) return null
                }
                builder.toString()
            }
        }
    }

    private companion object {
        const val LOVELY_IGNORE = ".lovelyignore"
        const val MAX_METADATA_BYTES = 128 * 1024
        const val MAX_METADATA_CANDIDATES = 12
    }

    private data class ModDirectoryScan(
        val topLevelEntries: List<DocumentFile>,
        val hasLovelyIgnore: Boolean,
    )
}

internal fun DocumentFile.hasLovelyIgnoreRecursively(
    initialEntries: List<DocumentFile>? = null,
): Boolean {
    val stack = ArrayDeque<DocumentFile>()
    val rootEntries = initialEntries ?: listFiles().toList()
    if (rootEntries.any { it.isFile && it.name.equals(".lovelyignore", ignoreCase = true) }) return true
    rootEntries.filter { it.isDirectory }.forEach(stack::add)

    while (stack.isNotEmpty()) {
        val entries = stack.removeLast().listFiles().toList()
        if (entries.any { it.isFile && it.name.equals(".lovelyignore", ignoreCase = true) }) return true
        entries.filter { it.isDirectory }.forEach(stack::add)
    }
    return false
}

internal object LocalModDetectionPolicy {
    fun shouldSkipFolder(name: String): Boolean {
        val lower = name.lowercase()
        return name.startsWith("BMMM-STAGING-", ignoreCase = true) ||
            name.startsWith("BMMM-BACKUP-", ignoreCase = true) ||
            lower.startsWith(".") ||
            lower.contains("lovely") ||
            lower == "bmm-compat" ||
            lower == "nativefs" ||
            lower == "node_modules" ||
            lower == "__macosx"
    }

    fun hasModEvidence(
        folderName: String,
        topLevelNames: Set<String>,
        metadata: LocalModMetadata,
        managed: Boolean,
    ): Boolean {
        if (managed || metadata.hasAnyValue) return true

        val names = topLevelNames.mapTo(hashSetOf()) { it.lowercase() }
        if ("lovely.toml" in names) return true

        val lowerFolder = folderName.lowercase()
        val steamoddedFolder = lowerFolder == "steamodded" ||
            lowerFolder == "smods" ||
            lowerFolder == "smods_main" ||
            lowerFolder.startsWith("smods-") ||
            lowerFolder.contains("steamodded")
        if (steamoddedFolder && names.any { it in STEAMODDED_INDICATORS }) return true

        return "mods" in names && names.any { it.equals("readme.md", ignoreCase = true) }
    }

    private val STEAMODDED_INDICATORS = setOf(
        "api.lua",
        "smods.lua",
        "loader.lua",
        "init.lua",
        "manifest.json",
    )
}

private fun DocumentFile.deleteRecursivelySaf(): Boolean {
    if (delete()) return true
    if (isDirectory) {
        listFiles().forEach { child ->
            if (!child.deleteRecursivelySaf()) return false
        }
        return delete()
    }
    return false
}
