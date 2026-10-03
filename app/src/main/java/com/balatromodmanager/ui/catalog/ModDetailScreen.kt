package com.balatromodmanager.ui.catalog

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balatromodmanager.OperationState
import com.balatromodmanager.accentColor
import com.balatromodmanager.canonicalSteamoddedDependency
import com.balatromodmanager.isBundledLovelyDependency
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.CatalogVersion
import com.balatromodmanager.catalog.forVersion
import com.balatromodmanager.domain.ModPrimaryAction
import com.balatromodmanager.domain.resolveModPrimaryAction
import com.balatromodmanager.openUrl
import com.balatromodmanager.ui.theme.BmmColor
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import java.util.Locale

private enum class ModDetailTab(val label: String, val icon: ImageVector) {
    Details("Details", Icons.Filled.Info),
    Required("Dependencies", Icons.Filled.Link),
    Versions("Versions", Icons.Filled.History),
}

internal data class CategoryStyle(val icon: ImageVector, val color: Color)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ModDetailScreen(
    mod: CatalogMod,
    catalogMods: List<CatalogMod>,
    installed: Boolean,
    installedVersion: String?,
    canGetOfficial: Boolean,
    hasUpdate: Boolean,
    operation: OperationState.Running?,
    busy: Boolean,
    localToggleBusy: Boolean,
    enabled: Boolean?,
    isRefreshing: Boolean,
    padding: PaddingValues,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onInstall: (CatalogMod) -> Unit,
    onRefresh: (String) -> Unit,
    onHydrateVersion: (String) -> Unit,
    onOpenDependency: (CatalogMod) -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedVersionNumber by rememberSaveable(mod.id) { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable(mod.id) { mutableIntStateOf(0) }
    val versions = remember(mod.versions, mod.version, mod.description, mod.downloadUrl, mod.requiredPackages) {
        mod.versions.ifEmpty {
            listOf(
                CatalogVersion(
                    versionNumber = mod.version,
                    description = mod.description,
                    downloadUrl = mod.downloadUrl,
                    dependencies = mod.requiredPackages,
                ),
            )
        }
    }
    val selectedVersion = versions.firstOrNull { it.versionNumber == selectedVersionNumber } ?: versions.first()
    val selectedMod = mod.forVersion(selectedVersion)
    val currentHydrateVersion by rememberUpdatedState(onHydrateVersion)
    LaunchedEffect(mod.id, selectedVersion.versionNumber) {
        if (selectedVersion.versionNumber != mod.version) currentHydrateVersion(selectedVersion.versionNumber)
    }

    val action = resolveModPrimaryAction(installed, hasUpdate, canGetOfficial)
    val selectedVersionIsInstalled = installed && (
        installedVersion?.equals(selectedVersion.versionNumber, ignoreCase = true) == true ||
            (installedVersion.isNullOrBlank() && action == ModPrimaryAction.Installed && selectedVersion.versionNumber == mod.version)
        )
    val selectedVersionIsOlder = selectedVersion.versionNumber != mod.version
    val actionLabel = when {
        action == ModPrimaryAction.GetOfficial -> "Get official"
        selectedVersionIsInstalled -> "Installed"
        selectedVersionIsOlder -> "Install ${selectedVersion.versionNumber}"
        action == ModPrimaryAction.Update -> "Update"
        else -> "Install"
    }

    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = Modifier.fillMaxSize().padding(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        ),
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { onRefresh(selectedVersion.versionNumber) },
            modifier = Modifier.fillMaxSize().padding(
                start = padding.calculateLeftPadding(layoutDirection),
                end = padding.calculateRightPadding(layoutDirection),
            ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 4.dp,
                    end = 18.dp,
                    bottom = 78.dp,
                ),
            ) {
                item(key = "toolbar") {
                    ModDetailToolbar(
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBack()
                        },
                    )
                }
                item(key = "artwork") { ModHeroArtwork(mod) }
                item(key = "identity") { ModIdentity(mod) }
                item(key = "tabs") {
                    ModDetailTabs(
                        selectedIndex = selectedTabIndex,
                        onSelect = { selectedTabIndex = it },
                    )
                }

                when (ModDetailTab.entries[selectedTabIndex.coerceIn(0, ModDetailTab.entries.lastIndex)]) {
                    ModDetailTab.Details -> item(key = "details") {
                        ModDetailsContent(
                            mod = mod,
                            version = selectedVersion,
                            onProjectPage = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                context.openUrl(mod.repo.ifBlank { mod.downloadUrl })
                            },
                            onTagClick = { category ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTagClick(category)
                            },
                        )
                    }

                    ModDetailTab.Required -> {
                        val dependencies = selectedMod.requiredPackages.distinct()
                            .filterNot { it.isBundledLovelyDependency() }
                        if (dependencies.isEmpty()) {
                            item(key = "no-dependencies") {
                                EmptyDetailMessage("No dependencies.")
                            }
                        } else {
                            items(dependencies, key = { it }) { dependency ->
                                DependencyRow(
                                    identifier = dependency,
                                    catalogMods = catalogMods,
                                    onOpenMod = onOpenDependency,
                                )
                            }
                        }
                    }

                    ModDetailTab.Versions -> {
                        item(key = "versions-heading") {
                            DetailSectionHeading(title = "Versions", detail = null)
                        }
                        items(versions, key = { it.versionNumber }) { version ->
                            VersionRow(
                                version = version,
                                latestVersion = mod.version,
                                selected = version.versionNumber == selectedVersion.versionNumber,
                                installed = installedVersion?.equals(version.versionNumber, ignoreCase = true) == true,
                                onSelect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedVersionNumber = version.versionNumber
                                },
                            )
                        }
                    }
                }
            }
        }

        ModDetailBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            mod = mod,
            installed = installed,
            enabled = enabled,
            actionLabel = actionLabel,
            primaryActionAvailable = selectedMod.supportsAutomaticInstall,
            operation = operation,
            busy = busy,
            localToggleBusy = localToggleBusy,
            onInstall = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onInstall(selectedMod)
            },
            onToggleEnabled = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleEnabled()
            },
            onRemove = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
            },
        )
    }
}

