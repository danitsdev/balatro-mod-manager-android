package com.balatromobilemodmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.CatalogSortMode
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.settings.VisualSettings
import com.balatromobilemodmanager.storage.AndroidGameTreeRepository
import com.balatromobilemodmanager.storage.TreeValidation
import com.balatromobilemodmanager.ui.background.BalatroShaderBackground
import com.balatromobilemodmanager.ui.installed.InstalledScreen
import com.balatromobilemodmanager.ui.theme.BalatroManagerTheme
import com.balatromobilemodmanager.ui.theme.BmmColor
import com.balatromobilemodmanager.catalog.CatalogRepository
import com.balatromobilemodmanager.installer.LocalModRepository
import com.balatromobilemodmanager.installer.ManagedInstallRepository
import com.balatromobilemodmanager.installer.ModInstaller
import com.balatromobilemodmanager.installer.OperationJournalRepository
import com.balatromobilemodmanager.settings.VisualSettingsRepository

internal val android.content.Context.settingsDataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = AndroidGameTreeRepository(applicationContext, applicationContext.settingsDataStore)
        val manifestRepository = ManagedInstallRepository(applicationContext)
        val operationJournalRepository = OperationJournalRepository(applicationContext)
        val localModRepository = LocalModRepository(applicationContext, manifestRepository)
        val visualSettingsRepository = VisualSettingsRepository(applicationContext.settingsDataStore)

        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = MainViewModel.Factory(
                    repository = repository,
                    lmmBuildDetector = LmmBuildDetector(applicationContext),
                    catalogRepository = CatalogRepository(applicationContext),
                    manifestRepository = manifestRepository,
                    localModRepository = localModRepository,
                    operationJournalRepository = operationJournalRepository,
                    visualSettingsRepository = visualSettingsRepository,
                    installer = ModInstaller(applicationContext, manifestRepository),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BalatroManagerTheme(darkMode = uiState.visualSettings.darkMode) { AppScreen(viewModel, uiState) }
        }
    }
}

private enum class AppDestination(val label: String, val icon: ImageVector) {
    Catalog("Catalog", Icons.Filled.Extension),
    Installed("Installed", Icons.Filled.Inventory2),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppScreen(viewModel: MainViewModel, uiState: MainUiState) {
    val visualSettings = uiState.visualSettings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.attachTree(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    LaunchedEffect(Unit) { viewModel.detectLmmBuilds(); viewModel.refreshValidation() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshCatalogIfStale() }
    LaunchedEffect(uiState.persistedTreeUri, uiState.validation) {
        if (uiState.validation is TreeValidation.Valid) viewModel.refreshLocalMods()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BmmColor.FeltRed) {
        BalatroShaderBackground(
            enabled = visualSettings.animatedBackground,
            darkMode = visualSettings.darkMode,
            modifier = Modifier.fillMaxSize(),
        )
        when {
            uiState.isLoading -> LoadingScreen()
            uiState.validation is TreeValidation.Valid -> ConnectedShell(
                state = uiState,
                onPickFolder = { picker.launch(null) },
                onPickDetectedBuild = { picker.launch(it.initialUri) },
                onRefresh = {
                    viewModel.refreshValidation()
                    viewModel.refreshLocalMods()
                },
                onValidateModDatabase = viewModel::validateModDatabase,
                onSetLocalModEnabled = viewModel::setLocalModEnabled,
                onSetLocalModsEnabled = viewModel::setLocalModsEnabled,
                onRefreshCatalog = viewModel::refreshCatalog,
                onEnsureCatalog = viewModel::ensureCatalogAvailable,
                onClearCatalogCache = viewModel::clearCatalogCache,
                onHydrateCatalogMod = viewModel::hydrateCatalogMod,
                onQueryChange = viewModel::setQuery,
                onCategoryChange = viewModel::setCategory,
                onSortChange = viewModel::setSortMode,
                visualSettings = visualSettings,
                onVisualSettingsChange = viewModel::setVisualSettings,
                onInstall = viewModel::install,
                onInstallAll = viewModel::installAll,
                onUninstall = viewModel::uninstall,
                onRemoveLocalMod = viewModel::removeLocalMod,
                onClearOperation = viewModel::clearOperation,
            )
            else -> OnboardingScreen(
                validation = uiState.validation,
                detectedBuilds = uiState.detectedBuilds,
                onPickFolder = { picker.launch(null) },
                onPickDetectedBuild = { picker.launch(it.initialUri) },
            )
        }
    }
}

@Composable
private fun ConnectedShell(
    state: MainUiState,
    onPickFolder: () -> Unit,
    onPickDetectedBuild: (DetectedGameBuild) -> Unit,
    onRefresh: () -> Unit,
    onValidateModDatabase: () -> Unit,
    onSetLocalModEnabled: (String, Boolean) -> Unit,
    onSetLocalModsEnabled: (List<String>, Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
    onEnsureCatalog: () -> Unit,
    onClearCatalogCache: () -> Unit,
    onHydrateCatalogMod: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSortChange: (CatalogSortMode) -> Unit,
    visualSettings: VisualSettings,
    onVisualSettingsChange: (VisualSettings) -> Unit,
    onInstall: (CatalogMod) -> Unit,
    onInstallAll: (List<CatalogMod>) -> Unit,
    onUninstall: (ManagedInstallManifest) -> Unit,
    onRemoveLocalMod: (String) -> Unit,
    onClearOperation: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(AppDestination.Catalog.name) }
    var dependencyPrompt by remember { mutableStateOf<CatalogMod?>(null) }
    var selectedModId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = AppDestination.valueOf(selected)

    LaunchedEffect(destination) {
        if (destination == AppDestination.Catalog) onEnsureCatalog()
    }

    // Back handler: mod detail → catalog list, non-catalog tab → catalog tab, catalog → allow exit
    BackHandler(enabled = selectedModId != null || destination != AppDestination.Catalog) {
        when {
            selectedModId != null -> selectedModId = null
            destination != AppDestination.Catalog -> selected = AppDestination.Catalog.name
            else -> { /* already on catalog with no detail — let system handle (exit app) */ }
        }
    }
    val validation = state.validation as TreeValidation.Valid
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.operation) {
        val error = state.operation as? OperationState.Error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error.message.redactPrivatePaths())
        onClearOperation()
    }
    val dependencies = remember(validation.modFolderNames, state.managedInstalls) {
        DependencyStatus(
            steamoddedInstalled = validation.hasSteamoddedFolder(),
            amuletInstalled = validation.hasAmuletCompatibleFolder(),
        )
    }

