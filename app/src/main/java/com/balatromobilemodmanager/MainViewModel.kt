package com.balatromobilemodmanager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.balatromobilemodmanager.catalog.CatalogFilters
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.CatalogRepository
import com.balatromobilemodmanager.catalog.CatalogSortMode
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.catalog.allCategories
import com.balatromobilemodmanager.catalog.search
import com.balatromobilemodmanager.domain.matchesLocal
import com.balatromobilemodmanager.installer.InstallResult
import com.balatromobilemodmanager.installer.LocalModRepository
import com.balatromobilemodmanager.installer.LocalModStatus
import com.balatromobilemodmanager.installer.ManagedInstallRepository
import com.balatromobilemodmanager.installer.ModInstaller
import com.balatromobilemodmanager.installer.OperationJournalRepository
import com.balatromobilemodmanager.installer.OperationLogEntry
import com.balatromobilemodmanager.settings.VisualSettingsRepository
import com.balatromobilemodmanager.settings.VisualSettings
import com.balatromobilemodmanager.storage.AttachResult
import com.balatromobilemodmanager.storage.GameTreeRepository
import com.balatromobilemodmanager.storage.TreeValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Data Classes ---

data class MainUiState(
    val isLoading: Boolean,
    val isCatalogLoading: Boolean = true,
    val isCatalogRefreshing: Boolean = false,
    val persistedTreeUri: Uri? = null,
    val validation: TreeValidation? = null,
    val detectedBuilds: List<DetectedGameBuild> = emptyList(),
    val catalogMods: List<CatalogMod> = emptyList(),
    val catalogInfo: CatalogInfo = CatalogInfo(),
    val visibleMods: List<CatalogMod> = emptyList(),
    val categories: List<String> = emptyList(),
    val filters: CatalogFilters = CatalogFilters(),
    val visualSettings: VisualSettings = VisualSettings(),
    val managedInstalls: List<ManagedInstallManifest> = emptyList(),
    val localMods: List<LocalModStatus> = emptyList(),
    val operationHistory: List<OperationLogEntry> = emptyList(),
    val operation: OperationState = OperationState.Idle,
)

data class CatalogInfo(
    val source: String = "",
    val generatedAt: String = "",
)

sealed interface OperationState {
    data object Idle : OperationState
    data class Running(val message: String, val downloadingModId: String? = null) : OperationState
    data class Done(val message: String) : OperationState
    data class Error(val message: String) : OperationState
}

data class DetectedGameBuild(
    val appLabel: String,
    val packageName: String,
    val authority: String,
    val initialUri: Uri,
)

private data class AppData(
    val builds: List<DetectedGameBuild>,
    val mods: List<CatalogMod>,
    val catalogInfo: CatalogInfo,
    val installs: List<ManagedInstallManifest>,
    val localMods: List<LocalModStatus>,
    val history: List<OperationLogEntry>,
    val filters: CatalogFilters,
    val visualSettings: VisualSettings,
    val operation: OperationState,
    val isCatalogLoading: Boolean,
    val isCatalogRefreshing: Boolean,
)

private data class LocalState(
    val installs: List<ManagedInstallManifest>,
    val history: List<OperationLogEntry>,
)

private data class CatalogState(
    val mods: List<CatalogMod>,
    val info: CatalogInfo,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
)

// --- LmmBuildDetector ---

class LmmBuildDetector(private val appContext: Context) {
    suspend fun detect(): List<DetectedGameBuild> = withContext(Dispatchers.IO) {
        val packageManager = appContext.packageManager
        packageManager.queryIntentContentProviders(
            Intent(DocumentsContract.PROVIDER_INTERFACE),
            PackageManager.GET_META_DATA,
        ).mapNotNull { resolveInfo ->
            val provider = resolveInfo.providerInfo ?: return@mapNotNull null
            if (!provider.looksLikeLoveSaveProvider()) return@mapNotNull null
            val appInfo = provider.applicationInfo ?: return@mapNotNull null
            DetectedGameBuild(
                appLabel = packageManager.getApplicationLabel(appInfo).toString().ifBlank { provider.packageName },
                packageName = provider.packageName,
                authority = provider.authority,
                initialUri = DocumentsContract.buildRootUri(provider.authority, "root"),
            )
        }.sortedBy { it.appLabel.lowercase() }
    }

    private fun ProviderInfo.looksLikeLoveSaveProvider(): Boolean {
        return packageName != appContext.packageName &&
            authority?.endsWith(".saves") == true &&
            name.contains("LoveDocumentsProvider", ignoreCase = true)
    }
}

// --- MainViewModel ---