@Composable
private fun ModDetailToolbar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BmmColor.Cream)
        }
    }
}

@Composable
private fun ModHeroArtwork(mod: CatalogMod) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = minOf(maxWidth * 0.72f, 248.dp)
        Box(
            modifier = Modifier.size(artworkSize)
                .clip(RoundedCornerShape(8.dp))
                .background(BmmColor.Panel)
                .border(2.dp, mod.accentColor().copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(5.dp),
        ) {
            ModStripedBackground(mod, Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)))
            ModThumbnail(mod, Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun ModIdentity(mod: CatalogMod) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            mod.title,
            style = MaterialTheme.typography.titleLarge,
            color = BmmColor.Cream,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "by ${mod.author.ifBlank { "Unknown" }}",
            color = BmmColor.Gold,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModDetailTabs(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(BmmColor.Panel, RoundedCornerShape(7.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ModDetailTab.entries.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(tab.icon, contentDescription = null, tint = if (selected) BmmColor.Gold else BmmColor.MutedCream, modifier = Modifier.size(16.dp))
                        Text(
                            tab.label,
                            color = if (selected) BmmColor.Gold else BmmColor.MutedCream,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth(0.62f).height(2.dp)
                            .background(if (selected) BmmColor.Gold else Color.Transparent, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModDetailsContent(
    mod: CatalogMod,
    version: CatalogVersion,
    onProjectPage: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (mod.repo.isNotBlank() || mod.downloadUrl.isNotBlank()) {
            ProjectPageButton(mod = mod, onClick = onProjectPage)
        }

        if (mod.categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                mod.categories.distinctBy { it.trim().lowercase() }.forEach { category ->
                    CategoryChip(category, onClick = { onTagClick(category) })
                }
            }
        }

        ModDescription(mod = mod, version = version)
    }
}

@Composable
private fun CategoryChip(category: String, onClick: () -> Unit) {
    val style = categoryStyle(category)
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.clip(shape)
            .background(style.color.copy(alpha = 0.16f))
            .border(1.dp, style.color.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(15.dp))
        Text(category, color = BmmColor.Cream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ProjectPageButton(mod: CatalogMod, onClick: () -> Unit) {
    val url = mod.repo.ifBlank { mod.downloadUrl }
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Language, contentDescription = "Open project link", tint = BmmColor.Accent, modifier = Modifier.size(18.dp))
        Text(url, color = BmmColor.Accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModDescription(mod: CatalogMod, version: CatalogVersion) {
    val source = remember(version.versionNumber, version.readme, version.description, mod.description, mod.summary) {
        version.readme.takeIf(String::isNotBlank)
            ?: version.description.ifBlank { mod.description.ifBlank { mod.summary } }
    }
    if (source.isBlank()) {
        if (!version.readmeLoaded) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BmmColor.Gold,
                trackColor = BmmColor.GlassBorder,
            )
        }
        return
    }
    val markdown = remember(source) {
        normalizeThunderstoreMarkdown(
            source,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!version.readmeLoaded) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BmmColor.Gold,
                trackColor = BmmColor.GlassBorder,
            )
        }

        Crossfade(
            targetState = markdown,
            animationSpec = tween(durationMillis = 180),
            label = "mod-description",
        ) { visibleMarkdown ->
            Markdown(
                content = visibleMarkdown,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                colors = markdownColor(
                    text = BmmColor.Cream,
                    codeText = BmmColor.Cream,
                    inlineCodeText = BmmColor.Cream,
                    linkText = BmmColor.Accent,
                    codeBackground = BmmColor.PanelRaised,
                    inlineCodeBackground = BmmColor.PanelRaised,
                    dividerColor = BmmColor.GlassBorder,
                    tableText = BmmColor.Cream,
                    tableBackground = BmmColor.Panel,
                ),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.headlineSmall,
                    h2 = MaterialTheme.typography.titleLarge,
                    h3 = MaterialTheme.typography.titleMedium,
                    h4 = MaterialTheme.typography.titleSmall,
                    h5 = MaterialTheme.typography.titleSmall,
                    h6 = MaterialTheme.typography.titleSmall,
                    text = MaterialTheme.typography.bodyMedium,
                    code = MaterialTheme.typography.bodySmall,
                    inlineCode = MaterialTheme.typography.bodySmall,
                    quote = MaterialTheme.typography.bodyMedium,
                    paragraph = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                    ordered = MaterialTheme.typography.bodyMedium,
                    bullet = MaterialTheme.typography.bodyMedium,
                    list = MaterialTheme.typography.bodyMedium,
                    link = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                ),
                imageTransformer = Coil2ImageTransformerImpl,
                padding = markdownPadding(block = 8.dp, list = 8.dp, listItemBottom = 4.dp),
            )
        }
    }
}

@Composable
private fun DependencyRow(
    identifier: String,
    catalogMods: List<CatalogMod>,
    onOpenMod: (CatalogMod) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dependency = remember(identifier, catalogMods) { describeDependency(identifier, catalogMods) }
    val shape = RoundedCornerShape(4.dp)

    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(BmmColor.Panel)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dependency.mod?.let(onOpenMod)
                    ?: context.openUrl("https://thunderstore.io/c/balatro/?q=${Uri.encode(dependency.identifier)}")
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(5.dp))
                .background(BmmColor.PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (dependency.mod != null) {
                ModThumbnail(dependency.mod, Modifier.fillMaxSize())
            } else {
                val style = categoryStyle(dependency.packageName)
                Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(23.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                dependency.displayName,
                color = BmmColor.Cream,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (dependency.mod != null) {
                        append(dependency.namespace)
                        if (dependency.version.isNotBlank()) append(" · ").append(dependency.version)
                    } else {
                        append("Thunderstore")
                    }
                },
                color = BmmColor.MutedCream,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open dependency", tint = BmmColor.Gold, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun VersionRow(
    version: CatalogVersion,
    latestVersion: String,
    selected: Boolean,
    installed: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) BmmColor.PanelRaised else BmmColor.Panel)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(38.dp)
                .background(if (selected) BmmColor.Gold else Color.Transparent, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(version.versionNumber, color = BmmColor.Cream, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (version.versionNumber == latestVersion) VersionBadge("Latest", BmmColor.Green)
                if (installed) VersionBadge("Installed", BmmColor.Gold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Date", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(version.dateCreated.take(10).ifBlank { "—" }, color = BmmColor.Cream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Downloads", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(formatCount(version.downloads), color = BmmColor.Cream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Size", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(if (version.fileSize > 0) formatFileSize(version.fileSize) else "—", color = BmmColor.Cream, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ModDetailBottomBar(
    modifier: Modifier,
    mod: CatalogMod,
    installed: Boolean,
    enabled: Boolean?,
    actionLabel: String,
    primaryActionAvailable: Boolean,
    operation: OperationState.Running?,
    busy: Boolean,
    localToggleBusy: Boolean,
    onInstall: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BmmColor.PanelOpaque,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enabled != null) {
                    Button(
                        onClick = onToggleEnabled,
                        enabled = !busy || localToggleBusy,
                        modifier = Modifier.width(40.dp).height(40.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (enabled) BmmColor.Green else BmmColor.Neutral,
                            contentColor = Color.White,
                            disabledContainerColor = BmmColor.Neutral.copy(alpha = 0.7f),
                        ),
                    ) {
                        Text(if (enabled) "ON" else "OFF", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                Button(
                    onClick = onInstall,
                    enabled = !busy && operation == null && primaryActionAvailable && actionLabel != "Installed",
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = if (operation != null) PaddingValues(0.dp) else PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BmmColor.Green,
                        contentColor = Color.White,
                        disabledContainerColor = if (operation != null) BmmColor.Green.copy(alpha = 0.8f) else BmmColor.Neutral,
                        disabledContentColor = Color.White.copy(alpha = 0.82f),
                    ),
                ) {
                    if (operation != null) {
                        val progress = operation.progress?.coerceIn(0f, 1f)?.takeIf { it > 0f }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                operation.message,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (progress == null) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                )
                            }
                        }
                    } else {
                        if (actionLabel != "Installed" && primaryActionAvailable) {
                            Icon(
                                if (actionLabel == "Update") Icons.Filled.SystemUpdateAlt else Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            when {
                                actionLabel == "Installed" -> "Installed"
                                primaryActionAvailable -> actionLabel
                                else -> "Unavailable"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (installed) {
                    Button(
                        onClick = onRemove,
                        enabled = !busy,
                        modifier = Modifier.width(36.dp).height(40.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Danger, contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${mod.title}", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionHeading(title: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = BmmColor.Gold, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun VersionBadge(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Composable
private fun EmptyDetailMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().background(BmmColor.Panel, RoundedCornerShape(7.dp)).padding(15.dp),
        color = BmmColor.MutedCream,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private data class DependencyDescription(
    val identifier: String,
    val displayName: String,
    val namespace: String,
    val packageName: String,
    val version: String,
    val mod: CatalogMod?,
)

private fun describeDependency(identifier: String, catalogMods: List<CatalogMod>): DependencyDescription {
    val canonicalIdentifier = identifier.canonicalSteamoddedDependency(catalogMods)
    val matching = catalogMods.mapNotNull { mod ->
        val identity = mod.id.split('@', limit = 2)
        if (identity.size != 2) return@mapNotNull null
        val prefix = "${identity[0]}-${identity[1]}-"
        if (canonicalIdentifier.startsWith(prefix, ignoreCase = true)) prefix to mod else null
    }.maxByOrNull { (prefix, _) -> prefix.length }
    matching?.let { (prefix, mod) ->
        return DependencyDescription(
            identifier = canonicalIdentifier,
            displayName = mod.title,
            namespace = mod.author,
            packageName = mod.title,
            version = canonicalIdentifier.substring(prefix.length),
            mod = mod,
        )
    }

    val versionMatch = Regex("^(.+)-((?:v)?\\d[^\\s]*)$").find(canonicalIdentifier)
    val packageKey = versionMatch?.groupValues?.get(1) ?: canonicalIdentifier
    val namespace = packageKey.substringBefore('-', "")
    val packageName = packageKey.substringAfter('-', packageKey)
    return DependencyDescription(
        identifier = canonicalIdentifier,
        displayName = if (canonicalIdentifier != identifier) packageName else identifier,
        namespace = namespace,
        packageName = packageName,
        version = versionMatch?.groupValues?.get(2).orEmpty(),
        mod = null,
    )
}

private fun categoryStyle(category: String): CategoryStyle = when (category.trim().lowercase()) {
    "ai generated", "ai-generated" -> CategoryStyle(Icons.Filled.AutoAwesome, Color(0xFFC494FF))
    "audio" -> CategoryStyle(Icons.Filled.MusicNote, Color(0xFFFF80AB))
    "content mods", "content" -> CategoryStyle(Icons.Filled.Casino, Color(0xFF7FD69A))
    "gameplay tweaks", "gameplay" -> CategoryStyle(Icons.Filled.Tune, Color(0xFF83B8FF))
    "libraries", "library" -> CategoryStyle(Icons.Filled.Code, Color(0xFF76D8D0))
    "new mechanics", "mechanics" -> CategoryStyle(Icons.Filled.Psychology, Color(0xFFFFD36E))
    "misc", "miscellaneous" -> CategoryStyle(Icons.Filled.Extension, BmmColor.MutedCream)
    "modpacks", "mod pack" -> CategoryStyle(Icons.Filled.Inventory2, Color(0xFFFC9B64))
    "mods", "mod" -> CategoryStyle(Icons.Filled.Extension, BmmColor.Gold)
    "quality of life", "quality-of-life" -> CategoryStyle(Icons.Filled.Favorite, Color(0xFF7FD69A))
    "text changes", "text" -> CategoryStyle(Icons.Filled.TextFields, Color(0xFFFFB86C))
    "tools", "tool" -> CategoryStyle(Icons.Filled.Build, Color(0xFF83B8FF))
    "translation", "translations" -> CategoryStyle(Icons.Filled.Translate, Color(0xFF76D8D0))
    else -> CategoryStyle(Icons.Filled.Extension, BmmColor.Purple)
}

private fun normalizeThunderstoreMarkdown(markdown: String): String {
    val protectedCode = mutableListOf<String>()
    var content = markdown.replace("\r\n", "\n").replace('\r', '\n')
    content = content.replace(Regex("(?m)^ {0,3}(`{3,}|~{3,})[^\\n]*\\n[\\s\\S]*?^ {0,3}\\1[ \\t]*$")) { match ->
        protectedCode += match.value
        "@@BMM_CODE_${protectedCode.lastIndex}@@"
    }
    content = content.replace(Regex("`+[^`\\n]+`+")) { match ->
        protectedCode += match.value
        "@@BMM_CODE_${protectedCode.lastIndex}@@"
    }

    content = content
        .replace(Regex("(?s)<!--.*?-->"), "")
        .replace(Regex("(?is)<pre\\b[^>]*>(.*?)</pre>")) { match ->
            val code = match.groupValues[1]
                .replace(Regex("(?i)</?code\\b[^>]*>"), "")
                .replace(Regex("<[^>]+>"), "")
                .trim('\n', ' ', '\t')
            "\n```\n$code\n```\n"
        }
        .replace(Regex("(?i)<code\\b[^>]*>"), "`")
        .replace(Regex("(?i)</code\\s*>"), "`")
        .replace(Regex("(?is)<img\\b([^>]*)/?>")) { match ->
            val attributes = match.groupValues[1]
            val src = htmlAttribute(attributes, "src")
            val alt = htmlAttribute(attributes, "alt").ifBlank { "Image" }
            if (src.isBlank()) alt else "![$alt]($src)"
        }
        .replace(Regex("(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a>")) { match ->
            val label = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim().ifBlank { match.groupValues[1] }
            "[$label](${match.groupValues[1]})"
        }
        .replace(Regex("(?is)<summary\\b[^>]*>(.*?)</summary>")) { match ->
            "\n### ${match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()}\n"
        }
        .replace(Regex("(?is)<h([1-6])\\b[^>]*>(.*?)</h[1-6]>")) { match ->
            "\n${"#".repeat(match.groupValues[1].toInt())} ${match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()}\n"
        }
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<hr\\s*/?>"), "\n---\n")
        .replace(Regex("(?i)<li\\b[^>]*>"), "\n- ")
        .replace(Regex("(?i)</li\\s*>"), "\n")
        .replace(Regex("(?i)<tr\\b[^>]*>"), "\n")
        .replace(Regex("(?i)</tr\\s*>"), "\n")
        .replace(Regex("(?i)<(td|th)\\b[^>]*>"), "")
        .replace(Regex("(?i)</(td|th)\\s*>"), " | ")
        .replace(Regex("(?i)</(p|div|section|article|main|center|figure|figcaption|ul|ol|details)\\s*>"), "\n\n")
        .replace(Regex("(?i)<(p|div|section|article|main|center|figure|figcaption|ul|ol|details)\\b[^>]*>"), "\n")
        .replace(Regex("(?i)</?(span|strong|em|b|i|u|small|sub|sup|thead|tbody|table)\\b[^>]*>"), " ")
        .replace(Regex("!\\[[^\\]]*\\]\\([^\\n)]*\\)")) { match ->
            "\n\n${match.value}\n\n"
        }
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    return content.replace(Regex("@@BMM_CODE_(\\d+)@@")) { match ->
        protectedCode[match.groupValues[1].toInt()]
    }
}

private fun htmlAttribute(attributes: String, name: String): String {
    val match = Regex("(?i)\\b${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").find(attributes)
        ?: return ""
    return match.groupValues.drop(1).firstOrNull(String::isNotBlank).orEmpty()
}

private fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value).replace(',', '.')

private fun formatFileSize(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1f MB", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.0f KB", value / 1_000.0)
    else -> "$value B"
}
