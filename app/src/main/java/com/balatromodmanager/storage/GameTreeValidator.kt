package com.balatromodmanager.storage

enum class TreeLayout {
    APP_ROOT_WITH_ASET,
}

sealed interface TreeValidation {
    data class Valid(
        val layout: TreeLayout,
        val canRead: Boolean,
        val canWrite: Boolean,
        val modFolderCount: Int,
        val modFolderNames: List<String>,
    ) : TreeValidation

    data class Invalid(
        val reason: TreeValidationError,
        val canRead: Boolean,
        val canWrite: Boolean,
        val message: String,
    ) : TreeValidation
}

enum class TreeValidationError {
    ROOT_UNAVAILABLE,
    ROOT_NOT_DIRECTORY,
    ROOT_READ_ONLY,
    MISSING_ASET_DIRECTORY,
    MISSING_MODS_DIRECTORY,
    MODS_NOT_DIRECTORY,
}

interface GameTreeNode {
    val name: String
    val isDirectory: Boolean
    val canRead: Boolean
    val canWrite: Boolean

    fun findFile(name: String): GameTreeNode?
    fun listFiles(): List<GameTreeNode>
}

class GameTreeValidator {
    fun validate(root: GameTreeNode?): TreeValidation {
        if (root == null) {
            return invalid(
                reason = TreeValidationError.ROOT_UNAVAILABLE,
                canRead = false,
                canWrite = false,
                message = "The selected folder could not be opened. Choose the LMM game folder again.",
            )
        }

        if (!root.isDirectory) {
            return invalid(
                reason = TreeValidationError.ROOT_NOT_DIRECTORY,
                canRead = root.canRead,
                canWrite = root.canWrite,
                message = "The selection is not a folder. Choose the folder that contains ASET.",
            )
        }

        if (!root.canWrite) {
            return invalid(
                reason = TreeValidationError.ROOT_READ_ONLY,
                canRead = root.canRead,
                canWrite = root.canWrite,
                message = "The selected folder is read-only. Grant read and write access in the Android picker.",
            )
        }

        val aset = root.findFile("ASET")
        if (aset == null || !aset.isDirectory) {
            return invalid(
                reason = TreeValidationError.MISSING_ASET_DIRECTORY,
                canRead = root.canRead,
                canWrite = root.canWrite,
                message = "ASET was not found. Choose the LMM game folder, not the official game or another folder.",
            )
        }

        val mods = aset.findFile("Mods")
        if (mods == null) {
            return invalid(
                reason = TreeValidationError.MISSING_MODS_DIRECTORY,
                canRead = root.canRead,
                canWrite = root.canWrite,
                message = "ASET was found, but ASET/Mods is missing. Open the game once before continuing.",
            )
        }

        if (!mods.isDirectory) {
            return invalid(
                reason = TreeValidationError.MODS_NOT_DIRECTORY,
                canRead = root.canRead,
                canWrite = root.canWrite,
                message = "ASET/Mods exists but is not a folder. Fix the LMM folder structure before continuing.",
            )
        }

        val modFolderNames = mods.listFiles()
            .filter { it.isDirectory }
            .map { it.name }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        return TreeValidation.Valid(
            layout = TreeLayout.APP_ROOT_WITH_ASET,
            canRead = root.canRead,
            canWrite = root.canWrite,
            modFolderCount = modFolderNames.size,
            modFolderNames = modFolderNames,
        )
    }

    private fun invalid(
        reason: TreeValidationError,
        canRead: Boolean,
        canWrite: Boolean,
        message: String,
    ): TreeValidation.Invalid {
        return TreeValidation.Invalid(
            reason = reason,
            canRead = canRead,
            canWrite = canWrite,
            message = message,
        )
    }
}