class MainViewModel(
    private val repository: GameTreeRepository,
    private val lmmBuildDetector: LmmBuildDetector,
    private val catalogRepository: CatalogRepository,
    private val manifestRepository: ManagedInstallRepository,
    private val localModRepository: LocalModRepository,
    private val operationJournalRepository: OperationJournalRepository,
    private val visualSettingsRepository: VisualSettingsRepository,
    private val installer: ModInstaller,
) : ViewModel() {
    private val detectedBuilds = MutableStateFlow<List<DetectedGameBuild>>(emptyList())
    private val catalogMods = MutableStateFlow<List<CatalogMod>>(emptyList())
    private val catalogInfo = MutableStateFlow(CatalogInfo())
    private val catalogLoading = MutableStateFlow(true)
    private val catalogRefreshing = MutableStateFlow(false)
    private val managedInstalls = MutableStateFlow<List<ManagedInstallManifest>>(emptyList())
    private val localMods = MutableStateFlow<List<LocalModStatus>>(emptyList())
    private val operationHistory = MutableStateFlow<List<OperationLogEntry>>(emptyList())
    private val filters = MutableStateFlow(CatalogFilters())
    private val visualSettings = MutableStateFlow(VisualSettings())
    private val operation = MutableStateFlow<OperationState>(OperationState.Idle)
    private val hydratedModIds = mutableSetOf<String>()
    private var catalogRefreshJob: Job? = null
    private var catalogCacheClearJob: Job? = null
    private val localState = combine(managedInstalls, operationHistory) { installs, history ->
        LocalState(installs, history)
    }
    private val catalogState = combine(catalogMods, catalogInfo, catalogLoading, catalogRefreshing) { mods, info, loading, refreshing ->
        CatalogState(mods, info, loading, refreshing)
    }

    private val catalogData = combine(
        detectedBuilds, catalogState, localState, localMods, filters,
    ) { builds, catalog, local, locals, activeFilters ->
        AppData(
            builds, catalog.mods, catalog.info, local.installs, locals, local.history,
            activeFilters, VisualSettings(), OperationState.Idle, catalog.isLoading, catalog.isRefreshing,
        )
    }

    private val appData = combine(catalogData, operation, visualSettings) { data, op, settings ->
        data.copy(operation = op, visualSettings = settings)
    }

    val uiState: StateFlow<MainUiState> = combine(repository.observeAttachment(), appData) { attachment, data ->
        val visible = data.mods.search(data.filters)
        MainUiState(
            isLoading = false,
            isCatalogLoading = data.isCatalogLoading,
            isCatalogRefreshing = data.isCatalogRefreshing,
            persistedTreeUri = attachment?.treeUri,
            validation = attachment?.validation,
            detectedBuilds = data.builds,
            catalogMods = data.mods,
            catalogInfo = data.catalogInfo,
            visibleMods = visible,
            categories = allCategories(data.mods),
            filters = data.filters,
            visualSettings = data.visualSettings,
            managedInstalls = data.installs,
            localMods = data.localMods,
            operationHistory = data.history,
            operation = data.operation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(isLoading = true),
    )

    init {
        loadCatalog()
        refreshManagedInstalls()
        refreshOperationHistory()
        observeVisualSettings()
    }

    fun attachTree(uri: Uri, grantFlags: Int) {
        viewModelScope.launch {
            val result = repository.attachTree(uri, grantFlags)
            if (result is AttachResult.Failed) repository.setTransientValidation(result.validation)
            refreshValidation()
        }
    }

    fun refreshValidation() {
        viewModelScope.launch { repository.validateCurrentTree() }
    }

    fun refreshLocalMods() {
        val treeUri = uiState.value.persistedTreeUri ?: return
        viewModelScope.launch { localMods.value = localModRepository.list(treeUri) }
    }

    fun detectLmmBuilds() {
        viewModelScope.launch { detectedBuilds.value = lmmBuildDetector.detect() }
    }

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setCategory(category: String?) {
        filters.value = filters.value.copy(selectedCategory = category)
    }

    fun setSortMode(sortMode: CatalogSortMode) {
        filters.value = filters.value.copy(sortMode = sortMode)
    }

    fun setVisualSettings(settings: VisualSettings) {
        visualSettings.value = settings
        viewModelScope.launch { visualSettingsRepository.save(settings) }
    }

    fun install(mod: CatalogMod) {
        val currentState = uiState.value
        val treeUri = currentState.persistedTreeUri ?: return
        val externalLocal = currentState.localMods.firstOrNull { local ->
            mod.matchesLocal(local) && currentState.managedInstalls.none { manifest ->
                manifest.folderName.equals(local.folderName, ignoreCase = true)
            }
        }
        viewModelScope.launch {
            val action = if (externalLocal == null) "install" else "get_official"
            operation.value = OperationState.Running(
                if (externalLocal == null) "Preparing ${mod.title}" else "Getting official version of ${mod.title}",
                downloadingModId = mod.id,
            )
            val modToInstall = runCatching { catalogRepository.resolveForInstall(mod) }
                .onSuccess(::upsertCatalogMod)
                .getOrElse { error ->
                    operation.value = OperationState.Error(error.message ?: "Could not resolve the download for ${mod.title}.")
                    recordOperation(action, mod.title, operation.value)
                    return@launch
                }
            operation.value = OperationState.Running("Downloading ${modToInstall.title}", downloadingModId = modToInstall.id)
            operation.value = when (val result = installer.install(
                treeUri = treeUri,
                mod = modToInstall,
                replaceUnmanagedFolder = externalLocal?.folderName,
            ) { phase ->
                operation.value = OperationState.Running(phase, downloadingModId = modToInstall.id)
            }) {
                is InstallResult.Installed -> {
                    managedInstalls.value = managedInstalls.value
                        .filterNot { it.folderName.equals(result.manifest.folderName, true) } + result.manifest
                    val previousEnabled = currentState.localMods.firstOrNull {
                        it.folderName.equals(result.manifest.folderName, true)
                    }?.enabled ?: true
                    localMods.value = localMods.value
                        .filterNot {
                            it.folderName.equals(result.manifest.folderName, true) ||
                                (externalLocal != null && it.folderName.equals(externalLocal.folderName, true))
                        } + LocalModStatus(
                            folderName = result.manifest.folderName,
                            enabled = previousEnabled,
                            title = result.manifest.title,
                            version = result.manifest.version,
                            declaredId = result.manifest.modId,
                        )
                    val oldFolder = externalLocal?.folderName
                    if (oldFolder != null && !oldFolder.equals(result.manifest.folderName, ignoreCase = true)) {
                        runCatching { localModRepository.remove(treeUri, oldFolder) }
                            .fold(
                                onSuccess = { OperationState.Done("Official version installed in ${result.manifest.folderName}; $oldFolder removed.") },
                                onFailure = { error -> OperationState.Error("Official version installed, but $oldFolder could not be removed: ${error.message}") },
                            )
                    } else {
                        OperationState.Done("${result.manifest.title} installed in ${result.manifest.folderName}.")
                    }
                }
                is InstallResult.Uninstalled -> OperationState.Done("${result.folderName} removed.")
                is InstallResult.Failed -> OperationState.Error(result.message)
            }
            recordOperation(action, modToInstall.title, operation.value)
            refreshManagedInstalls()
            refreshLocalMods()
            refreshOperationHistory()
            refreshValidation()
        }
    }

    fun installAll(mods: List<CatalogMod>) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val targets = mods.distinctBy { it.id }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            targets.forEachIndexed { index, mod ->
                operation.value = OperationState.Running("Updating ${index + 1}/${targets.size}: ${mod.title}", downloadingModId = mod.id)
                val modToInstall = runCatching { catalogRepository.resolveForInstall(mod) }
                    .onSuccess(::upsertCatalogMod)
                    .getOrElse { error ->
                        failCount += 1
                        recordOperation("update", mod.title, OperationState.Error(error.message ?: "Could not resolve download."))
                        return@forEachIndexed
                    }
                val resultState = when (val result = installer.install(treeUri, modToInstall) { phase ->
                    operation.value = OperationState.Running("${index + 1}/${targets.size}: $phase", downloadingModId = mod.id)
                }) {
                    is InstallResult.Installed -> { successCount += 1; OperationState.Done("${result.manifest.title} updated.") }
                    is InstallResult.Uninstalled -> OperationState.Done("${result.folderName} removed.")
                    is InstallResult.Failed -> { failCount += 1; OperationState.Error(result.message) }
                }
                recordOperation("update", modToInstall.title, resultState)
            }
            operation.value = if (failCount == 0) OperationState.Done("$successCount updates completed.")
            else OperationState.Error("$successCount updates completed; $failCount failed.")
            refreshManagedInstalls()
            refreshLocalMods()
            refreshOperationHistory()
            refreshValidation()
        }
    }

    fun uninstall(manifest: ManagedInstallManifest) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val previousLocals = localMods.value
        val previousManaged = managedInstalls.value
        localMods.value = previousLocals.filterNot { it.folderName.equals(manifest.folderName, true) }
        managedInstalls.value = previousManaged.filterNot { it.folderName.equals(manifest.folderName, true) }
        viewModelScope.launch {
            operation.value = OperationState.Running("Removing ${manifest.title}")
            val resultState = when (val result = installer.uninstall(treeUri, manifest) { phase ->
                operation.value = OperationState.Running(phase)
            }) {
                is InstallResult.Installed -> OperationState.Done("${result.manifest.title} installed.")
                is InstallResult.Uninstalled -> OperationState.Done("${result.folderName} removed.")
                is InstallResult.Failed -> OperationState.Error(result.message)
            }
            operation.value = resultState
            if (resultState is OperationState.Error) {
                localMods.value = previousLocals
                managedInstalls.value = previousManaged
            } else operation.value = OperationState.Idle
            recordOperation("uninstall", manifest.title, resultState)
            refreshManagedInstalls()
            refreshLocalMods()
            refreshOperationHistory()
            refreshValidation()
        }
    }

    fun removeLocalMod(folderName: String) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val previousLocals = localMods.value
        localMods.value = previousLocals.filterNot { it.folderName.equals(folderName, true) }
        viewModelScope.launch {
            operation.value = OperationState.Running("Removing $folderName")
            val resultState = runCatching { localModRepository.remove(treeUri, folderName) }
                .fold(
                    onSuccess = { OperationState.Done("$folderName removed.") },
                    onFailure = { OperationState.Error(it.message ?: "Could not remove $folderName.") },
                )
            operation.value = resultState
            if (resultState is OperationState.Error) localMods.value = previousLocals
            else operation.value = OperationState.Idle
            recordOperation("uninstall", folderName, resultState)
            refreshLocalMods()
            refreshOperationHistory()
            refreshValidation()
        }
    }

    fun setLocalModEnabled(folderName: String, enabled: Boolean) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val previousLocals = localMods.value
        localMods.value = previousLocals.map {
            if (it.folderName.equals(folderName, true)) it.copy(enabled = enabled) else it
        }
        viewModelScope.launch {
            operation.value = OperationState.Running(if (enabled) "Enabling $folderName" else "Disabling $folderName")
            operation.value = runCatching { localModRepository.setEnabled(treeUri, folderName, enabled) }
                .fold(
                    onSuccess = { OperationState.Done(if (enabled) "$folderName enabled." else "$folderName disabled.") },
                    onFailure = { OperationState.Error(it.message ?: "Could not change $folderName state.") },
                )
            if (operation.value is OperationState.Error) localMods.value = previousLocals
            else operation.value = OperationState.Idle
            refreshLocalMods()
            refreshValidation()
        }
    }

    fun setLocalModsEnabled(folderNames: List<String>, enabled: Boolean) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val targets = folderNames.distinct()
        if (targets.isEmpty()) return
        val previousLocals = localMods.value
        localMods.value = previousLocals.map {
            if (targets.any { name -> name.equals(it.folderName, true) }) it.copy(enabled = enabled) else it
        }
        viewModelScope.launch {
            operation.value = OperationState.Running(
                if (enabled) "Enabling ${targets.size} mods" else "Disabling ${targets.size} mods",
            )
            operation.value = runCatching { localModRepository.setEnabled(treeUri, targets, enabled) }
                .fold(
                    onSuccess = { OperationState.Done(if (enabled) "${targets.size} mods enabled." else "${targets.size} mods disabled.") },
                    onFailure = { OperationState.Error(it.message ?: "Could not change selected mods.") },
                )
            if (operation.value is OperationState.Error) localMods.value = previousLocals
            else operation.value = OperationState.Idle
            refreshLocalMods()
            refreshValidation()
        }
    }

    fun clearOperation() { operation.value = OperationState.Idle }

    fun refreshCatalog() {
        refreshCatalogInBackground()
    }

    fun refreshCatalogIfStale() {
        if (catalogRepository.isCacheStale()) refreshCatalogInBackground()
    }

    fun clearCatalogCache() {
        catalogRefreshJob?.cancel()
        catalogMods.value = emptyList()
        catalogInfo.value = CatalogInfo()
        hydratedModIds.clear()
        catalogLoading.value = false
        catalogRefreshing.value = false
        catalogCacheClearJob = viewModelScope.launch {
            catalogRepository.clearCache()
        }
    }

    fun ensureCatalogAvailable() {
        if (catalogMods.value.isNotEmpty() || catalogRefreshJob?.isActive == true) return
        val pendingClear = catalogCacheClearJob
        catalogRefreshJob = viewModelScope.launch {
            pendingClear?.join()
            catalogLoading.value = true
            try {
                runCatching { catalogRepository.refreshSnapshot() }
                    .onSuccess(::applyCatalogSnapshot)
                    .onFailure { error ->
                        operation.value = OperationState.Error(
                            error.message ?: "Could not load the catalog.",
                        )
                    }
            } finally {
                catalogLoading.value = false
            }
        }
    }

    fun validateModDatabase() {
        val treeUri = uiState.value.persistedTreeUri ?: return
        viewModelScope.launch {
            operation.value = OperationState.Running("Validating mod database")
            val validation = repository.validateCurrentTree()
            if (validation !is TreeValidation.Valid) {
                operation.value = OperationState.Error("ASET/Mods could not be validated.")
                return@launch
            }
            val existingFolders = validation.modFolderNames.mapTo(hashSetOf()) { it.lowercase() }
            manifestRepository.list()
                .filterNot { it.folderName.lowercase() in existingFolders }
                .forEach { manifestRepository.delete(it.folderName) }
            managedInstalls.value = manifestRepository.list()
            localMods.value = localModRepository.list(treeUri)
            operation.value = OperationState.Idle
        }
    }

    fun hydrateCatalogMod(modId: String) {
        val current = catalogMods.value.firstOrNull { it.id == modId } ?: return
        if (!hydratedModIds.add(modId)) return
        viewModelScope.launch {
            runCatching { catalogRepository.hydrateMod(current) }
                .onSuccess(::upsertCatalogMod)
                .onFailure { hydratedModIds.remove(modId) }
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            catalogLoading.value = true
            val cached = runCatching { catalogRepository.loadLocalSnapshot() }.getOrNull()
            if (cached != null && cached.mods.isNotEmpty()) {
                applyCatalogSnapshot(cached)
            }

            if (cached == null || cached.mods.isEmpty()) {
                runCatching { catalogRepository.refreshSnapshot() }
                    .onSuccess(::applyCatalogSnapshot)
                    .onFailure { error ->
                        operation.value = OperationState.Error(
                            error.message ?: "Could not load the catalog.",
                        )
                    }
            } else if (catalogRepository.isCacheStale()) {
                refreshCatalogInBackground()
            }
            catalogLoading.value = false
        }
    }

    private fun refreshCatalogInBackground() {
        if (catalogRefreshJob?.isActive == true) return
        catalogRefreshJob = viewModelScope.launch {
            catalogRefreshing.value = true
            try {
                runCatching { catalogRepository.refreshSnapshot() }
                    .onSuccess(::applyCatalogSnapshot)
            } finally {
                catalogRefreshing.value = false
            }
        }
    }

    private fun applyCatalogSnapshot(snapshot: com.balatromobilemodmanager.catalog.CatalogSnapshot) {
        catalogMods.value = snapshot.mods
        catalogInfo.value = CatalogInfo(snapshot.source, snapshot.generatedAt)
    }

    private fun upsertCatalogMod(mod: CatalogMod) {
        catalogMods.value = catalogMods.value.map { if (it.id == mod.id) mod else it }
    }

    private fun observeVisualSettings() {
        viewModelScope.launch { visualSettingsRepository.observe().collect { visualSettings.value = it } }
    }

    private fun refreshManagedInstalls() {
        viewModelScope.launch { managedInstalls.value = manifestRepository.list() }
    }

    private fun refreshOperationHistory() {
        viewModelScope.launch { operationHistory.value = operationJournalRepository.list() }
    }

    private suspend fun recordOperation(action: String, title: String, state: OperationState) {
        val status = when (state) {
            is OperationState.Done -> "success"
            is OperationState.Error -> "error"
            OperationState.Idle -> "idle"
            is OperationState.Running -> "running"
        }
        val message = when (state) {
            is OperationState.Done -> state.message
            is OperationState.Error -> state.message
            OperationState.Idle -> ""
            is OperationState.Running -> state.message
        }
        operationJournalRepository.append(
            OperationLogEntry(
                id = "${System.currentTimeMillis()}-$action-$title",
                action = action, title = title, status = status, message = message, epochMs = System.currentTimeMillis(),
            ),
        )
    }

    class Factory(
        private val repository: GameTreeRepository,
        private val lmmBuildDetector: LmmBuildDetector,
        private val catalogRepository: CatalogRepository,
        private val manifestRepository: ManagedInstallRepository,
        private val localModRepository: LocalModRepository,
        private val operationJournalRepository: OperationJournalRepository,
        private val visualSettingsRepository: VisualSettingsRepository,
        private val installer: ModInstaller,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, lmmBuildDetector, catalogRepository, manifestRepository, localModRepository, operationJournalRepository, visualSettingsRepository, installer) as T
        }
    }
}
