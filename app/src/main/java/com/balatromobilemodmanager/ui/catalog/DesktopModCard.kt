package com.balatromobilemodmanager.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.balatromobilemodmanager.ModThumbnail
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.domain.ModPrimaryAction
import com.balatromobilemodmanager.domain.resolveModPrimaryAction
import com.balatromobilemodmanager.shortDescription
import com.balatromobilemodmanager.ui.theme.BmmColor

@Composable
internal fun DesktopModCard(
    mod: CatalogMod,
    installed: Boolean,
    enabled: Boolean?,
    hasUpdate: Boolean,
    canGetOfficial: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val supported = mod.supportsAutomaticInstall
    val (base, stripe) = desktopCardColors(mod.title, BmmColor.darkMode)
    val action = resolveModPrimaryAction(installed, hasUpdate, canGetOfficial)

    Card(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onOpen()
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .background(base, RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(base)
                    val stripeWidth = 8.dp.toPx()
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), stripeWidth)
                        x += stripeWidth * 2f
                    }
                }
        ) {
            Column(Modifier.padding(9.dp)) {
                ModThumbnail(mod, Modifier.fillMaxWidth().aspectRatio(1.72f))
                Spacer(Modifier.height(7.dp))
                Text(
                    text = mod.title,
                    modifier = Modifier.height(20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = BmmColor.Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mod.shortDescription(),
                    modifier = Modifier.height(45.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = BmmColor.Cream,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (enabled != null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleEnabled()
                            },
                            modifier = Modifier.width(34.dp).height(32.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (enabled) BmmColor.GreenStrong else BmmColor.Neutral,
                                contentColor = Color.White,
                            ),
                        ) { Text(if (enabled) "ON" else "OFF", style = MaterialTheme.typography.labelSmall) }
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInstall()
                        },
                        enabled = supported && action != ModPrimaryAction.Installed && !isDownloading,
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BmmColor.Green,
                            contentColor = Color.White,
                            disabledContainerColor = if (isDownloading) BmmColor.Green else BmmColor.Neutral,
                            disabledContentColor = Color.White.copy(alpha = 0.82f),
                        ),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = BmmColor.Cream, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (action == ModPrimaryAction.Update) {
                                    Icons.Filled.SystemUpdateAlt
                                } else {
                                    Icons.Filled.Download
                                },
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(action.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                    if (installed) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemove()
                            },
                            modifier = Modifier.width(34.dp).height(32.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BmmColor.Danger,
                                contentColor = Color.White
                            ),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
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
