package com.balatromodmanager.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModSearchTest {
    private val mods = listOf(
        CatalogMod(
            id = "SteamoddedTeam@Steamodded",
            title = "Steamodded",
            author = "Steamodded Team",
            categories = listOf("API"),
            folderName = "smods",
        ),
        CatalogMod(
            id = "MathIsFun0@Talisman",
            title = "Talisman",
            author = "MathIsFun0",
            categories = listOf("API", "Technical"),
            requiresSteamodded = true,
        ),
        CatalogMod(
            id = "Example@JokerPack",
            title = "Joker Pack",
            author = "Example",
            categories = listOf("Joker"),
        ),
    )

    @Test
    fun searchFindsSubsequenceTypos() {
        val result = mods.search(CatalogFilters(query = "stmdd"))

        assertEquals("Steamodded", result.first().title)
    }

    @Test
    fun categoryFilterKeepsMatchingMods() {
        val result = mods.search(CatalogFilters(selectedCategory = "API"))

        assertEquals(listOf("Steamodded", "Talisman"), result.map { it.title }.sorted())
    }

    @Test
    fun folderNamesAreSanitized() {
        assertEquals("Bad_Name_", """Bad/Name?""".sanitizeFolderName())
        assertTrue("".sanitizeFolderName().isNotBlank())
    }

    @Test
    fun automaticInstallAcceptsExtensionlessHttpsArchives() {
        assertTrue(CatalogMod(id = "a", title = "A", author = "A", downloadUrl = "https://example.com/mod.zip").supportsAutomaticInstall)
        assertTrue(
            CatalogMod(
                id = "author@mod",
                title = "Mod",
                author = "Author",
                downloadUrl = "https://thunderstore.io/package/download/author/mod/1.0.0/",
            ).supportsAutomaticInstall,
        )
        assertTrue(
            CatalogMod(
                id = "banner",
                title = "Banner",
                author = "SylviBlossom",
                downloadUrl = "https://codeload.github.com/SylviBlossom/Banner/zip/refs/tags/v1.2.1",
            ).supportsAutomaticInstall,
        )
        assertTrue(!CatalogMod(id = "b", title = "B", author = "B", downloadUrl = "https://example.com/mod.tar").supportsAutomaticInstall)
        assertTrue(!CatalogMod(id = "c", title = "C", author = "C", downloadUrl = "http://example.com/mod.zip").supportsAutomaticInstall)
    }

    @Test
    fun duplicateLegacyAndCatalogEntriesCollapseByRepository() {
        val duplicates = listOf(
            CatalogMod(id = "Lost_Edition", title = "Lost Edition", author = "Danitsdev", repo = "https://github.com/danitsdev/Lost_Edition"),
            CatalogMod(id = "danitsdev@Lost_Edition", title = "Lost Edition", author = "Danitsdev", repo = "https://github.com/danitsdev/Lost_Edition/", thumbnailUrl = "thumb"),
        )

        val result = duplicates.deduplicatedCatalog()

        assertEquals(1, result.size)
        assertEquals("danitsdev@Lost_Edition", result.single().id)
    }

    @Test
    fun differentModsInTheSameRepositoryArePreserved() {
        val result = listOf(
            CatalogMod(id = "author@One", title = "First Mod", author = "Author", repo = "https://github.com/author/modpack"),
            CatalogMod(id = "author@Two", title = "Second Mod", author = "Author", repo = "https://github.com/author/modpack"),
        ).deduplicatedCatalog()

        assertEquals(listOf("First Mod", "Second Mod"), result.map { it.title })
    }

    @Test
    fun canonicalCryptidAndLostEditionRemainSearchableAfterDeduplication() {
        val result = listOf(
            CatalogMod(id = "Cryptid", title = "Cryptid", author = "MathIsFun0", repo = "https://github.com/MathIsFun0/Cryptid"),
            CatalogMod(id = "MathIsFun0@Cryptid", title = "Cryptid", author = "MathIsFun0", repo = "https://github.com/MathIsFun0/Cryptid"),
            CatalogMod(id = "Lost_Edition", title = "Lost Edition", author = "Danitsdev", repo = "https://github.com/danitsdev/Lost_Edition"),
            CatalogMod(id = "danitsdev@Lost_Edition", title = "Lost Edition", author = "Danitsdev", repo = "https://github.com/danitsdev/Lost_Edition"),
        ).deduplicatedCatalog()

        assertEquals(1, result.search(CatalogFilters(query = "Cryptid")).size)
        assertEquals(1, result.search(CatalogFilters(query = "Lost Edition")).size)
    }

    @Test
    fun winningDuplicateIsKeptAsOneCompleteRecord() {
        val result = listOf(
            CatalogMod(
                id = "MathIsFun0@Cryptid", title = "Cryptid", author = "MathIsFun0",
                repo = "https://github.com/MathIsFun0/Cryptid", description = "Long index description",
            ),
            CatalogMod(
                id = "Cryptid", title = "Cryptid", author = "MathIsFun0",
                repo = "https://github.com/MathIsFun0/Cryptid", downloadsTotal = 149_393,
                thumbnailUrl = "https://catalog.example/Cryptid.webp", description = "Short",
            ),
        ).deduplicatedCatalog().single()

        assertEquals("Cryptid", result.id)
        assertEquals(149_393, result.downloadsTotal)
        assertEquals("https://catalog.example/Cryptid.webp", result.thumbnailUrl)
        assertEquals("Short", result.description)
    }

    @Test
    fun searchDoesNotMatchCategoryOrFolderOnly() {
        val result = listOf(
            CatalogMod(id = "one", title = "Actual Mod", author = "Author", categories = listOf("BoobooBalatro"), folderName = "BoobooBalatro"),
        ).search(CatalogFilters(query = "BoobooBalatro"))

        assertTrue(result.isEmpty())
    }
}
