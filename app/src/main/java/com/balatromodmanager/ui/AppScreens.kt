package com.balatromodmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.border
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedButton
import com.balatromodmanager.DetectedGameBuild
import com.balatromodmanager.DependencyStatus
import com.balatromodmanager.MainUiState
import com.balatromodmanager.findDependencyMod
import com.balatromodmanager.missingDependencies
import com.balatromodmanager.openModsFolder
import com.balatromodmanager.readablePath
import com.balatromodmanager.shortSyncStamp
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.settings.VisualSettings
import com.balatromodmanager.storage.TreeValidation
import com.balatromodmanager.ui.theme.BmmColor

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Card size", color = BmmColor.Cream)
                Text("${(visualSettings.cardScale * 100).toInt()}%", color = BmmColor.Gold)
            }
            Slider(
                value = visualSettings.cardScale,
                onValueChange = { value -> onVisualSettingsChange(visualSettings.copy(cardScale = value)) },
                valueRange = 0.75f..1.4f,
                steps = 12,
                colors = SliderDefaults.colors(
                    thumbColor = BmmColor.Gold,
                    activeTrackColor = BmmColor.Gold,
                    inactiveTrackColor = BmmColor.Neutral,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(BmmColor.Cream, RoundedCornerShape(1.dp))
                            .border(1.5.dp, BmmColor.Gold, RoundedCornerShape(1.dp))
                    )
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            Text("Smaller cards fit more per row.", color = BmmColor.MutedCream, style = MaterialTheme.typography.bodyMedium)
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
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPickFolder()
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Change", color = Color.White)
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
        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenLicenses()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, BmmColor.Cream),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = BmmColor.PanelOpaque,
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
            Text("Game Folder Access", style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
            Text("Connect the Balatro game folder used by Lovely Mobile Maker.", color = BmmColor.Cream)
            RequirementRow("1", "Open your LMM Balatro build once.")
            RequirementRow("2", "Grant access to the folder that contains ASET.")
            RequirementRow("3", "The manager validates ASET/Mods before making changes.")
        }
        validation?.let { ValidationCard(it) }
        if (detectedBuilds.isNotEmpty()) { DetectedBuildsCard(detectedBuilds, onPickDetectedBuild) }
        else {
            PixelPanel(borderColor = BmmColor.Amber, containerColor = Color(0xFF3A2E1B)) {
                Text("Game folder not detected", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
                Text("Choose the LMM game folder that contains ASET.", color = BmmColor.Cream)
            }
        }
        Button(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPickFolder()
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green, contentColor = BmmColor.Cream), shape = RoundedCornerShape(4.dp)) {
            Icon(Icons.Filled.Folder, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Choose Game Folder")
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
    val missing = mod.missingDependencies(dependencies)
    val haptic = LocalHapticFeedback.current
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
                borderColor = BmmColor.Cream,
                containerColor = BmmColor.PanelRaised,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = BmmColor.Gold, modifier = Modifier.size(26.dp))
                    Text("Required Dependencies", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
                }
                Text(
                    "${mod.title} requires the following missing dependencies:",
                    color = BmmColor.Cream,
                    style = MaterialTheme.typography.bodyLarge,
                )
                missing.forEach { dependency ->
                    val dependencyMod = catalogMods.findDependencyMod(dependency)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = dependencyMod != null) {
                                if (dependencyMod != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenDependency(dependencyMod)
                                }
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            dependency,
                            color = if (dependencyMod != null) BmmColor.Gold else BmmColor.MutedCream,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (dependencyMod != null) TextDecoration.Underline else TextDecoration.None,
                        )
                        Text(dependencyDescription(dependency), color = BmmColor.MutedCream, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Installing these first is recommended.", color = BmmColor.MutedCream)
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
                    ) { Text("Download Anyway", maxLines = 1) }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Danger, contentColor = Color.White),
                    ) { Text("Close") }
                }
            }
        }
    }
}

private fun dependencyDescription(name: String): String = when {
    name.equals("Steamodded", ignoreCase = true) -> "Core modding framework"
    name.equals("Amulet", ignoreCase = true) -> "Large-number API replacing Talisman"
    else -> "Required mod"
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
