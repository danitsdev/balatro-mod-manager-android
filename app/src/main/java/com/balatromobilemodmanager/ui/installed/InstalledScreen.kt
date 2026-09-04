package com.balatromobilemodmanager.ui.installed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.balatromobilemodmanager.MainUiState
import com.balatromobilemodmanager.OperationState
import com.balatromobilemodmanager.SectionTitle
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.domain.findCatalogForLocal
import com.balatromobilemodmanager.domain.hasUpdateFor
import com.balatromobilemodmanager.domain.simplifiedModKey
import com.balatromobilemodmanager.installer.LocalModStatus
import com.balatromobilemodmanager.settings.VisualSettings
import com.balatromobilemodmanager.storage.TreeValidation
import com.balatromobilemodmanager.ui.theme.BmmColor
import com.balatromobilemodmanager.ui.catalog.DesktopModCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledScreen(
    state: MainUiState,
    padding: PaddingValues,
    visualSettings: VisualSettings,
    onRefresh: () -> Unit,
    onOpenMod: (CatalogMod) -> Unit,
    onInstall: (CatalogMod) -> Unit,
    onInstallAll: (List<CatalogMod>) -> Unit,
    onUninstall: (ManagedInstallManifest) -> Unit,
    onRemoveLocalMod: (String) -> Unit,
    onSetLocalModEnabled: (String, Boolean) -> Unit,
    onSetLocalModsEnabled: (List<String>, Boolean) -> Unit,
) {
    val validation = state.validation as? TreeValidation.Valid ?: return
    val haptic = LocalHapticFeedback.current
    val localMods = state.localMods.filterNot { it.folderName.equals("lovely", ignoreCase = true) }
    var installedQuery by rememberSaveable { mutableStateOf("") }
    val visibleLocalMods = localMods.filter { it.matchesQuery(installedQuery) }
    val enabledMods = visibleLocalMods.filter { it.enabled }
    val disabledMods = visibleLocalMods.filterNot { it.enabled }
    val visibleFolderNames = visibleLocalMods.map { it.folderName }
    val visibleUpdateMods = visibleLocalMods.mapNotNull { local ->
        val manifest = state.managedInstalls.firstOrNull {
            it.folderName.equals(local.folderName, ignoreCase = true)
        } ?: return@mapNotNull null
        val catalog = state.catalogMods.findCatalogForLocal(local)
            ?: state.catalogMods.firstOrNull { it.id == manifest.modId }
        catalog?.takeIf { it.hasUpdateFor(manifest) }
    }.distinctBy { it.id }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            delay(700)
            isRefreshing = false
        }
    }

    val visibleCatalogMods = visibleLocalMods.map { local ->
        val manifest = state.managedInstalls.firstOrNull {
            it.folderName.equals(local.folderName, ignoreCase = true)
        }
        val catalogMatch = state.catalogMods.findCatalogForLocal(local)
            ?: state.catalogMods.firstOrNull { it.id == manifest?.modId }
        val catalog = catalogMatch ?: CatalogMod(
                id = local.declaredId.ifBlank { local.folderName },
                title = local.title.ifBlank { local.folderName },
                author = local.author.ifBlank { "Unknown" },
                categories = emptyList(),
                repo = "",
                downloadUrl = "",
                folderName = local.folderName,
                version = local.version,
                requiresSteamodded = false,
                requiresTalisman = false,
                automaticVersionCheck = false,
                lastUpdated = 0,
                downloadsTotal = 0,
                downloadsToday = 0,
                thumbnailUrl = "",
                summary = "Mod folder: ${local.folderName}",
                description = "No online description available."
            )
        InstalledCardItem(local, catalog, catalogMatch != null)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(visualSettings.cardMinWidthDp.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installed Mods", style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
                OutlinedTextField(
                    value = installedQuery,
                    onValueChange = { installedQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BmmColor.MutedCream) },
                    placeholder = { Text("Filter installed mods...", color = BmmColor.MutedCream) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BmmColor.Input,
                        unfocusedContainerColor = BmmColor.Input,
                        disabledContainerColor = BmmColor.Input,
                        focusedBorderColor = BmmColor.Gold,
                        unfocusedBorderColor = BmmColor.Neutral,
                        cursorColor = BmmColor.Gold,
                        focusedTextColor = BmmColor.Cream,
                        unfocusedTextColor = BmmColor.Cream,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSetLocalModsEnabled(visibleFolderNames, true)
                        },
                        enabled = disabledMods.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enable all")
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSetLocalModsEnabled(visibleFolderNames, false)
                        },
                        enabled = enabledMods.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.PanelRaised),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Disable all")
                    }
                }
                if (visibleUpdateMods.isNotEmpty()) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInstallAll(visibleUpdateMods)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green),
                    ) {
                        Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Update all (${visibleUpdateMods.size})")
                    }
                }
            }
        }

        val enabledCatalogMods = visibleCatalogMods.filter { it.local.enabled }
        val disabledCatalogMods = visibleCatalogMods.filterNot { it.local.enabled }

        if (enabledCatalogMods.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Enabled", enabledCatalogMods.size, isEnabled = true)
            }
            gridItems(enabledCatalogMods, key = { "enabled_" + it.local.folderName }) { item ->
                val local = item.local
                val catalog = item.catalog
                val manifest = state.managedInstalls.firstOrNull {
                    it.folderName.equals(local.folderName, ignoreCase = true)
                }
                val hasUpdate = manifest?.let { catalog.hasUpdateFor(it) } == true
                val operation = (state.operation as? OperationState.Running)
                    ?.takeIf { it.downloadingModId == catalog.id }

                DesktopModCard(
                    mod = catalog,
                    installed = true,
                    enabled = local.enabled,
                    hasUpdate = hasUpdate,
                    canGetOfficial = manifest == null && item.hasCatalogMatch,
                    operation = operation,
                    onOpen = { onOpenMod(catalog) },
                    onInstall = { onInstall(catalog) },
                    onToggleEnabled = { onSetLocalModEnabled(local.folderName, !local.enabled) },
                    onRemove = {
                        if (manifest != null) onUninstall(manifest) else onRemoveLocalMod(local.folderName)
                    }
                )
            }
        }

        if (disabledCatalogMods.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Disabled", disabledCatalogMods.size, isEnabled = false)
            }
            gridItems(disabledCatalogMods, key = { "disabled_" + it.local.folderName }) { item ->
                val local = item.local
                val catalog = item.catalog
                val manifest = state.managedInstalls.firstOrNull {
                    it.folderName.equals(local.folderName, ignoreCase = true)
                }
                val hasUpdate = manifest?.let { catalog.hasUpdateFor(it) } == true
                val operation = (state.operation as? OperationState.Running)
                    ?.takeIf { it.downloadingModId == catalog.id }

                DesktopModCard(
                    mod = catalog,
                    installed = true,
                    enabled = local.enabled,
                    hasUpdate = hasUpdate,
                    canGetOfficial = manifest == null && item.hasCatalogMatch,
                    operation = operation,
                    onOpen = { onOpenMod(catalog) },
                    onInstall = { onInstall(catalog) },
                    onToggleEnabled = { onSetLocalModEnabled(local.folderName, !local.enabled) },
                    onRemove = {
                        if (manifest != null) onUninstall(manifest) else onRemoveLocalMod(local.folderName)
                    }
                )
            }
        }

    }
    }
}

private data class InstalledCardItem(
    val local: LocalModStatus,
    val catalog: CatalogMod,
    val hasCatalogMatch: Boolean,
)

@Composable
private fun SectionHeader(title: String, count: Int, isEnabled: Boolean) {
    val bgColor = if (isEnabled) BmmColor.Green.copy(alpha = 0.12f) else BmmColor.PanelRaised.copy(alpha = 0.35f)
    val borderColor = if (isEnabled) BmmColor.Green.copy(alpha = 0.25f) else BmmColor.MutedCream.copy(alpha = 0.25f)
    val textColor = if (isEnabled) BmmColor.Green else BmmColor.MutedCream

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = textColor)
        Text("$count active", style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

private fun LocalModStatus.matchesQuery(query: String): Boolean {
    val normalized = query.simplifiedModKey()
    if (normalized.isBlank()) return true
    return listOf(folderName, title, author, version, declaredId)
        .any { it.simplifiedModKey().contains(normalized) }
}
