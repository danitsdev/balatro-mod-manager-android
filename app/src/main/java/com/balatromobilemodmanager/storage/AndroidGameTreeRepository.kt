package com.balatromobilemodmanager.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AndroidGameTreeRepository(
    private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
    private val validator: GameTreeValidator = GameTreeValidator(),
) : GameTreeRepository {
    private val transientValidation = MutableStateFlow<TreeValidation?>(null)

    override fun observeAttachment(): Flow<GameTreeAttachment?> {
        val persistedUri = dataStore.data.map { preferences ->
            preferences[Keys.treeUri]?.let(Uri::parse)
        }

        return combine(persistedUri, transientValidation) { uri, transient ->
            when {
                uri != null -> GameTreeAttachment(uri, validateUri(uri))
                transient != null -> GameTreeAttachment(Uri.EMPTY, transient)
                else -> null
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun attachTree(uri: Uri, grantFlags: Int): AttachResult {
        return withContext(Dispatchers.IO) {
            val persistableFlags = grantFlags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, persistableFlags)
            }.onFailure {
                return@withContext AttachResult.Failed(
                    TreeValidation.Invalid(
                        reason = TreeValidationError.ROOT_UNAVAILABLE,
                        canRead = false,
                        canWrite = false,
                        message = "Android could not keep access to this folder. Select it again and allow permanent access when prompted.",
                    ),
                )
            }

            val validation = validateUri(uri)
            if (validation is TreeValidation.Invalid) {
                transientValidation.value = validation
                return@withContext AttachResult.Failed(validation)
            }

            dataStore.edit { preferences ->
                preferences[Keys.treeUri] = uri.toString()
            }
            transientValidation.value = null
            AttachResult.Attached(GameTreeAttachment(uri, validation))
        }
    }

    override suspend fun validateCurrentTree(): TreeValidation {
        return withContext(Dispatchers.IO) {
            val uri = dataStore.data.first()[Keys.treeUri]?.let(Uri::parse)
            val validation = validateUri(uri)
            if (uri != null) {
                transientValidation.value = validation
            }
            validation
        }
    }

    override suspend fun setTransientValidation(validation: TreeValidation) {
        transientValidation.value = validation
    }

    private fun validateUri(uri: Uri?): TreeValidation {
        val root = uri?.let { DocumentFile.fromTreeUri(appContext, it) }
        return validator.validate(root?.let(::DocumentFileGameTreeNode))
    }

    private object Keys {
        val treeUri = stringPreferencesKey("game_tree_uri")
    }
}

private class DocumentFileGameTreeNode(
    private val documentFile: DocumentFile,
) : GameTreeNode {
    override val name: String = documentFile.name.orEmpty()
    override val isDirectory: Boolean = documentFile.isDirectory
    override val canRead: Boolean = documentFile.canRead()
    override val canWrite: Boolean = documentFile.canWrite()

    override fun findFile(name: String): GameTreeNode? {
        return documentFile.findFile(name)?.let(::DocumentFileGameTreeNode)
    }

    override fun listFiles(): List<GameTreeNode> {
        return documentFile.listFiles().map(::DocumentFileGameTreeNode)
    }
}
