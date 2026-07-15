package com.balatromobilemodmanager.storage

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface GameTreeRepository {
    fun observeAttachment(): Flow<GameTreeAttachment?>
    suspend fun attachTree(uri: Uri, grantFlags: Int): AttachResult
    suspend fun validateCurrentTree(): TreeValidation
    suspend fun setTransientValidation(validation: TreeValidation)
}

data class GameTreeAttachment(
    val treeUri: Uri,
    val validation: TreeValidation,
)

sealed interface AttachResult {
    data class Attached(val attachment: GameTreeAttachment) : AttachResult
    data class Failed(val validation: TreeValidation.Invalid) : AttachResult
}
