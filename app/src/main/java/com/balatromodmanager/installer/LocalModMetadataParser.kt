package com.balatromodmanager.installer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class LocalModMetadata(
    val title: String = "",
    val author: String = "",
    val version: String = "",
    val declaredId: String = "",
) {
    val hasAnyValue: Boolean
        get() = title.isNotBlank() || author.isNotBlank() || version.isNotBlank() || declaredId.isNotBlank()
}

object LocalModMetadataParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(fileName: String, text: String): LocalModMetadata {
        val trimmed = text.trim()
        val fromJson = if (fileName.endsWith(".json", ignoreCase = true) || trimmed.startsWith("{")) {
            parseJson(trimmed)
        } else {
            LocalModMetadata()
        }
        val fromAssignments = parseAssignments(text)
        return fromJson.merge(fromAssignments)
    }

    private fun parseJson(text: String): LocalModMetadata {
        return runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            LocalModMetadata(
                title = obj.valueFor("name", "title", "display_name", "displayName", "mod_name"),
                author = obj.valueFor("author", "authors", "creator", "creators"),
                version = obj.valueFor("version", "mod_version"),
                declaredId = obj.valueFor("id", "mod_id", "slug", "prefix"),
            )
        }.getOrDefault(LocalModMetadata())
    }

    private fun parseAssignments(text: String): LocalModMetadata {
        return LocalModMetadata(
            title = text.assignmentValue("name", "title", "display_name", "displayName", "mod_name"),
            author = text.assignmentValue("author", "authors", "creator", "creators"),
            version = text.assignmentValue("version", "mod_version"),
            declaredId = text.assignmentValue("id", "mod_id", "slug", "prefix"),
        )
    }

    private fun LocalModMetadata.merge(fallback: LocalModMetadata): LocalModMetadata {
        return LocalModMetadata(
            title = title.ifBlank { fallback.title },
            author = author.ifBlank { fallback.author },
            version = version.ifBlank { fallback.version },
            declaredId = declaredId.ifBlank { fallback.declaredId },
        )
    }

    private fun JsonObject.valueFor(vararg keys: String): String {
        return keys.asSequence()
            .mapNotNull { key -> get(key)?.stringValue() }
            .map { it.trimMetadataValue() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun JsonElement.stringValue(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonArray -> mapNotNull { it.stringValue() }.joinToString(", ")
            else -> null
        }
    }

    private fun String.assignmentValue(vararg keys: String): String {
        val keyPattern = keys.joinToString("|") { Regex.escape(it) }
        val quoted = Regex("""(?im)(?:^|[\s,{;])(?:$keyPattern)\s*[:=]\s*(?:"([^"]{1,180})"|'([^']{1,180})')""")
        val quotedMatch = quoted.find(this)
        if (quotedMatch != null) {
            return quotedMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty().trimMetadataValue()
        }

        val bare = Regex("""(?im)(?:^|[\s,{;])(?:$keyPattern)\s*[:=]\s*([A-Za-z0-9_. @+\-]{1,120})""")
        return bare.find(this)?.groupValues?.getOrNull(1).orEmpty().trimMetadataValue()
    }

    private fun String.trimMetadataValue(): String {
        return trim()
            .trim(',', ';')
            .replace(Regex("""\s+"""), " ")
            .take(180)
    }
}
