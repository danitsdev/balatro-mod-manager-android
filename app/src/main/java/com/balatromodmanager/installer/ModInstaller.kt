package com.balatromodmanager.installer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.ManagedInstallManifest
import com.balatromodmanager.catalog.sanitizeFolderName
import com.balatromodmanager.redactPrivatePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

class ModInstaller(
    private val appContext: Context,
    private val manifestRepository: ManagedInstallRepository,
    private val inspector: ZipArchiveInspector = ZipArchiveInspector(),
    private val policy: ArchivePolicy = ArchivePolicy(),
) {
    private val steamoddedBlacklist = SteamoddedBlacklistStore(appContext)

    suspend fun install(
        treeUri: Uri,
        mod: CatalogMod,
        replaceUnmanagedFolder: String? = null,
        replaceUnmanagedDisabled: Boolean = false,
        onProgress: suspend (InstallProgress) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        val downloadUrl = mod.downloadUrl.trim()
        if (!downloadUrl.startsWith("https://")) {
            return@withContext InstallResult.Failed("Missing or unsafe download for ${mod.title}.")
        }
        if (!mod.supportsAutomaticInstall) {
            return@withContext InstallResult.Failed("${mod.title} does not provide a ZIP supported by automatic installation.")
        }

        onProgress(InstallProgress("Opening ASET/Mods"))
        val modsDir = resolveModsDir(treeUri)
            ?: return@withContext InstallResult.Failed("ASET/Mods could not be opened with the current folder permission.")
        if (!modsDir.canWrite()) {
            return@withContext InstallResult.Failed("ASET/Mods is read-only.")
        }

        val folderName = mod.installFolder
        val existing = modsDir.findFile(folderName)
        val existingManifest = manifestRepository.findByFolder(folderName)
        val canReplaceUnmanaged = replaceUnmanagedFolder?.equals(folderName, ignoreCase = true) == true
        if (existing != null && existingManifest == null && !canReplaceUnmanaged) {
            return@withContext InstallResult.Failed("$folderName already exists as a manual mod and cannot be replaced by this action.")
        }

        val disabledBeforeInstall = if (existing != null || replaceUnmanagedFolder != null) {
            runCatching {
                val markerDisabled = existing?.hasLovelyIgnoreRecursively() == true
                val blacklistedFolder = (replaceUnmanagedFolder ?: folderName).lowercase()
                markerDisabled ||
                    blacklistedFolder in steamoddedBlacklist.read(modsDir) ||
                    replaceUnmanagedDisabled
            }.getOrElse {
                return@withContext InstallResult.Failed("Could not read the current disabled state for $folderName.")
            }
        } else {
            false
        }

        val workDir = File(appContext.cacheDir, "install-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            onProgress(InstallProgress("Downloading ${mod.title}"))
            val archive = File(workDir, "download.zip")
            download(downloadUrl, archive, mod.title, onProgress)
            onProgress(InstallProgress("Inspecting archive"))
            val inspection = runCatching { inspector.inspect(archive) }
                .getOrElse { error ->
                    if (error is java.util.zip.ZipException) {
                        throw IllegalStateException("The download is not a valid ZIP for automatic installation.")
                    }
                    throw error
                }
            onProgress(InstallProgress("Installing ${mod.title}", 0f))
            commitArchiveToMods(
                modsDir = modsDir,
                folderName = folderName,
                existing = existing,
                archive = archive,
                inspection = inspection,
                title = mod.title,
                preserveDisabled = disabledBeforeInstall,
                onProgress = onProgress,
            )
            if (existing == null) steamoddedBlacklist.remove(modsDir, folderName)

            val manifest = ManagedInstallManifest(
                modId = mod.id,
                title = mod.title,
                folderName = folderName,
                version = mod.version,
                sourceUrl = downloadUrl,
                installedAtEpochMs = System.currentTimeMillis(),
                fileCount = inspection.fileCount,
                totalBytes = inspection.totalBytes,
            )
            onProgress(InstallProgress("Saving managed manifest", 1f))
            manifestRepository.save(manifest)
            InstallResult.Installed(manifest)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            InstallResult.Failed(exception.userFacingInstallMessage(mod.title))
        } finally {
            workDir.deleteRecursively()
        }
    }

    suspend fun importArchive(
        treeUri: Uri,
        archiveUri: Uri,
        onProgress: suspend (InstallProgress) -> Unit = {},
    ): ArchiveImportResult = withContext(Dispatchers.IO) {
        val modsDir = resolveModsDir(treeUri)
            ?: return@withContext ArchiveImportResult.Failed("ASET/Mods could not be opened with the current folder permission.")
        if (!modsDir.canWrite()) return@withContext ArchiveImportResult.Failed("ASET/Mods is read-only.")

        val sourceName = archiveUri.displayName()?.substringAfterLast('/')
            ?: return@withContext ArchiveImportResult.Failed("Could not read the selected file name.")
        val workDir = File(appContext.cacheDir, "import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = File(workDir, "selected.zip")
            onProgress(InstallProgress("Reading $sourceName"))
            copySelectedArchive(archiveUri, archive)
            onProgress(InstallProgress("Inspecting archive"))
            val inspection = runCatching { inspector.inspect(archive) }
                .getOrElse { error ->
                    if (error is java.util.zip.ZipException) {
                        throw IllegalStateException("The selected file is not a valid ZIP archive.")
                    }
                    throw error
                }
            val units = inspection.importUnits(sourceName)
            if (units.isEmpty()) throw ArchiveValidationException.EmptyArchive()
            val names = units.map { it.folderName.lowercase() }
            if (names.distinct().size != names.size) {
                throw IllegalStateException("The archive contains folders that map to the same mod name.")
            }
            val existingNames = modsDir.listFiles().mapNotNull { it.name?.lowercase() }.toSet()
            val conflicts = units.map { it.folderName }.filter { it.lowercase() in existingNames }
            if (conflicts.isNotEmpty()) {
                throw IllegalStateException(
                    "${conflicts.joinToString()} already exists in ASET/Mods. Remove or rename it before importing.",
                )
            }

            val imported = mutableListOf<String>()
            try {
                units.forEachIndexed { index, unit ->
                    onProgress(InstallProgress("Importing ${unit.folderName}", index.toFloat() / units.size))
                    commitArchiveToMods(
                        modsDir = modsDir,
                        folderName = unit.folderName,
                        existing = null,
                        archive = archive,
                        inspection = unit.inspection,
                        title = unit.folderName,
                        preserveDisabled = false,
                        onProgress = onProgress,
                    )
                    imported += unit.folderName
                }
            } catch (exception: Exception) {
                imported.forEach { modsDir.findFile(it)?.deleteTreeSaf() }
                throw exception
            }
            ArchiveImportResult.Imported(units.map { it.folderName })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            ArchiveImportResult.Failed(exception.userFacingImportMessage(sourceName))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun resolveModsDir(treeUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return null
        val aset = root.findFile("ASET")?.takeIf { it.isDirectory } ?: return null
        return aset.findFile("Mods")?.takeIf { it.isDirectory }
    }

    suspend fun uninstall(
        treeUri: Uri,
        manifest: ManagedInstallManifest,
        onProgress: suspend (InstallProgress) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        onProgress(InstallProgress("Opening ASET/Mods"))
        val modsDir = resolveModsDir(treeUri)
            ?: return@withContext InstallResult.Failed("ASET/Mods could not be opened with the current folder permission.")
        val target = modsDir.findFile(manifest.folderName)
            ?: return@withContext InstallResult.Failed("${manifest.folderName} no longer exists in Mods.")
        onProgress(InstallProgress("Removing ${manifest.folderName}"))
        if (!target.deleteTreeSaf()) {
            return@withContext InstallResult.Failed("Could not delete ${manifest.folderName}.")
        }
        steamoddedBlacklist.remove(modsDir, manifest.folderName)
        onProgress(InstallProgress("Removing managed manifest"))
        manifestRepository.delete(manifest.folderName)
        InstallResult.Uninstalled(manifest.folderName)
    }

    private suspend fun download(
        url: String,
        target: File,
        title: String,
        onProgress: suspend (InstallProgress) -> Unit,
    ) {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                useCaches = true
                setRequestProperty("User-Agent", "BalatroModManagerAndroid/0.1")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect has no destination.")
                    val next = URL(current, location)
                    if (current.protocol == "https" && next.protocol != "https") {
                        throw IllegalStateException("HTTPS to HTTP redirect was blocked.")
                    }
                    current = next
                    return@repeat
                }
                if (status !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $status.")
                }
                val contentLength = connection.contentLengthLong.takeIf { it > 0 }
                if (contentLength != null && contentLength > policy.maxArchiveBytes) {
                    throw ArchiveValidationException.TooLarge("Download exceeds the app size limit.")
                }
                var copied = 0L
                var lastPercent = -1
                var lastReportedBytes = 0L
                BufferedInputStream(connection.inputStream, IO_BUFFER_SIZE).use { input ->
                    BufferedOutputStream(FileOutputStream(target), IO_BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(IO_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > policy.maxArchiveBytes) {
                                throw ArchiveValidationException.TooLarge("Download exceeds the app size limit.")
                            }
                            output.write(buffer, 0, read)
                            if (contentLength != null) {
                                val percent = (copied * 100L / contentLength).coerceAtMost(100).toInt()
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    onProgress(
                                        InstallProgress(
                                            message = "Downloading $title: $percent%",
                                            fraction = percent / 100f,
                                        ),
                                    )
                                }
                            } else if (copied - lastReportedBytes >= UNKNOWN_LENGTH_PROGRESS_INTERVAL_BYTES) {
                                lastReportedBytes = copied
                                onProgress(InstallProgress(message = "Downloading $title"))
                            }
                        }
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("The download exceeded the redirect limit.")
    }

    private suspend fun copySelectedArchive(uri: Uri, target: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open the selected ZIP file.")
        var copied = 0L
        BufferedInputStream(input, IO_BUFFER_SIZE).use { source ->
            BufferedOutputStream(FileOutputStream(target), IO_BUFFER_SIZE).use { output ->
                val buffer = ByteArray(IO_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > policy.maxArchiveBytes) {
                        throw ArchiveValidationException.TooLarge("The selected ZIP exceeds the app size limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        if (copied == 0L) throw ArchiveValidationException.EmptyArchive()
    }

    private fun Uri.displayName(): String? {
        DocumentFile.fromSingleUri(appContext, this)?.name?.takeIf(String::isNotBlank)?.let { return it }
        return appContext.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let(cursor::getString)
                    ?.takeIf(String::isNotBlank)
            }
    }

    private fun ArchiveInspection.importUnits(sourceName: String): List<ArchiveImportUnit> {
        val wrapper = installRoot
        val rootPrefix = wrapper?.let { "$it/" }.orEmpty()
        val contents = entries.filter { it.path.startsWith(rootPrefix) && it.path != wrapper }
        val roots = contents.asSequence()
            .mapNotNull { entry ->
                val relative = entry.path.removePrefix(rootPrefix)
                if ('/' in relative || entry.isDirectory) relative.substringBefore('/') else null
            }
            .filterNot { it.lowercase() in IGNORED_ARCHIVE_DIRECTORIES }
            .distinct()
            .toList()
        val rootFiles = contents.filter { '/' !in it.path.removePrefix(rootPrefix) && !it.isDirectory }
        val hasOnlyPackMetadata = rootFiles.all { entry ->
            val name = entry.path.substringAfterLast('/').lowercase()
            name in PACK_METADATA_FILES || name.startsWith("license.") || name.startsWith("readme.")
        }
        if (roots.isEmpty() && rootFiles.any { it.path.substringAfterLast('/').equals("manifest.json", ignoreCase = true) }) {
            throw IllegalStateException(
                "This ZIP contains only a modpack manifest. It lists dependencies but does not include the mod files.",
            )
        }
        val splitFolders = roots.size > 1 && (rootFiles.isEmpty() || hasOnlyPackMetadata)
        if (splitFolders) {
            return roots.map { root ->
                val unitRoot = rootPrefix + root
                val unitEntries = entries.filter { it.path == unitRoot || it.path.startsWith("$unitRoot/") }
                ArchiveImportUnit(
                    folderName = root.sanitizeFolderName(),
                    inspection = copy(
                        entries = unitEntries,
                        totalBytes = unitEntries.sumOf { it.size },
                        installRoot = unitRoot,
                    ),
                )
            }
        }

        if (wrapper != null) return listOf(ArchiveImportUnit(wrapper.sanitizeFolderName(), this))
        if (roots.size == 1 && rootFiles.isEmpty()) {
            val root = roots.single()
            return listOf(ArchiveImportUnit(root.sanitizeFolderName(), copy(installRoot = root)))
        }

        val archiveName = sourceName.substringBeforeLast('.', sourceName)
        return listOf(ArchiveImportUnit(archiveName.sanitizeFolderName(), this))
    }

    private suspend fun commitArchiveToMods(
        modsDir: DocumentFile,
        folderName: String,
        existing: DocumentFile?,
        archive: File,
        inspection: ArchiveInspection,
        title: String,
        preserveDisabled: Boolean,
        onProgress: suspend (InstallProgress) -> Unit,
    ) {
        val stageName = "BMMM-STAGING-${folderName}-${UUID.randomUUID()}"
        val backupName = "BMMM-BACKUP-${folderName}-${UUID.randomUUID()}"
        val stage = modsDir.createDirectory(stageName)
            ?: throw IllegalStateException("Could not create the staging folder in Mods.")
        var existingMovedToBackup = false
        try {
            extractZipToSafStage(archive, stage, inspection, title, onProgress)
            if (preserveDisabled) stage.createLovelyIgnore()
            onProgress(InstallProgress("Activating $title", 1f))
            if (existing != null && !existing.renameTo(backupName)) {
                throw IllegalStateException("Could not prepare a backup of the previous version.")
            }
            existingMovedToBackup = existing != null
            if (!stage.renameTo(folderName)) {
                val backup = modsDir.findFile(backupName)
                backup?.renameTo(folderName)
                existingMovedToBackup = false
                throw IllegalStateException("Could not activate the new version. The backup was restored when possible.")
            }
            modsDir.findFile(backupName)?.deleteTreeSaf()
        } catch (exception: Exception) {
            stage.deleteTreeSaf()
            if (existingMovedToBackup && modsDir.findFile(folderName) == null) {
                modsDir.findFile(backupName)?.renameTo(folderName)
            }
            throw exception
        }
    }

    private suspend fun extractZipToSafStage(
        archive: File,
        stage: DocumentFile,
        inspection: ArchiveInspection,
        title: String,
        onProgress: suspend (InstallProgress) -> Unit,
    ) {
        val allowed = inspection.entries.mapTo(hashSetOf()) { it.path }
        val directories = mutableMapOf("" to stage)
        val totalFiles = inspection.fileCount
        val progressInterval = (totalFiles / 100).coerceAtLeast(1)
        var copiedFiles = 0
        var lastReportedFiles = -1

        suspend fun reportProgress(force: Boolean = false) {
            if (!force && copiedFiles != 1 && copiedFiles - lastReportedFiles < progressInterval) return
            lastReportedFiles = copiedFiles
            val percent = if (totalFiles == 0) 100 else copiedFiles * 100 / totalFiles
            onProgress(
                InstallProgress(
                    message = "Installing $title: $copiedFiles/$totalFiles files ($percent%)",
                    fraction = percent / 100f,
                ),
            )
        }

        reportProgress(force = true)
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                val path = entry.name.normalizedZipPath()
                if (path !in allowed) continue
                val relative = path.relativeToInstallRoot(inspection.installRoot) ?: continue
                if (relative.isBlank() || relative == "__MACOSX" || relative.startsWith("__MACOSX/")) continue
                if (entry.isDirectory) {
                    directories.ensureSafDirectory(relative)
                } else {
                    val parentPath = relative.substringBeforeLast('/', "")
                    val fileName = relative.substringAfterLast('/')
                    val parent = directories.ensureSafDirectory(parentPath)
                    // Staging is empty and archive paths are unique. Avoid findFile():
                    // many SAF providers enumerate every sibling for every lookup,
                    // which makes large mods effectively O(n^2) to install.
                    val target = parent.createFile("application/octet-stream", fileName)
                        ?: throw IllegalStateException("Could not create file $relative.")
                    appContext.contentResolver.openOutputStream(target.uri, "w")?.use { rawOutput ->
                        BufferedOutputStream(rawOutput, IO_BUFFER_SIZE).use { output ->
                            BufferedInputStream(zip.getInputStream(entry), IO_BUFFER_SIZE).use { input ->
                                val buffer = ByteArray(IO_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    } ?: throw IllegalStateException("Could not open $relative for writing.")
                    copiedFiles += 1
                    reportProgress(force = copiedFiles == totalFiles)
                }
            }
        }
    }

    private fun DocumentFile.createLovelyIgnore() {
        if (findFile(LOVELY_IGNORE) != null) return
        val marker = createFile("application/octet-stream", LOVELY_IGNORE)
            ?: throw IllegalStateException("Could not preserve the disabled state.")
        appContext.contentResolver.openOutputStream(marker.uri, "w")?.use { }
            ?: throw IllegalStateException("Could not preserve the disabled state.")
    }

    private fun MutableMap<String, DocumentFile>.ensureSafDirectory(path: String): DocumentFile {
        if (path.isBlank()) return getValue("")
        this[path]?.let { return it }
        var currentPath = ""
        var current = getValue("")
        path.split('/').forEach { segment ->
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            current = this[currentPath] ?: current.createDirectory(segment)
                ?: throw IllegalStateException("Could not create folder $currentPath.")
            this[currentPath] = current
        }
        return current
    }

    private fun String.relativeToInstallRoot(installRoot: String?): String? {
        if (installRoot == null) return this
        if (this == installRoot) return ""
        val prefix = "$installRoot/"
        return if (startsWith(prefix)) removePrefix(prefix) else null
    }

    private companion object {
        const val MAX_REDIRECTS = 6
        const val IO_BUFFER_SIZE = 64 * 1024
        const val UNKNOWN_LENGTH_PROGRESS_INTERVAL_BYTES = 512 * 1024L
        const val LOVELY_IGNORE = ".lovelyignore"
        val IGNORED_ARCHIVE_DIRECTORIES = setOf("__macosx", ".git", ".github", "docs", "documentation")
        val PACK_METADATA_FILES = setOf(
            "readme", "readme.md", "readme.txt", "license", "copying",
            "manifest.json", "changelog.md", "notice", "notice.md", "icon.png", "icon.jpg",
        )
    }
}

private data class ArchiveImportUnit(
    val folderName: String,
    val inspection: ArchiveInspection,
)

sealed interface ArchiveImportResult {
    data class Imported(val folderNames: List<String>) : ArchiveImportResult
    data class Failed(val message: String) : ArchiveImportResult
}

data class InstallProgress(
    val message: String,
    val fraction: Float? = null,
)

private fun DocumentFile.deleteTreeSaf(): Boolean {
    if (delete()) return true
    if (isDirectory) {
        listFiles().forEach { child ->
            if (!child.deleteTreeSaf()) return false
        }
        return delete()
    }
    return false
}

sealed interface InstallResult {
    data class Installed(val manifest: ManagedInstallManifest) : InstallResult
    data class Uninstalled(val folderName: String) : InstallResult
    data class Failed(val message: String) : InstallResult
}

private fun Exception.userFacingInstallMessage(title: String): String {
    val raw = message ?: return "Unknown error while installing $title."
    return raw.redactPrivatePaths()
}

private fun Exception.userFacingImportMessage(fileName: String): String {
    val raw = message ?: return "Unknown error while importing $fileName."
    return raw.redactPrivatePaths()
}