    Scaffold(
        modifier = Modifier.padding(compactSystemBarPadding(includeBottom = false)),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val haptic = LocalHapticFeedback.current
            NavigationBar(
                containerColor = BmmColor.PanelOpaque,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars,
            ) {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = item.name
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BmmColor.Gold,
                            selectedTextColor = BmmColor.Gold,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = BmmColor.MutedCream,
                            unselectedTextColor = BmmColor.MutedCream,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            AppDestination.Catalog -> CatalogScreen(
                state = state, padding = padding,
                onQueryChange = onQueryChange, onCategoryChange = onCategoryChange, onSortChange = onSortChange,
                onRefreshCatalog = onRefreshCatalog,
                onHydrateCatalogMod = onHydrateCatalogMod, visualSettings = visualSettings,
                selectedMod = selectedModId?.let { id -> state.catalogMods.firstOrNull { it.id == id } },
                onOpenMod = { selectedModId = it.id }, onBackFromMod = { selectedModId = null },
                onInstall = { mod -> if (mod.missingDependencies(dependencies).isEmpty()) onInstall(mod) else dependencyPrompt = mod },
                onSetLocalModEnabled = onSetLocalModEnabled,
                onUninstall = onUninstall, onRemoveLocalMod = onRemoveLocalMod, onClearOperation = onClearOperation,
            )
            AppDestination.Installed -> InstalledScreen(
                state = state, padding = padding, visualSettings = visualSettings, onRefresh = onRefresh,
                onOpenMod = { selectedModId = it.id; selected = AppDestination.Catalog.name },
                onInstall = { mod -> if (mod.missingDependencies(dependencies).isEmpty()) onInstall(mod) else dependencyPrompt = mod },
                onInstallAll = onInstallAll, onUninstall = onUninstall, onRemoveLocalMod = onRemoveLocalMod,
                onSetLocalModEnabled = onSetLocalModEnabled, onSetLocalModsEnabled = onSetLocalModsEnabled,
            )
            AppDestination.Settings -> SettingsScreen(
                state = state, padding = padding, onPickFolder = onPickFolder,
                onValidateModDatabase = onValidateModDatabase,
                onClearCatalogCache = onClearCatalogCache,
                visualSettings = visualSettings, onVisualSettingsChange = onVisualSettingsChange,
            )
        }
    }

    dependencyPrompt?.let { mod ->
        DependencySheet(
            mod = mod,
            catalogMods = state.catalogMods,
            dependencies = dependencies,
            onInstall = onInstall,
            onOpenDependency = { dependency ->
                selectedModId = dependency.id
                selected = AppDestination.Catalog.name
                dependencyPrompt = null
            },
            onDismiss = { dependencyPrompt = null },
        )
    }
}
