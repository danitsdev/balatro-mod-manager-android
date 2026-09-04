package com.balatromobilemodmanager.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.balatromobilemodmanager.ModThumbnail
import com.balatromobilemodmanager.ModStripedBackground
import com.balatromobilemodmanager.OperationState
import com.balatromobilemodmanager.R
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.domain.ModPrimaryAction
import com.balatromobilemodmanager.domain.resolveModPrimaryAction
import com.balatromobilemodmanager.formatDownloads
import com.balatromobilemodmanager.openUrl
import com.balatromobilemodmanager.shortDescription
import com.balatromobilemodmanager.ui.theme.BmmColor
import com.balatromobilemodmanager.ui.theme.PixelFont
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import androidx.compose.ui.unit.sp

@Composable
internal fun ModDetailScreen(
    mod: CatalogMod,
    installed: Boolean,
    canGetOfficial: Boolean,
    hasUpdate: Boolean,
    operation: OperationState.Running?,
    enabled: Boolean?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onInstall: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val action = resolveModPrimaryAction(installed, hasUpdate, canGetOfficial)
    val actionEnabled = mod.supportsAutomaticInstall && action != ModPrimaryAction.Installed
    val actionColor = if (action == ModPrimaryAction.Installed) BmmColor.Neutral else BmmColor.Green

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BmmColor.Cream,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = mod.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                ModThumbnail(mod, Modifier.fillMaxSize())
            }
        }

        item {
            if (action == ModPrimaryAction.Installed && operation == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleEnabled()
                        },
                        modifier = Modifier.width(48.dp).height(52.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (enabled != false) BmmColor.GreenStrong else BmmColor.Neutral,
                            contentColor = Color.White,
                        ),
                    ) { Text(if (enabled != false) "ON" else "OFF", style = MaterialTheme.typography.labelMedium) }
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = BmmColor.Neutral,
                            disabledContentColor = Color.White.copy(alpha = 0.82f),
                        ),
                    ) { Text("Installed", style = MaterialTheme.typography.titleMedium) }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        },
                        modifier = Modifier.width(48.dp).height(52.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BmmColor.Danger,
                            contentColor = Color.White
                        ),
                    ) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onInstall()
                    },
                    enabled = actionEnabled && operation == null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(5.dp),
                    contentPadding = if (operation == null) PaddingValues(horizontal = 24.dp) else PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (operation != null) BmmColor.Green else actionColor,
                        contentColor = Color.White,
                        disabledContainerColor = if (operation != null) BmmColor.Green.copy(alpha = 0.58f) else BmmColor.Neutral,
                        disabledContentColor = Color.White.copy(alpha = 0.82f),
                    ),
                ) {
                    if (operation != null) {
                        ModOperationProgress(operation)
                    } else {
                        Icon(
                            if (action == ModPrimaryAction.Update) {
                                Icons.Filled.SystemUpdateAlt
                            } else {
                                Icons.Filled.Download
                            },
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(action.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.openUrl(mod.repo.ifBlank { mod.downloadUrl })
                },
                enabled = mod.repo.isNotBlank() || mod.downloadUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Repository, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Repository", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailMetric(
                    icon = Icons.Filled.Person,
                    value = mod.author.ifBlank { "Unknown" },
                    modifier = Modifier.weight(1f),
                )
                DetailMetric(
                    icon = Icons.Filled.Download,
                    value = formatCount(mod.downloadsTotal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (mod.categories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mod.categories.distinctBy { it.trim().lowercase() }.forEach { category ->
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTagClick(category)
                            },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(17.dp), tint = categoryColor(category))
                            Spacer(Modifier.width(6.dp))
                            Text(category, maxLines = 1, color = BmmColor.Cream)
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(BmmColor.Panel.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                    .border(1.dp, BmmColor.MutedCream.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Markdown(
                    content = mod.description.ifBlank { mod.shortDescription() },
                    colors = markdownColor(
                        text = BmmColor.Cream,
                        codeText = BmmColor.Cream,
                        inlineCodeText = BmmColor.Cream,
                        linkText = BmmColor.Accent,
                        codeBackground = BmmColor.PanelRaised,
                        inlineCodeBackground = BmmColor.PanelRaised,
                        dividerColor = BmmColor.GlassBorder,
                    ),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineSmall.copy(fontFamily = PixelFont),
                        h2 = MaterialTheme.typography.titleLarge.copy(fontFamily = PixelFont),
                        h3 = MaterialTheme.typography.titleMedium.copy(fontFamily = PixelFont),
                        text = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont),
                        paragraph = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont, lineHeight = 27.sp),
                        ordered = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont),
                        bullet = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont),
                        list = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont),
                        link = MaterialTheme.typography.bodyLarge.copy(fontFamily = PixelFont),
                    ),
                    padding = markdownPadding(block = 9.dp, list = 10.dp, listItemBottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailMetric(icon: ImageVector, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .background(BmmColor.Glass, RoundedCornerShape(8.dp))
            .border(1.dp, BmmColor.GlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BmmColor.Cream, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = value,
                color = BmmColor.Cream,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when {
    category.contains("quality", true) -> Icons.Filled.Star
    category.contains("technical", true) || category.contains("api", true) -> Icons.Filled.Build
    category.contains("multiplayer", true) -> Icons.Filled.Groups
    category.contains("content", true) -> Icons.Filled.Folder
    else -> Icons.Filled.Extension
}

private fun categoryColor(category: String): Color = when {
    category.contains("quality", true) -> BmmColor.Gold
    category.contains("technical", true) || category.contains("api", true) -> BmmColor.Green
    category.contains("multiplayer", true) -> BmmColor.Blue
    else -> Color(0xFFA982E8)
}

private fun formatCount(value: Long): String = String.format(java.util.Locale.US, "%,d", value).replace(',', '.')
