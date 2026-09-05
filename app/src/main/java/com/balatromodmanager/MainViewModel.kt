package com.balatromodmanager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.balatromodmanager.catalog.CatalogFilters
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.CatalogRepository
import com.balatromodmanager.catalog.CatalogSortMode
import com.balatromodmanager.catalog.ManagedInstallManifest
import com.balatromodmanager.catalog.allCategories
import com.balatromodmanager.catalog.search
import com.balatromodmanager.domain.matchesLocal
import com.balatromodmanager.installer.InstallResult
import com.balatromodmanager.installer.LocalModRepository
import com.balatromodmanager.installer.LocalModStatus
import com.balatromodmanager.installer.ManagedInstallRepository
import com.balatromodmanager.installer.ModInstaller
import com.balatromodmanager.settings.VisualSettingsRepository
import com.balatromodmanager.settings.VisualSettings
import com.balatromodmanager.storage.AttachResult
import com.balatromodmanager.storage.GameTreeRepository
import com.balatromodmanager.storage.TreeValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    val operation: OperationState = OperationState.Idle,
)

data class CatalogInfo(
    val generatedAt: String = "",
)

sealed interface OperationState {
    data object Idle : OperationState
    data class Running(
        val message: String,
        val downloadingModId: String? = null,
        val progress: Float? = null,
        val isLocalToggle: Boolean = false,
    ) : OperationState
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
    val visibleMods: List<CatalogMod>,
    val categories: List<String>,
    val catalogInfo: CatalogInfo,
    val installs: List<ManagedInstallManifest>,
    val localMods: List<LocalModStatus>,
    val filters: CatalogFilters,
    val visualSettings: VisualSettings,
    val operation: OperationState,
    val isCatalogLoading: Boolean,
    val isCatalogRefreshing: Boolean,
)

private data class CatalogState(
    val mods: List<CatalogMod>,
    val info: CatalogInfo,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
)

private data class CatalogPresentation(
    val visibleMods: List<CatalogMod>,
    val categories: List<String>,
)

private data class PendingEnabledState(
    val folderName: String,
    val enabled: Boolean,
)

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

