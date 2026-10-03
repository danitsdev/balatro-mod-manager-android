package com.balatromodmanager.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSnapshotTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun thunderstorePackageFieldsParse() {
        val item = json.decodeFromString(
            ThunderstorePackage.serializer(),
            """
            {
              "uuid4": "935bea81-8ab6-45be-b592-9fad1f6cebf3",
              "full_name": "nh6574-JokerDisplay",
              "owner": "nh6574",
              "name": "JokerDisplay",
              "package_url": "https://thunderstore.io/c/balatro/p/nh6574/JokerDisplay/",
              "date_updated": "2026-10-02T20:51:59.547381Z",
              "is_deprecated": false,
              "categories": ["Mods", "Tools"],
              "versions": [
                {
                  "description": "# JokerDisplay\\nUseful info.",
                  "icon": "https://ccdn.thunderstore.io/live/repository/icons/nh6574-JokerDisplay-1.0.0.png",
                  "version_number": "1.0.0",
                  "dependencies": ["Steamodded-Steamodded-26.1002.0"],
                  "download_url": "https://thunderstore.io/package/download/nh6574/JokerDisplay/1.0.0/",
                  "downloads": 1200,
                  "date_created": "2026-10-02T20:51:59.079625Z",
                  "website_url": "https://github.com/nh6574/JokerDisplay",
                  "is_active": true,
                  "uuid4": "93470b4a-7ae4-4fb3-a20d-9e7307536c3b"
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(item.uuid.isNotBlank())
        assertTrue(item.versions.single().downloadUrl.endsWith("1.0.0/"))
        assertTrue(item.versions.single().description.contains("Useful info."))
        assertTrue(item.versions.single().websiteUrl.contains("github.com"))
    }
}
