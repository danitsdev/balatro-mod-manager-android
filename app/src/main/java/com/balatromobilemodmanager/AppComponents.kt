package com.balatromobilemodmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import com.balatromobilemodmanager.catalog.CatalogSortMode
import com.balatromobilemodmanager.ui.theme.BmmColor

@Composable
internal fun PixelPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = BmmColor.Cream,
    containerColor: Color = BmmColor.PanelOpaque,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun CenterPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelPanel(modifier = Modifier.padding(24.dp), content = content)
    }
}

@Composable
internal fun SectionTitle(title: String, badge: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
        StatusPill(badge, BmmColor.PanelRaised)
    }
}

@Composable
internal fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier.background(color, RoundedCornerShape(3.dp)).border(1.dp, BmmColor.Cream.copy(alpha = 0.55f), RoundedCornerShape(3.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
    ) { Text(label, color = BmmColor.Cream, fontSize = 14.sp, maxLines = 1) }
}

@Composable
internal fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    PixelPanel(modifier = modifier, borderColor = color) {
        Text(title, color = BmmColor.MutedCream)
        Text(value, style = MaterialTheme.typography.displaySmall, color = color)
    }
}

@Composable
internal fun OperationBanner(operation: OperationState, onDismiss: () -> Unit) {
    when (operation) {
        OperationState.Idle -> Unit
        is OperationState.Running -> PixelPanel(borderColor = BmmColor.Blue) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = BmmColor.Gold, modifier = Modifier.size(22.dp))
                Text(operation.message.redactPrivatePaths(), color = BmmColor.Cream)
            }
        }
        is OperationState.Done -> PixelPanel(borderColor = BmmColor.Green) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BmmColor.Green)
                Text(operation.message.redactPrivatePaths(), color = BmmColor.Cream, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(3.dp)) { Text("OK") }
            }
        }
        is OperationState.Error -> PixelPanel(borderColor = BmmColor.Danger, containerColor = Color(0xFF4A232A)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = BmmColor.Gold)
                Text(operation.message.redactPrivatePaths(), color = BmmColor.Cream, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(3.dp)) { Text("OK") }
            }
        }
    }
}

@Composable
internal fun HeroHeader() {
    Text(text = "Balatro Mod Manager", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displaySmall, color = BmmColor.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun LoadingScreen() {
    CenterPanel {
        CircularProgressIndicator(color = BmmColor.Gold)
        Spacer(Modifier.height(16.dp))
        Text("Loading", color = BmmColor.Cream)
    }
}

@Composable
internal fun compactSystemBarPadding(includeBottom: Boolean = true): PaddingValues {
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = safePadding.calculateStartPadding(layoutDirection),
        top = safePadding.calculateTopPadding() / 2,
        end = safePadding.calculateEndPadding(layoutDirection),
        bottom = if (includeBottom) safePadding.calculateBottomPadding() / 2 else 0.dp,
    )
}

@Composable
internal fun PagerBar(page: Int, pageCount: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = page > 0) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
        }
        Text("${page + 1} / $pageCount", color = BmmColor.Cream, modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = onNext, enabled = page < pageCount - 1) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
        }
    }
}

@Composable
internal fun CategoryBar(categories: List<String>, selected: String?, onCategoryChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = remember(widthPx) { with(density) { widthPx.toDp() } }

    Box(modifier.onGloballyPositioned { widthPx = it.size.width }) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = BmmColor.FilterControl, contentColor = BmmColor.Cream),
        ) {
            Text(selected ?: "All", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            modifier = Modifier.width(menuWidth).heightIn(max = 300.dp).background(BmmColor.PanelRaised)
        ) {
            DropdownMenuItem(text = { Text("All", color = BmmColor.Cream) }, onClick = { onCategoryChange(null); expanded = false })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category, color = BmmColor.Cream) }, onClick = { onCategoryChange(category); expanded = false })
            }
        }
    }
}

@Composable
internal fun SortBar(selected: CatalogSortMode, onSortChange: (CatalogSortMode) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = remember(widthPx) { with(density) { widthPx.toDp() } }

    Box(modifier.onGloballyPositioned { widthPx = it.size.width }) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = BmmColor.FilterControl, contentColor = BmmColor.Cream),
        ) {
            Text(selected.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            modifier = Modifier.width(menuWidth).heightIn(max = 300.dp).background(BmmColor.PanelRaised)
        ) {
            CatalogSortMode.entries.forEach { sortMode ->
                DropdownMenuItem(
                    text = { Text(sortMode.label, color = BmmColor.Cream) },
                    onClick = { onSortChange(sortMode); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun TagRow(tags: List<String>, onTagClick: ((String) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        tags.distinctBy { it.trim().lowercase() }.forEach { tag -> AssistChip(onClick = { onTagClick?.invoke(tag) }, label = { Text(tag) }, shape = RoundedCornerShape(4.dp)) }
    }
}

@Composable
internal fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = BmmColor.MutedCream)
        Spacer(Modifier.width(16.dp))
        Text(value, color = BmmColor.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SquareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val background = if (checked) BmmColor.Green else BmmColor.Neutral
    val border = BmmColor.Cream
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 24.dp)
            .background(background, RoundedCornerShape(4.dp))
            .border(1.5.dp, border.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(BmmColor.Cream, RoundedCornerShape(2.dp))
        )
    }
}
