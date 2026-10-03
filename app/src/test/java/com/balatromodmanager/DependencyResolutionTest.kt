package com.balatromodmanager

import com.balatromodmanager.catalog.CatalogMod
import org.junit.Assert.assertEquals
import org.junit.Test

class DependencyResolutionTest {
    @Test
    fun `talisman requirement redirects to Amulet`() {
        val mod = CatalogMod(
            id = "dependent",
            title = "Dependent",
            author = "Author",
            requiresAmulet = true,
        )

        val missing = mod.missingDependencies(
            DependencyStatus(steamoddedInstalled = true, amuletInstalled = false),
        )

        assertEquals(listOf("Amulet"), missing.map { it.title })
    }

    @Test
    fun `dependency lookup resolves legacy Talisman name to Amulet`() {
        val amulet = CatalogMod(id = "Amulet", title = "Amulet", author = "MathIsFun0")

        assertEquals(amulet, listOf(amulet).findDependencyMod("Talisman"))
    }
}
