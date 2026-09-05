package com.balatromodmanager.installer

import android.content.Context
import com.balatromodmanager.catalog.ManagedInstallManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ManagedInstallRepository(
    private val appContext: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    suspend fun list(): List<ManagedInstallManifest> = withContext(Dispatchers.IO) {
        manifestDir().listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString(ManagedInstallManifest.serializer(), file.readText())
                }.getOrNull()
            }
            ?.sortedBy { it.title.lowercase() }
            ?: emptyList()
    }

    suspend fun findByFolder(folderName: String): ManagedInstallManifest? = withContext(Dispatchers.IO) {
        list().firstOrNull { it.folderName.equals(folderName, ignoreCase = true) }
    }

    suspend fun save(manifest: ManagedInstallManifest) = withContext(Dispatchers.IO) {
        manifestFile(manifest.folderName).writeText(json.encodeToString(manifest))
    }

    suspend fun delete(folderName: String) = withContext(Dispatchers.IO) {
        manifestFile(folderName).delete()
    }

    private fun manifestDir(): File {
        return File(appContext.filesDir, "managed-mods").apply { mkdirs() }
    }

    private fun manifestFile(folderName: String): File {
        val safeName = folderName.lowercase().replace(Regex("""[^a-z0-9._-]+"""), "_")
        return File(manifestDir(), "$safeName.json")
    }
}
