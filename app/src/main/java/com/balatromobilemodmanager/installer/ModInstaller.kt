package com.balatromobilemodmanager.installer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import kotlinx.coroutines.Dispatchers
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
        onProgress: suspend (String) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        val downloadUrl = mod.downloadUrl.trim()
        if (!downloadUrl.startsWith("https://")) {
            return@withContext InstallResult.Failed("Missing or unsafe download for ${mod.title}.")
        }
        if (!mod.supportsAutomaticInstall) {
            return@withContext InstallResult.Failed("${mod.title} does not provide a ZIP supported by automatic installation.")
        }

        onProgress("Opening ASET/Mods")
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

        val workDir = File(appContext.cacheDir, "install-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            onProgress("Downloading ${mod.title}")
            val archive = File(workDir, "download.zip")
            download(downloadUrl, archive, onProgress)
            onProgress("Inspecting archive")
            val inspection = runCatching { inspector.inspect(archive) }
                .getOrElse { error ->
                    if (error is java.util.zip.ZipException) {
                        throw IllegalStateException("The download is not a valid ZIP for automatic installation.")
                    }
                    throw error
                }
            onProgress("Installing ${mod.title}")
            commitArchiveToMods(
                modsDir = modsDir,
                folderName = folderName,
                existing = existing,
                archive = archive,
                inspection = inspection,
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
            onProgress("Saving managed manifest")
            manifestRepository.save(manifest)
            InstallResult.Installed(manifest)
        } catch (exception: Exception) {
            InstallResult.Failed(exception.userFacingInstallMessage(mod.title))
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
        onProgress: suspend (String) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        onProgress("Opening ASET/Mods")
        val modsDir = resolveModsDir(treeUri)
            ?: return@withContext InstallResult.Failed("ASET/Mods could not be opened with the current folder permission.")
        val target = modsDir.findFile(manifest.folderName)
            ?: return@withContext InstallResult.Failed("${manifest.folderName} no longer exists in Mods.")
        onProgress("Removing ${manifest.folderName}")
        if (!target.deleteTreeSaf()) {
            return@withContext InstallResult.Failed("Could not delete ${manifest.folderName}.")
        }
        steamoddedBlacklist.remove(modsDir, manifest.folderName)
        onProgress("Removing managed manifest")
        manifestRepository.delete(manifest.folderName)
        InstallResult.Uninstalled(manifest.folderName)
    }

    private suspend fun download(
        url: String,
        target: File,
        onProgress: suspend (String) -> Unit,
    ) {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                useCaches = true
                setRequestProperty("User-Agent", "BalatroMobileModManager/0.1")
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
                var lastPercent = -1L
                BufferedInputStream(connection.inputStream, IO_BUFFER_SIZE).use { input ->
                    BufferedOutputStream(FileOutputStream(target), IO_BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(IO_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > policy.maxArchiveBytes) {
                                throw ArchiveValidationException.TooLarge("Download exceeds the app size limit.")
                            }
                            output.write(buffer, 0, read)
                            if (contentLength != null) {
                                val percent = copied * 100L / contentLength
                                if (percent >= lastPercent + 10L) {
                                    lastPercent = percent
                                    onProgress("Downloading ${percent.coerceAtMost(100)}%")
                                }
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

    private fun commitArchiveToMods(
        modsDir: DocumentFile,
        folderName: String,
        existing: DocumentFile?,
        archive: File,
        inspection: ArchiveInspection,
    ) {
        val stageName = "BMMM-STAGING-${folderName}-${UUID.randomUUID()}"
        val backupName = "BMMM-BACKUP-${folderName}-${UUID.randomUUID()}"
        val stage = modsDir.createDirectory(stageName)
            ?: throw IllegalStateException("Could not create the staging folder in Mods.")
        var existingMovedToBackup = false
        try {
            extractZipToSafStage(archive, stage, inspection)
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

    private fun extractZipToSafStage(
        archive: File,
        stage: DocumentFile,
        inspection: ArchiveInspection,
    ) {
        val allowed = inspection.entries.mapTo(hashSetOf()) { it.path }
        val directories = mutableMapOf("" to stage)
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
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
                    val target = parent.findFile(fileName)?.takeIf { it.isFile }
                        ?: parent.createFile("application/octet-stream", fileName)
                        ?: throw IllegalStateException("Could not create file $relative.")
                    appContext.contentResolver.openOutputStream(target.uri, "w")?.use { rawOutput ->
                        BufferedOutputStream(rawOutput, IO_BUFFER_SIZE).use { output ->
                            BufferedInputStream(zip.getInputStream(entry), IO_BUFFER_SIZE).use { input ->
                                input.copyTo(output, IO_BUFFER_SIZE)
                            }
                        }
                    } ?: throw IllegalStateException("Could not open $relative for writing.")
                }
            }
        }
    }

    private fun MutableMap<String, DocumentFile>.ensureSafDirectory(path: String): DocumentFile {
        if (path.isBlank()) return getValue("")
        this[path]?.let { return it }
        var currentPath = ""
        var current = getValue("")
        path.split('/').forEach { segment ->
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            current = this[currentPath] ?: current.findFile(segment)?.takeIf { it.isDirectory }
                ?: current.createDirectory(segment)
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
    }
}

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

private fun String.redactPrivatePaths(): String {
    return replace(Regex("""/data/data/[^\s:]+/cache/[^\s:]+"""), "[private cache]")
        .replace(Regex("""C:\\[^\s:]+"""), "[private cache]")
}
