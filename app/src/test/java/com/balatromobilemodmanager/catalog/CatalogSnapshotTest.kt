package com.balatromobilemodmanager.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSnapshotTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun bmiDetailFieldsParse() {
        val item = json.decodeFromString(
            BmiModItem.serializer(),
            """
            {
              "id": "nh6574@JokerDisplay",
              "name": "JokerDisplay",
              "download_url": "https://example.com/JokerDisplay.zip",
              "description_html": "<h1>JokerDisplay</h1><p>Useful info.</p>",
              "homepage": "https://github.com/nh6574/JokerDisplay"
            }
            """.trimIndent(),
        )

        assertTrue(item.downloadUrl.orEmpty().endsWith(".zip"))
        assertTrue(item.descriptionHtml.orEmpty().contains("Useful info."))
        assertTrue(item.homepage.orEmpty().contains("github.com"))
    }
}
