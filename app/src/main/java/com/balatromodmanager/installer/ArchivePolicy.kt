package com.balatromodmanager.installer

data class ArchivePolicy(
    val maxArchiveBytes: Long = 250L * 1024L * 1024L,
    val maxExtractedBytes: Long = 1024L * 1024L * 1024L,
    val maxFileBytes: Long = 512L * 1024L * 1024L,
    val maxEntries: Int = 20_000,
    val maxPathDepth: Int = 32,
)

data class ArchiveInspection(
    val entries: List<ArchiveEntry>,
    val totalBytes: Long,
    val installRoot: String? = null,
) {
    val fileCount: Int
        get() = entries.count { !it.isDirectory }
}

data class ArchiveEntry(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
)

sealed class ArchiveValidationException(message: String) : Exception(message) {
    class TooLarge(message: String) : ArchiveValidationException(message)
    class UnsafePath(message: String) : ArchiveValidationException(message)
    class UnsupportedEntry(message: String) : ArchiveValidationException(message)
    class EmptyArchive : ArchiveValidationException("The downloaded archive contains no installable files.")
}
