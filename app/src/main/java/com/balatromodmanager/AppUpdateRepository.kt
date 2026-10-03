package com.balatromodmanager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateNotice(
    val tag: String,
    val releaseUrl: String,
)

class AppUpdateRepository(
    context: Context,
    private val installedVersion: String,
    private val json: Json = Json,
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val checkMutex = Mutex()

    suspend fun checkForUpdate(): AppUpdateNotice? = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cachedNotice()
            val checkedAt = preferences.getLong(KEY_CHECKED_AT, 0L)
            val attemptedAt = preferences.getLong(KEY_ATTEMPTED_AT, 0L)
            if (now - checkedAt < SUCCESS_CACHE_MS || now - attemptedAt < RETRY_DELAY_MS) {
                cached
            } else {
                preferences.edit().putLong(KEY_ATTEMPTED_AT, now).apply()
                val release = runCatching { fetchLatestRelease() }.getOrNull()
                if (release == null) {
                    cached
                } else {
                    preferences.edit()
                        .putLong(KEY_CHECKED_AT, now)
                        .putString(KEY_TAG, release.tag)
                        .putString(KEY_URL, release.releaseUrl)
                        .apply()
                    release.takeIf {
                        isNewerVersion(it.tag, installedVersion) &&
                            it.tag != preferences.getString(KEY_DISMISSED_TAG, null)
                    }
                }
            }
        }
    }

    fun dismiss(notice: AppUpdateNotice) {
        preferences.edit().putString(KEY_DISMISSED_TAG, notice.tag).apply()
    }

    private fun cachedNotice(): AppUpdateNotice? {
        val tag = preferences.getString(KEY_TAG, null) ?: return null
        val releaseUrl = preferences.getString(KEY_URL, null) ?: return null
        if (tag == preferences.getString(KEY_DISMISSED_TAG, null)) return null
        return AppUpdateNotice(tag, releaseUrl)
            .takeIf { isNewerVersion(it.tag, installedVersion) }
    }

    private fun fetchLatestRelease(): AppUpdateNotice {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BalatroModManagerAndroid")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}.")
            }
            val releases = connection.inputStream.bufferedReader().use { reader ->
                json.parseToJsonElement(reader.readText()).jsonArray
            }
            val release = releases.firstOrNull { item ->
                item.jsonObject["draft"]?.jsonPrimitive?.content?.toBoolean() != true
            }?.jsonObject ?: throw IllegalStateException("GitHub returned no published releases.")
            val tag = release["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val url = release["html_url"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(tag.isNotBlank() && url.startsWith("https://github.com/", ignoreCase = true)) {
                "GitHub returned an invalid release record."
            }
            return AppUpdateNotice(tag, url)
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewerVersion(releaseTag: String, installed: String): Boolean {
        val release = ParsedVersion.parse(releaseTag) ?: return releaseTag.trimStart('v') != installed.removeSuffix("-debug")
        val current = ParsedVersion.parse(installed.removeSuffix("-debug")) ?: return true
        val numbersComparison = compareNumberParts(release.numbers, current.numbers)
        if (numbersComparison != 0) return numbersComparison > 0
        return comparePrerelease(release.prerelease, current.prerelease) > 0
    }

    private fun compareNumberParts(left: List<Long>, right: List<Long>): Int {
        val count = maxOf(left.size, right.size)
        for (index in 0 until count) {
            val comparison = (left.getOrElse(index) { 0L }).compareTo(right.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun comparePrerelease(left: List<String>?, right: List<String>?): Int {
        if (left == null && right == null) return 0
        if (left == null) return 1
        if (right == null) return -1
        val count = maxOf(left.size, right.size)
        for (index in 0 until count) {
            if (index >= left.size) return -1
            if (index >= right.size) return 1
            val a = left[index]
            val b = right[index]
            val aNumber = a.toLongOrNull()
            val bNumber = b.toLongOrNull()
            val comparison = when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    private data class ParsedVersion(
        val numbers: List<Long>,
        val prerelease: List<String>?,
    ) {
        companion object {
            private val pattern = Regex("""^v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

            fun parse(value: String): ParsedVersion? {
                val match = pattern.matchEntire(value.trim()) ?: return null
                val numbers = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
                val prerelease = match.groupValues[2].takeIf(String::isNotBlank)?.split('.')
                return ParsedVersion(numbers, prerelease)
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_updates"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_ATTEMPTED_AT = "attempted_at"
        const val KEY_TAG = "latest_tag"
        const val KEY_URL = "latest_url"
        const val KEY_DISMISSED_TAG = "dismissed_tag"
        const val SUCCESS_CACHE_MS = 24L * 60L * 60L * 1_000L
        const val RETRY_DELAY_MS = 60L * 60L * 1_000L
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/danitsdev/balatro-mod-manager-android/releases?per_page=10"
    }
}
