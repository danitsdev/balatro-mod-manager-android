package com.balatromobilemodmanager.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveInspectorTest {
    private val inspector = ZipArchiveInspector()

    @Test
    fun acceptsNormalArchive() {
        val zip = createZip("mod/main.lua" to "print('ok')")

        val inspection = inspector.inspect(zip)

        assertEquals(1, inspection.fileCount)
        assertEquals("mod", inspection.installRoot)
    }

    @Test
    fun keepsRootWhenArchiveContainsTopLevelFiles() {
        val inspection = inspector.inspect(
            createZip(
                "main.lua" to "print('ok')",
                "assets/card.png" to "image",
            ),
        )

        assertEquals(null, inspection.installRoot)
    }

    @Test
    fun ignoresMacMetadataWhenDetectingWrapper() {
        val inspection = inspector.inspect(
            createZip(
                "__MACOSX/._mod" to "metadata",
                "mod/main.lua" to "print('ok')",
            ),
        )

        assertEquals("mod", inspection.installRoot)
    }

    @Test(expected = ArchiveValidationException.UnsafePath::class)
    fun rejectsTraversal() {
        inspector.inspect(createZip("../outside.lua" to "bad"))
    }

    @Test(expected = ArchiveValidationException.UnsafePath::class)
    fun rejectsAbsoluteWindowsPath() {
        inspector.inspect(createZip("C:/outside.lua" to "bad"))
    }

    @Test(expected = ArchiveValidationException.UnsafePath::class)
    fun rejectsFileUsedAsDirectory() {
        inspector.inspect(
            createZip(
                "mod/assets" to "not a directory",
                "mod/assets/card.png" to "bad",
            ),
        )
    }

    @Test
    fun acceptsEmptyFileUsedAsDirectoryMarker() {
        val inspection = inspector.inspect(
            createZip(
                "mod/assets" to "",
                "mod/assets/card.png" to "ok",
            ),
        )

        assertEquals(1, inspection.fileCount)
        assertEquals(listOf("mod/assets/card.png"), inspection.entries.map { it.path })
    }

    @Test(expected = ArchiveValidationException.EmptyArchive::class)
    fun rejectsEmptyArchive() {
        val file = File.createTempFile("empty", ".zip")
        ZipOutputStream(file.outputStream()).use { }

        inspector.inspect(file)
    }

    @Test
    fun rejectsDuplicateFilePathsBeforeStagingExtraction() {
        val zip = createZip(
            "mod/main.lua" to "print('first')",
            "mod\\main.lua" to "print('second')",
        )

        val error = runCatching { inspector.inspect(zip) }.exceptionOrNull()

        assertTrue(error is ArchiveValidationException.UnsafePath)
        assertTrue(error?.message.orEmpty().contains("duplicate path"))
    }

    @Test
    fun acceptsLargeWrappedModArchive() {
        val file = File.createTempFile("large-mod", ".zip")
        ZipOutputStream(file.outputStream()).use { output ->
            repeat(1_822) { index ->
                output.putNextEntry(ZipEntry("All-In-Jest-0.7.2/Items/Jokers/joker-$index.lua"))
                output.write("return $index".toByteArray())
                output.closeEntry()
            }
        }

        val inspection = inspector.inspect(file)

        assertEquals(1_822, inspection.fileCount)
        assertEquals("All-In-Jest-0.7.2", inspection.installRoot)
    }

    private fun createZip(vararg files: Pair<String, String>): File {
        val file = File.createTempFile("mod", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            files.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
