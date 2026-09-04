package com.balatromobilemodmanager.installer

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipArchiveInspector(
    private val policy: ArchivePolicy = ArchivePolicy(),
) {
    fun inspect(zipFile: File): ArchiveInspection {
        if (zipFile.length() > policy.maxArchiveBytes) {
            throw ArchiveValidationException.TooLarge("Archive maior que o limite do app.")
        }

        val entries = mutableListOf<ArchiveEntry>()
        var totalBytes = 0L
        ZipFile(zipFile).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                if (entries.size >= policy.maxEntries) {
                    throw ArchiveValidationException.TooLarge("The archive contains too many files.")
                }
                val entry = enumeration.nextElement()
                validateEntry(entry)
                val size = entry.size.takeIf { it >= 0 } ?: 0L
                if (size > policy.maxFileBytes) {
                    throw ArchiveValidationException.TooLarge("A mod file exceeds the allowed size limit.")
                }
                totalBytes += size
                if (totalBytes > policy.maxExtractedBytes) {
                    throw ArchiveValidationException.TooLarge("Archive expande alem do limite seguro.")
                }
                entries += ArchiveEntry(
                    path = entry.name.normalizedZipPath(),
                    size = size,
                    isDirectory = entry.isDirectory,
                )
            }
        }

        val installableEntries = entries.withoutDirectoryMarkerFiles()

        if (installableEntries.none { !it.isDirectory }) {
            throw ArchiveValidationException.EmptyArchive()
        }
        validateNoPathConflicts(installableEntries)
        return ArchiveInspection(
            entries = installableEntries,
            totalBytes = totalBytes,
            installRoot = installableEntries.singleWrapperDirectory(),
        )
    }

    private fun validateEntry(entry: ZipEntry) {
        val normalized = entry.name.normalizedZipPath()
        if (normalized.isBlank()) {
            throw ArchiveValidationException.UnsafePath("The archive contains an empty path.")
        }
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw ArchiveValidationException.UnsafePath("The archive contains an absolute path.")
        }
        if (normalized.split('/').any { it == ".." || it == "." || it.isBlank() }) {
            throw ArchiveValidationException.UnsafePath("The archive contains a path traversal entry.")
        }
        if (normalized.split('/').size > policy.maxPathDepth) {
            throw ArchiveValidationException.TooLarge("The archive exceeds the folder depth limit.")
        }
        val unixMode = entry.unixModeOrNull()
        if (unixMode != null && unixMode and 0xF000 == 0xA000) {
            throw ArchiveValidationException.UnsupportedEntry("The archive contains a symbolic link, which is not supported.")
        }
    }

    private fun validateNoPathConflicts(entries: List<ArchiveEntry>) {
        val seenPaths = mutableSetOf<String>()
        entries.forEach { entry ->
            if (!seenPaths.add(entry.path)) {
                throw ArchiveValidationException.UnsafePath(
                    "The archive contains a duplicate path: ${entry.path}.",
                )
            }
        }
        val filePaths = entries.filterNot { it.isDirectory }.map { it.path }.toSet()
        entries.forEach { entry ->
            val parts = entry.path.split('/')
            for (index in 1 until parts.size) {
                val parent = parts.take(index).joinToString("/")
                if (parent in filePaths) {
                    throw ArchiveValidationException.UnsafePath(
                        "The archive contains a path conflict: a file is used as a folder.",
                    )
                }
            }
        }
    }

    private fun List<ArchiveEntry>.withoutDirectoryMarkerFiles(): List<ArchiveEntry> {
        val parentPaths = flatMap { entry ->
            val parts = entry.path.split('/')
            (1 until parts.size).map { index -> parts.take(index).joinToString("/") }
        }.toSet()
        val markerPaths = filter { entry ->
            !entry.isDirectory && entry.size == 0L && entry.path in parentPaths
        }.map { it.path }.toSet()
        return filterNot { it.path in markerPaths && !it.isDirectory && it.size == 0L }
    }

    private fun List<ArchiveEntry>.singleWrapperDirectory(): String? {
        val relevant = filterNot { it.path == "__MACOSX" || it.path.startsWith("__MACOSX/") }
        val topLevelNames = relevant.map { it.path.substringBefore('/') }.toSet()
        val wrapper = topLevelNames.singleOrNull() ?: return null
        return wrapper.takeIf { root -> relevant.any { it.path.startsWith("$root/") } }
    }
}

fun String.normalizedZipPath(): String = replace('\\', '/').trim('/')

private fun ZipEntry.unixModeOrNull(): Int? {
    return runCatching {
        val method = javaClass.getMethod("getExternalAttributes")
        val attributes = (method.invoke(this) as Number).toLong()
        (attributes shr 16).toInt()
    }.getOrNull()
}
