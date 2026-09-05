package com.balatromodmanager.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalModMetadataParserTest {
    @Test
    fun parsesJsonManifestMetadata() {
        val metadata = LocalModMetadataParser.parse(
            "manifest.json",
            """
            {
              "id": "Danitdev@BalatroLostEdition",
              "name": "Balatro: Lost Edition",
              "authors": ["Danitdev", "ClickNoPaulo"],
              "version": "1.1.1"
            }
            """.trimIndent(),
        )

        assertEquals("Danitdev@BalatroLostEdition", metadata.declaredId)
        assertEquals("Balatro: Lost Edition", metadata.title)
        assertEquals("Danitdev, ClickNoPaulo", metadata.author)
        assertEquals("1.1.1", metadata.version)
    }

    @Test
    fun parsesTomlOrLuaStyleAssignments() {
        val metadata = LocalModMetadataParser.parse(
            "main.lua",
            """
            return {
              id = "nh6574.JokerDisplay",
              name = "JokerDisplay",
              author = "nh6574",
              version = "1.8.4"
            }
            """.trimIndent(),
        )

        assertEquals("nh6574.JokerDisplay", metadata.declaredId)
        assertEquals("JokerDisplay", metadata.title)
        assertEquals("nh6574", metadata.author)
        assertEquals("1.8.4", metadata.version)
    }

    @Test
    fun ignoresFilesWithoutMetadata() {
        val metadata = LocalModMetadataParser.parse("main.lua", "print('hello')")

        assertFalse(metadata.hasAnyValue)
    }
}
