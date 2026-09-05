package com.balatromodmanager.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDeduplicationTest {
    @Test
    fun `keeps the most downloaded record for duplicate titles`() {
        val popular = mod(
            id = "author@popular",
            title = "Hot Potato",
            downloads = 10_694,
            thumbnail = "https://example.test/popular.webp",
        )
        val duplicate = mod(
            id = "author@patch",
            title = "Hot Potato",
            downloads = 2_587,
            thumbnail = "https://example.test/patch.webp",
        )

        val result = listOf(popular, duplicate).deduplicatedCatalog()

        assertEquals(1, result.size)
        assertEquals(popular, result.single())
    }

    @Test
    fun `deduplicates titles without case or surrounding whitespace`() {
        val older = mod(id = "older", title = " Cryptid ", downloads = 100)
        val current = mod(id = "current", title = "cryptid", downloads = 200)

        val result = listOf(older, current).deduplicatedCatalog()

        assertEquals(listOf(current), result)
    }

    private fun mod(
        id: String,
        title: String,
        downloads: Long,
        thumbnail: String = "",
    ) = CatalogMod(
        id = id,
        title = title,
        author = "Author",
        downloadsTotal = downloads,
        thumbnailUrl = thumbnail,
    )
}