class MainViewModel(
    private val repository: GameTreeRepository,
    private val lmmBuildDetector: LmmBuildDetector,
    private val catalogRepository: CatalogRepository,
    private val manifestRepository: ManagedInstallRepository,
    private val localModRepository: LocalModRepository,
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
    private val filters = MutableStateFlow(CatalogFilters())
    private val visualSettings = MutableStateFlow(VisualSettings())
    private val operation = MutableStateFlow<OperationState>(OperationState.Idle)
    private val hydratedModIds = mutableSetOf<String>()
    private var catalogRefreshJob: Job? = null
    private var catalogCacheClearJob: Job? = null
    private val localModsIoMutex = Mutex()
    private val modOperationMutex = Mutex()
    private var localModsRefreshJob: Job? = null
    private var localModsMutationJob: Job? = null
    private var localModsRefreshRequested = false
    private val pendingEnabledStates = linkedMapOf<String, PendingEnabledState>()
    private var localModsRevision = 0L
    private val catalogState = combine(catalogMods, catalogInfo, catalogLoading, catalogRefreshing) { mods, info, loading, refreshing ->
        CatalogState(mods, info, loading, refreshing)
    }

    private val catalogData = combine(
        detectedBuilds, catalogState, managedInstalls, localMods, filters,
    ) { builds, catalog, installs, locals, activeFilters ->
        AppData(
            builds, catalog.mods, emptyList(), emptyList(), catalog.info, installs, locals,
            activeFilters, VisualSettings(), OperationState.Idle, catalog.isLoading, catalog.isRefreshing,
        )
    }

    private val catalogVisibleMods = combine(catalogMods, filters) { mods, activeFilters ->
        mods.search(activeFilters)
    }

    private val catalogCategories = catalogMods.map(::allCategories)

    private val catalogPresentation = combine(catalogVisibleMods, catalogCategories) { visibleMods, categories ->
        CatalogPresentation(
            visibleMods = visibleMods,
            categories = categories,
        )
    }

    private val catalogDataWithPresentation = combine(catalogData, catalogPresentation) { data, presentation ->
        data.copy(
            visibleMods = presentation.visibleMods,
            categories = presentation.categories,
        )
    }

    private val appData = combine(catalogDataWithPresentation, operation, visualSettings) { data, op, settings ->
        data.copy(operation = op, visualSettings = settings)
    }

    val uiState: StateFlow<MainUiState> = combine(repository.observeAttachment(), appData) { attachment, data ->
        MainUiState(
            isLoading = false,
            isCatalogLoading = data.isCatalogLoading,
            isCatalogRefreshing = data.isCatalogRefreshing,
            persistedTreeUri = attachment?.treeUri,
            validation = attachment?.validation,
            detectedBuilds = data.builds,
            catalogMods = data.mods,
            catalogInfo = data.catalogInfo,
            visibleMods = data.visibleMods,
            categories = data.categories,
            filters = data.filters,
            visualSettings = data.visualSettings,
            managedInstalls = data.installs,
            localMods = data.localMods,
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
        if (modOperationMutex.isLocked) {
            localModsRefreshRequested = true
            return
        }
        val revision = localModsRevision
        val pendingMutation = localModsMutationJob
        localModsRefreshJob?.cancel()
        localModsRefreshJob = viewModelScope.launch {
            pendingMutation?.join()
            runCatchingCancellable {
                localModsIoMutex.withLock { localModRepository.list(treeUri) }
            }.onSuccess { refreshed ->
                if (revision == localModsRevision) localMods.value = refreshed
            }
        }
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
        if (!modOperationMutex.tryLock()) return
        operation.value = OperationState.Running(
            if (externalLocal == null) "Preparing ${mod.title}" else "Getting official version of ${mod.title}",
            downloadingModId = mod.id,
        )
        viewModelScope.launch {
            try {
                val modToInstall = runCatchingCancellable { catalogRepository.resolveForInstall(mod) }
                    .getOrElse { error ->
                        operation.value = OperationState.Error(error.message ?: "Could not resolve the download for ${mod.title}.")
                        return@launch
                    }
                operation.value = OperationState.Running("Downloading ${modToInstall.title}", downloadingModId = modToInstall.id)
                operation.value = when (val result = installer.install(
                    treeUri = treeUri,
                    mod = modToInstall,
                    replaceUnmanagedFolder = externalLocal?.folderName,
                    replaceUnmanagedDisabled = externalLocal?.enabled == false,
                ) { progress ->
                    operation.value = OperationState.Running(
                        progress.message,
                        downloadingModId = modToInstall.id,
                        progress = progress.fraction,
                    )
                }) {
                    is InstallResult.Installed -> {
                        managedInstalls.value = managedInstalls.value
                            .filterNot { it.folderName.equals(result.manifest.folderName, true) } + result.manifest
                        val replacingExisting = externalLocal != null || currentState.managedInstalls.any {
                            it.folderName.equals(result.manifest.folderName, true)
                        }
                        val previousEnabled = currentState.localMods.firstOrNull {
                            it.folderName.equals(result.manifest.folderName, true)
                        }?.enabled ?: externalLocal?.enabled ?: !replacingExisting
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
                            runCatchingCancellable { localModRepository.remove(treeUri, oldFolder) }
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
                refreshManagedInstalls()
                refreshLocalMods()
                refreshValidation()
            } finally {
                finishModOperation()
            }
        }
    }

    fun installAll(mods: List<CatalogMod>) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val targets = mods.distinctBy { it.id }
        if (targets.isEmpty()) return
        if (!modOperationMutex.tryLock()) return
        operation.value = OperationState.Running("Updating 1/${targets.size}: ${targets.first().title}", downloadingModId = targets.first().id)
        viewModelScope.launch {
            try {
                var successCount = 0
                var failCount = 0
                targets.forEachIndexed { index, mod ->
                    operation.value = OperationState.Running("Updating ${index + 1}/${targets.size}: ${mod.title}", downloadingModId = mod.id)
                    val modToInstall = runCatchingCancellable { catalogRepository.resolveForInstall(mod) }
                        .getOrElse {
                            failCount += 1
                            return@forEachIndexed
                        }
                    when (installer.install(treeUri, modToInstall) { progress ->
                        operation.value = OperationState.Running(
                            "${index + 1}/${targets.size}: ${progress.message}",
                            downloadingModId = mod.id,
                            progress = progress.fraction,
                        )
                    }) {
                        is InstallResult.Installed -> successCount += 1
                        is InstallResult.Uninstalled -> Unit
                        is InstallResult.Failed -> failCount += 1
                    }
                }
                operation.value = if (failCount == 0) OperationState.Done("$successCount updates completed.")
                else OperationState.Error("$successCount updates completed; $failCount failed.")
                refreshManagedInstalls()
                refreshLocalMods()
                refreshValidation()
            } finally {
                finishModOperation()
            }
        }
    }

    fun uninstall(manifest: ManagedInstallManifest) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        if (!modOperationMutex.tryLock()) return
        val previousLocals = localMods.value
        val previousManaged = managedInstalls.value
        localMods.value = previousLocals.filterNot { it.folderName.equals(manifest.folderName, true) }
        managedInstalls.value = previousManaged.filterNot { it.folderName.equals(manifest.folderName, true) }
        viewModelScope.launch {
            try {
                operation.value = OperationState.Running("Removing ${manifest.title}")
                val resultState = when (val result = installer.uninstall(treeUri, manifest) { progress ->
                operation.value = OperationState.Running(progress.message, progress = progress.fraction)
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
                refreshManagedInstalls()
                refreshLocalMods()
                refreshValidation()
            } finally {
                finishModOperation()
            }
        }
    }

    fun removeLocalMod(folderName: String) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        if (!modOperationMutex.tryLock()) return
        val previousLocals = localMods.value
        localMods.value = previousLocals.filterNot { it.folderName.equals(folderName, true) }
        viewModelScope.launch {
            try {
                operation.value = OperationState.Running("Removing $folderName")
                val resultState = runCatchingCancellable { localModRepository.remove(treeUri, folderName) }
                .fold(
                    onSuccess = { OperationState.Done("$folderName removed.") },
                    onFailure = { OperationState.Error(it.message ?: "Could not remove $folderName.") },
                )
                operation.value = resultState
                if (resultState is OperationState.Error) localMods.value = previousLocals
                else operation.value = OperationState.Idle
                refreshLocalMods()
                refreshValidation()
            } finally {
                finishModOperation()
            }
        }
    }

    fun setLocalModEnabled(folderName: String, enabled: Boolean) {
        mutateLocalMods(listOf(folderName), enabled)
    }

    fun setLocalModsEnabled(folderNames: List<String>, enabled: Boolean) {
        mutateLocalMods(folderNames, enabled)
    }

    private fun mutateLocalMods(folderNames: List<String>, enabled: Boolean) {
        val treeUri = uiState.value.persistedTreeUri ?: return
        val targets = folderNames.distinctBy { it.lowercase() }
        if (targets.isEmpty()) return
        if (localModsMutationJob?.isActive != true && !modOperationMutex.tryLock()) return
        localMods.value = localMods.value.map {
            if (targets.any { name -> name.equals(it.folderName, true) }) it.copy(enabled = enabled) else it
        }
        localModsRevision += 1
        localModsRefreshJob?.cancel()
        targets.forEach {
            pendingEnabledStates[it.lowercase()] = PendingEnabledState(it, enabled)
        }
        operation.value = OperationState.Running(
            message = if (enabled) "Enabling ${targets.size} mods" else "Disabling ${targets.size} mods",
            isLocalToggle = true,
        )
        if (localModsMutationJob?.isActive == true) return
        localModsMutationJob = viewModelScope.launch {
            try {
                var mutationError: Throwable? = null
                while (pendingEnabledStates.isNotEmpty()) {
                    val batch = pendingEnabledStates.toMap()
                    val previousBatchStates = localMods.value
                        .filter { it.folderName.lowercase() in batch.keys }
                        .associateBy { it.folderName.lowercase() }
                    batch.forEach { (key, state) ->
                        if (pendingEnabledStates[key] == state) pendingEnabledStates.remove(key)
                    }
                    val batchFolderNames = batch.values.mapTo(linkedSetOf()) { it.folderName }
                    val refreshed = localModsIoMutex.withLock {
                        try {
                            batch.values.groupBy { it.enabled }.forEach { (desired, entries) ->
                                localModRepository.setEnabled(treeUri, entries.map { it.folderName }, desired)
                            }
                            localModRepository.list(treeUri, batchFolderNames)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Throwable) {
                            mutationError = error
                            runCatchingCancellable {
                                localModRepository.list(treeUri, batchFolderNames)
                            }.getOrNull()
                        }
                    }
                    if (refreshed != null) {
                        val batchKeys = batch.keys
                        localMods.value = (localMods.value
                            .filterNot { it.folderName.lowercase() in batchKeys } + refreshed)
                            .map { local ->
                                pendingEnabledStates[local.folderName.lowercase()]?.let {
                                    local.copy(enabled = it.enabled)
                                } ?: local
                            }
                            .sortedWith(compareBy<LocalModStatus> { !it.enabled }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.folderName })
                    } else if (mutationError != null) {
                        localMods.value = localMods.value.map { local ->
                            previousBatchStates[local.folderName.lowercase()] ?: local
                        }
                    }
                    if (mutationError != null) {
                        pendingEnabledStates.clear()
                        break
                    }
                }
                operation.value = mutationError?.let { error ->
                    OperationState.Error(error.message ?: "Could not change selected mods.")
                } ?: OperationState.Idle
            } finally {
                localModsMutationJob = null
                finishModOperation()
            }
        }
    }

    fun clearOperation() {
        if (operation.value !is OperationState.Running) operation.value = OperationState.Idle
    }

    private fun finishModOperation() {
        modOperationMutex.unlock()
        if (localModsRefreshRequested) {
            localModsRefreshRequested = false
            refreshLocalMods()
        }
    }

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
                runCatchingCancellable { catalogRepository.refreshSnapshot() }
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
        if (!modOperationMutex.tryLock()) return
        viewModelScope.launch {
            try {
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
            } finally {
                finishModOperation()
            }
        }
    }

    fun hydrateCatalogMod(modId: String) {
        val current = catalogMods.value.firstOrNull { it.id == modId } ?: return
        if (!hydratedModIds.add(modId)) return
        viewModelScope.launch {
            runCatchingCancellable { catalogRepository.hydrateMod(current) }
                .onSuccess(::upsertCatalogMod)
                .onFailure { hydratedModIds.remove(modId) }
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            catalogLoading.value = true
            val cached = runCatchingCancellable { catalogRepository.loadLocalSnapshot() }.getOrNull()
            if (cached != null && cached.mods.isNotEmpty()) {
                applyCatalogSnapshot(cached)
            }

            if (cached == null || cached.mods.isEmpty()) {
                runCatchingCancellable { catalogRepository.refreshSnapshot() }
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
                runCatchingCancellable { catalogRepository.refreshSnapshot() }
                    .onSuccess(::applyCatalogSnapshot)
            } finally {
                catalogRefreshing.value = false
            }
        }
    }

    private fun applyCatalogSnapshot(snapshot: com.balatromodmanager.catalog.CatalogSnapshot) {
        catalogMods.value = snapshot.mods
        catalogInfo.value = CatalogInfo(snapshot.generatedAt)
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

    class Factory(
        private val repository: GameTreeRepository,
        private val lmmBuildDetector: LmmBuildDetector,
        private val catalogRepository: CatalogRepository,
        private val manifestRepository: ManagedInstallRepository,
        private val localModRepository: LocalModRepository,
        private val visualSettingsRepository: VisualSettingsRepository,
        private val installer: ModInstaller,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, lmmBuildDetector, catalogRepository, manifestRepository, localModRepository, visualSettingsRepository, installer) as T
        }
    }
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
