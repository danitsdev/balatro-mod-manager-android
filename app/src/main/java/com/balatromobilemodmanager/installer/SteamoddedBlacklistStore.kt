package com.balatromobilemodmanager.installer

import android.content.Context
import androidx.documentfile.provider.DocumentFile

internal class SteamoddedBlacklistStore(
    private val appContext: Context,
) {
    fun read(modsDir: DocumentFile): Set<String> {
        return findBlacklistFiles(modsDir)
            .mapNotNull(::readText)
            .flatMapTo(linkedSetOf()) { SteamoddedBlacklist.parse(it) }
    }

    fun remove(modsDir: DocumentFile, folderName: String) {
        findBlacklistFiles(modsDir).forEach { file ->
            val current = readText(file) ?: return@forEach
            val updated = SteamoddedBlacklist.remove(current, folderName)
            if (updated == current) return@forEach
            appContext.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(updated)
            } ?: throw IllegalStateException("Could not update Steamodded's disabled mod list.")
        }
    }

    private fun findBlacklistFiles(modsDir: DocumentFile): List<DocumentFile> {
        val roots = buildList {
            add(modsDir)
            modsDir.listFiles()
                .filter { it.isDirectory && it.name.orEmpty().isSteamoddedFolderName() }
                .forEach(::add)
        }
        return roots.mapNotNull { root ->
            val lovelyDir = root.listFiles().firstOrNull {
                it.isDirectory && it.name.equals("lovely", ignoreCase = true)
            } ?: return@mapNotNull null
            lovelyDir.listFiles().firstOrNull {
                it.isFile && it.name.equals("blacklist.txt", ignoreCase = true)
            }
        }.distinctBy { it.uri }
    }

    private fun readText(file: DocumentFile): String? {
        if (file.length() > MAX_BLACKLIST_BYTES) return null
        return appContext.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
    }

    private companion object {
        const val MAX_BLACKLIST_BYTES = 256 * 1024L
    }
}

private fun String.isSteamoddedFolderName(): Boolean {
    val lower = lowercase()
    return lower == "smods" || lower == "steamodded" || lower.startsWith("smods-") ||
        lower.startsWith("steamodded-") || lower.contains("steamodded")
}

internal object SteamoddedBlacklist {
    fun parse(text: String): Set<String> = text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapTo(linkedSetOf()) { it.lowercase() }

    fun remove(text: String, folderName: String): String {
        val retained = text.lineSequence()
            .filterNot { it.trim().equals(folderName, ignoreCase = true) }
            .toList()
        return retained.joinToString("\n").trimEnd() + if (retained.any { it.isNotBlank() }) "\n" else ""
    }
}
