package com.balatromobilemodmanager.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class OperationLogEntry(
    val id: String,
    val action: String,
    val title: String,
    val status: String,
    val message: String,
    val epochMs: Long,
)

class OperationJournalRepository(
    private val appContext: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    suspend fun list(): List<OperationLogEntry> = withContext(Dispatchers.IO) {
        readEntries()
    }

    suspend fun append(entry: OperationLogEntry) = withContext(Dispatchers.IO) {
        val entries = (listOf(entry) + readEntries()).take(50)
        journalFile().writeText(json.encodeToString(ListSerializer(OperationLogEntry.serializer()), entries))
    }

    private fun readEntries(): List<OperationLogEntry> {
        val file = journalFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(OperationLogEntry.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private fun journalFile(): File {
        return File(appContext.filesDir, "operation-journal.json")
    }
}
