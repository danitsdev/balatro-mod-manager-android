package com.balatromodmanager.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.balatromodmanager.OperationState
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.domain.ModPrimaryAction
import com.balatromodmanager.domain.resolveModPrimaryAction
import com.balatromodmanager.shortDescription
import com.balatromodmanager.ui.theme.BmmColor

@Composable
internal fun ModCard(
    mod: CatalogMod,
    installed: Boolean,
    enabled: Boolean?,
    hasUpdate: Boolean,
    canGetOfficial: Boolean,
    operation: OperationState.Running?,
    busy: Boolean,
    localToggleBusy: Boolean,
    openEnabled: Boolean = true,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val supported = mod.supportsAutomaticInstall
    val (base, stripe) = desktopCardColors(mod.title, BmmColor.darkMode)
    val cardShape = RoundedCornerShape(4.dp)
    val action = resolveModPrimaryAction(installed, hasUpdate, canGetOfficial)

    Card(
        modifier = Modifier
            .border(1.dp, stripe, cardShape)
            .clickable(enabled = openEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(base, cardShape)) {
            val cardWidth = maxWidth
            val compact = cardWidth < 145.dp
            val horizontal = cardWidth >= 300.dp
            if (horizontal) {
                val imageSize = (cardWidth * 0.34f).coerceAtLeast(124.dp).coerceAtMost(160.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(9.dp).height(imageSize),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ModThumbnail(mod, Modifier.size(imageSize).aspectRatio(1f).clip(RoundedCornerShape(5.dp)))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ModCardText(mod, compact = false, horizontal = true)
                        Spacer(Modifier.weight(1f))
                        ModCardActions(
                            action = action,
                            operation = operation,
                            installed = installed,
                            enabled = enabled,
                            busy = busy,
                            localToggleBusy = localToggleBusy,
                            supported = supported,
                            compact = false,
                            onToggleEnabled = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleEnabled()
                            },
                            onInstall = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onInstall()
                            },
                            onRemove = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemove()
                            },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(if (compact) 5.dp else 8.dp)) {
                    ModThumbnail(mod, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
                    ModCardText(mod, compact = compact, horizontal = false)
                    Spacer(Modifier.height(3.dp))
                    ModCardActions(
                        action = action,
                        operation = operation,
                        installed = installed,
                        enabled = enabled,
                        busy = busy,
                        localToggleBusy = localToggleBusy,
                        supported = supported,
                        compact = compact,
                        onToggleEnabled = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleEnabled()
                        },
                        onInstall = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInstall()
                        },
                        onRemove = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModCardText(mod: CatalogMod, compact: Boolean, horizontal: Boolean) {
    Text(
        text = mod.title,
        modifier = Modifier.fillMaxWidth(),
        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        color = BmmColor.Gold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = mod.shortDescription(),
        modifier = if (horizontal) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().height(if (compact) 34.dp else 44.dp),
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = BmmColor.Cream,
        maxLines = if (horizontal) 2 else if (compact) 2 else 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ModCardActions(
    action: ModPrimaryAction,
    operation: OperationState.Running?,
    installed: Boolean,
    enabled: Boolean?,
    busy: Boolean,
    localToggleBusy: Boolean,
    supported: Boolean,
    compact: Boolean,
    onToggleEnabled: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabled != null) {
            Button(
                onClick = onToggleEnabled,
                enabled = !busy || localToggleBusy,
                modifier = Modifier.width(if (compact) 24.dp else 40.dp).height(32.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) BmmColor.GreenStrong else BmmColor.Neutral,
                    contentColor = Color.White,
                ),
            ) {
                if (compact) Icon(Icons.Filled.PowerSettingsNew, contentDescription = if (enabled) "Disable" else "Enable", modifier = Modifier.size(15.dp))
                else Text(if (enabled) "ON" else "OFF", style = MaterialTheme.typography.labelSmall)
            }
        }
        Button(
            onClick = onInstall,
            enabled = !busy && supported && action != ModPrimaryAction.Installed && operation == null,
            modifier = Modifier.weight(1f).height(32.dp),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = if (compact) 0.dp else 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BmmColor.Green,
                contentColor = Color.White,
                disabledContainerColor = if (operation != null) BmmColor.Green.copy(alpha = 0.8f) else BmmColor.Neutral,
                disabledContentColor = Color.White.copy(alpha = 0.82f),
            ),
        ) {
            if (operation != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ModOperationProgress(operation)
                    if (!compact) {
                        Text(
                            operation.message,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = if (action == ModPrimaryAction.Update) Icons.Filled.SystemUpdateAlt else Icons.Filled.Download,
                    contentDescription = if (compact) action.label else null,
                    modifier = Modifier.size(15.dp),
                )
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (installed) {
            Button(
                onClick = onRemove,
                enabled = !busy,
                modifier = Modifier.width(if (compact) 24.dp else 36.dp).height(32.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Danger, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun ModOperationProgress(operation: OperationState.Running) {
    val progress = operation.progress?.coerceIn(0f, 1f)?.takeIf { it > 0f }
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(18.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.22f),
                strokeWidth = 2.dp,
            )
        }
    }
}

private fun desktopCardColors(key: String, darkMode: Boolean): Pair<Color, Color> {
    val palette = if (darkMode) DarkCardPalette else LightCardPalette
    var hash = 0L
    key.forEach { char -> hash = (hash * 31L + char.code) and 0xFFFF_FFFFL }
    return palette[(hash % palette.size).toInt()]
}

private val LightCardPalette = listOf(
    Color(0xFF4F6367) to Color(0xFF425556),
    Color(0xFFAA778D) to Color(0xFF906577),
    Color(0xFFA2615E) to Color(0xFF89534F),
    Color(0xFFA48447) to Color(0xFF8B703C),
    Color(0xFF4F7869) to Color(0xFF436659),
    Color(0xFF728DBF) to Color(0xFF6177A3),
    Color(0xFF5D5E8F) to Color(0xFF4F4F78),
    Color(0xFF796E9E) to Color(0xFF655D86),
    Color(0xFF64825D) to Color(0xFF556E4E),
    Color(0xFF86A367) to Color(0xFF728A57),
    Color(0xFF748C8A) to Color(0xFF627775),
)

private val DarkCardPalette = listOf(
    Color(0xFF262B31) to Color(0xFF1B2026), Color(0xFF2A2F36) to Color(0xFF1F242A),
    Color(0xFF2F353D) to Color(0xFF232A31), Color(0xFF343B44) to Color(0xFF262C33),
    Color(0xFF3C434D) to Color(0xFF2D343D), Color(0xFF3F4651) to Color(0xFF2F3741),
    Color(0xFF1B2C44) to Color(0xFF132238), Color(0xFF1D3149) to Color(0xFF15263D),
    Color(0xFF1F354F) to Color(0xFF172A41), Color(0xFF223A5A) to Color(0xFF1A2F4B),
    Color(0xFF243F62) to Color(0xFF1C3450), Color(0xFF28466F) to Color(0xFF203958),
    Color(0xFF2B4B7D) to Color(0xFF213A63), Color(0xFF2D507F) to Color(0xFF234066),
    Color(0xFF294C73) to Color(0xFF1F3D5F), Color(0xFF295A8F) to Color(0xFF1F476F),
    Color(0xFF2F5B93) to Color(0xFF234578), Color(0xFF3466AB) to Color(0xFF27508F),
    Color(0xFF3A6FC0) to Color(0xFF2B59A1), Color(0xFF3F78CF) to Color(0xFF2F61AD),
    Color(0xFF3C4B5B) to Color(0xFF2D3946), Color(0xFF425165) to Color(0xFF313D4E),
    Color(0xFF22364D) to Color(0xFF1A2B40), Color(0xFF2A4362) to Color(0xFF213652),
)
