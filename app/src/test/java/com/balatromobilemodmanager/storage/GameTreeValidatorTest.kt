package com.balatromobilemodmanager.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTreeValidatorTest {
    private val validator = GameTreeValidator()

    @Test
    fun validRootCountsDirectModFolders() {
        val root = node(
            "LMM Build",
            children = listOf(
                node(
                    "ASET",
                    children = listOf(
                        node(
                            "Mods",
                            children = listOf(
                                node("smods"),
                                node("CoolMod"),
                                node("README.md", isDirectory = false),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = validator.validate(root)

        assertTrue(result is TreeValidation.Valid)
        result as TreeValidation.Valid
        assertEquals(TreeLayout.APP_ROOT_WITH_ASET, result.layout)
        assertEquals(2, result.modFolderCount)
        assertEquals(listOf("CoolMod", "smods"), result.modFolderNames)
    }

    @Test
    fun validRootReportsSortedFolderNamesForDependencyDetection() {
        val root = node(
            "LMM Build",
            children = listOf(
                node(
                    "ASET",
                    children = listOf(
                        node(
                            "Mods",
                            children = listOf(
                                node("Talisman"),
                                node("smods-1.0.0-beta"),
                                node("Cryptid"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = validator.validate(root)

        assertTrue(result is TreeValidation.Valid)
        result as TreeValidation.Valid
        assertEquals(listOf("Cryptid", "smods-1.0.0-beta", "Talisman"), result.modFolderNames)
    }

    @Test
    fun missingAsetIsActionableInvalidResult() {
        val result = validator.validate(node("Wrong Root"))

        assertTrue(result is TreeValidation.Invalid)
        result as TreeValidation.Invalid
        assertEquals(TreeValidationError.MISSING_ASET_DIRECTORY, result.reason)
        assertTrue(result.message.contains("ASET"))
    }

    @Test
    fun missingModsIsDifferentFromMissingAset() {
        val root = node("LMM Build", children = listOf(node("ASET")))

        val result = validator.validate(root)

        assertTrue(result is TreeValidation.Invalid)
        result as TreeValidation.Invalid
        assertEquals(TreeValidationError.MISSING_MODS_DIRECTORY, result.reason)
    }

    @Test
    fun modsFileIsRejected() {
        val root = node(
            "LMM Build",
            children = listOf(
                node(
                    "ASET",
                    children = listOf(node("Mods", isDirectory = false)),
                ),
            ),
        )

        val result = validator.validate(root)

        assertTrue(result is TreeValidation.Invalid)
        result as TreeValidation.Invalid
        assertEquals(TreeValidationError.MODS_NOT_DIRECTORY, result.reason)
    }

    @Test
    fun readOnlyRootIsRejectedBeforeTraversal() {
        val result = validator.validate(node("LMM Build", canWrite = false))

        assertTrue(result is TreeValidation.Invalid)
        result as TreeValidation.Invalid
        assertEquals(TreeValidationError.ROOT_READ_ONLY, result.reason)
    }

    private fun node(
        name: String,
        isDirectory: Boolean = true,
        canRead: Boolean = true,
        canWrite: Boolean = true,
        children: List<FakeNode> = emptyList(),
    ) = FakeNode(name, isDirectory, canRead, canWrite, children)

    private data class FakeNode(
        override val name: String,
        override val isDirectory: Boolean,
        override val canRead: Boolean,
        override val canWrite: Boolean,
        private val children: List<FakeNode>,
    ) : GameTreeNode {
        override fun findFile(name: String): GameTreeNode? {
            return children.firstOrNull { it.name == name }
        }

        override fun listFiles(): List<GameTreeNode> {
            return children
        }
    }
}
