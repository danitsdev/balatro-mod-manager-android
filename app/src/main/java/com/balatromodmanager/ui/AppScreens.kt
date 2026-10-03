package com.balatromodmanager.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.balatromodmanager.DetectedGameBuild
import com.balatromodmanager.AppUpdateNotice
import com.balatromodmanager.DependencyStatus
import com.balatromodmanager.MainUiState
import com.balatromodmanager.resolveDependencies
import com.balatromodmanager.openModsFolder
import com.balatromodmanager.openUrl
import com.balatromodmanager.readablePath
import com.balatromodmanager.shortSyncStamp
import com.balatromodmanager.isBundledLovelyDependency
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.settings.VisualSettings
import com.balatromodmanager.settings.CatalogCardSize
import com.balatromodmanager.storage.TreeValidation
import com.balatromodmanager.ui.theme.BmmColor

@Composable
internal fun AppUpdateBanner(
    notice: AppUpdateNotice,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BmmColor.PanelOpaque,
        contentColor = BmmColor.Cream,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("APP UPDATE", style = MaterialTheme.typography.labelSmall, color = BmmColor.Gold)
                Text(
                    "${notice.tag} is available",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onOpenRelease,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = Color.White),
            ) {
                Text("Release", maxLines = 1)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss update", tint = BmmColor.MutedCream)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: MainUiState,
    padding: PaddingValues,
    onPickFolder: () -> Unit,
    onValidateModDatabase: () -> Unit,
    onClearCatalogCache: () -> Unit,
    onOpenLicenses: () -> Unit,
    visualSettings: VisualSettings,
    onVisualSettingsChange: (VisualSettings) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val modsPath = state.persistedTreeUri?.toString()?.readablePath()?.let { root ->
        if (root.endsWith("ASET", ignoreCase = true)) "$root/Mods" else "$root/ASET/Mods"
    } ?: "Not selected"
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
        PixelPanel {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", color = BmmColor.Cream)
                SquareSwitch(checked = visualSettings.darkMode, onCheckedChange = { enabled -> onVisualSettingsChange(visualSettings.copy(darkMode = enabled)) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Animated background", color = BmmColor.Cream)
                SquareSwitch(checked = visualSettings.animatedBackground, onCheckedChange = { enabled -> onVisualSettingsChange(visualSettings.copy(animatedBackground = enabled)) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogCardSize.entries.forEach { size ->
                    val selected = visualSettings.cardSize == size
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVisualSettingsChange(visualSettings.copy(cardSize = size))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) BmmColor.Gold.copy(alpha = 0.22f) else BmmColor.PanelRaised,
                            contentColor = if (selected) BmmColor.Gold else BmmColor.Cream,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(size.name.lowercase().replaceFirstChar(Char::uppercase))
                            Text(size.description, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        PixelPanel {
            Text("Mods", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Directory:", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modsPath,
                        color = BmmColor.Cream,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPickFolder()
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.PanelRaised, contentColor = BmmColor.Cream),
                    ) {
                        Text("Change")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.openModsFolder(state.persistedTreeUri?.toString().orEmpty())
                    },
                    enabled = state.persistedTreeUri != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Neutral, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open Mods Folder")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onValidateModDatabase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Validate Mod Database")
                }
            }
        }
        PixelPanel {
            Text("Catalog", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
            Text(
                if (state.catalogInfo.generatedAt.isBlank()) "Catalog cache is empty"
                else "Synced ${state.catalogInfo.generatedAt.shortSyncStamp()}",
                color = BmmColor.MutedCream,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClearCatalogCache()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Neutral, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Cache")
                }
            }
        }
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenLicenses()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BmmColor.PanelRaised,
                contentColor = BmmColor.Cream,
            ),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Licenses & credits", modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
internal fun OnboardingScreen(
    validation: TreeValidation?,
    detectedBuilds: List<DetectedGameBuild>,
    onPickFolder: () -> Unit,
    onPickDetectedBuild: (DetectedGameBuild) -> Unit,
    onOpenLmm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(compactSystemBarPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroHeader()
        PixelPanel {
            Text("Set up your game folder", style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
            Text("This manager needs a Balatro Android build made with Lovely Mobile Maker (LMM).", color = BmmColor.Cream)
            RequirementRow("1", "Create and install your Android build with LMM.")
            RequirementRow("2", "Open the game at least once. The first launch can be unstable; this step creates ASET/Mods.")
            RequirementRow("3", "Choose the parent folder that contains ASET, then confirm Use this folder.")
            Text("After access is granted, the manager checks ASET/Mods and handles mod installation and management.", color = BmmColor.MutedCream)
            Button(
                onClick = onOpenLmm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = BmmColor.Cream),
            ) { Text("Open Lovely Mobile Maker") }
        }
        validation?.let { ValidationCard(it) }
        if (detectedBuilds.isNotEmpty()) { DetectedBuildsCard(detectedBuilds, onPickDetectedBuild) }
        else {
            PixelPanel(borderColor = BmmColor.Amber, containerColor = Color(0xFF3A2E1B)) {
                Text("Game folder not detected", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
                Text("Choose the LMM parent folder with ASET inside.", color = BmmColor.Cream)
            }
        }
        Button(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPickFolder()
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = BmmColor.Cream), shape = RoundedCornerShape(4.dp)) {
            Icon(Icons.Filled.Folder, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Select Folder with ASET")
        }
    }
}

@Composable
internal fun DependencySheet(
    mod: CatalogMod, catalogMods: List<CatalogMod>, dependencies: DependencyStatus,
    onInstall: (CatalogMod) -> Unit,
    onOpenDependency: (CatalogMod) -> Unit,
    onDismiss: () -> Unit,
) {
    val requirements = mod.resolveDependencies(catalogMods, dependencies)
        .filterNot { it.identifier.isBundledLovelyDependency() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelPanel(
                modifier = Modifier.widthIn(max = 420.dp),
                containerColor = BmmColor.PanelRaised,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = BmmColor.Gold, modifier = Modifier.size(26.dp))
                    Text("Requirements for ${mod.title}", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold, maxLines = 2)
                }
                requirements.forEach { dependency ->
                    val dependencyMod = dependency.catalogMod
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (dependencyMod != null) onOpenDependency(dependencyMod)
                                else context.openUrl("https://thunderstore.io/c/balatro/?q=${Uri.encode(dependency.identifier)}")
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            dependency.title,
                            color = BmmColor.Gold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            buildString {
                                append("Required: ").append(dependency.version.ifBlank { "version not listed" })
                                append("  ·  ")
                                append(
                                    when {
                                        dependency.installed -> "Installed"
                                        dependencyMod != null -> "Available in catalog (${dependencyMod.version})"
                                        else -> "Not in catalog"
                                    },
                                )
                            },
                            color = if (dependency.installed) BmmColor.Green else BmmColor.MutedCream,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInstall(mod)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Blue, contentColor = Color.White),
                    ) { Text("Install anyway", maxLines = 1) }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Danger, contentColor = Color.White),
                    ) { Text("Back") }
                }
            }
        }
    }
}

@Composable
internal fun DetectedBuildsCard(builds: List<DetectedGameBuild>, onPickDetectedBuild: (DetectedGameBuild) -> Unit) {
    val haptic = LocalHapticFeedback.current
    PixelPanel {
        Text("Detected game folders", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
        builds.forEach { build ->
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPickDetectedBuild(build)
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = Color.White)) {
                Icon(Icons.Filled.Folder, contentDescription = null); Spacer(Modifier.width(8.dp))
                Text(build.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RequirementRow(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        StatusPill(number, BmmColor.Blue); Text(text, color = BmmColor.Cream, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ValidationCard(validation: TreeValidation) {
    val isValid = validation is TreeValidation.Valid
    val message = when (validation) {
        is TreeValidation.Invalid -> validation.message
        is TreeValidation.Valid -> "ASET/Mods is ready."
    }
    PixelPanel(borderColor = if (isValid) BmmColor.Green else BmmColor.Danger, containerColor = if (isValid) BmmColor.Panel else Color(0xFF4A232A)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = if (isValid) BmmColor.Green else BmmColor.Gold)
            Text(message, color = BmmColor.Cream, modifier = Modifier.weight(1f))
        }
    }
}
