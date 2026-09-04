package com.balatromobilemodmanager

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Precision
import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.CatalogSortMode
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.domain.hasUpdateFor
import com.balatromobilemodmanager.domain.matchesLocal
import com.balatromobilemodmanager.settings.VisualSettings
import com.balatromobilemodmanager.ui.theme.BmmColor
import com.balatromobilemodmanager.ui.catalog.DesktopModCard
import com.balatromobilemodmanager.ui.catalog.ModDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogScreen(
    state: MainUiState,
    padding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSortChange: (CatalogSortMode) -> Unit,
    onRefreshCatalog: () -> Unit,
    onHydrateCatalogMod: (String) -> Unit,
    visualSettings: VisualSettings,
    selectedMod: CatalogMod?,
    onOpenMod: (CatalogMod) -> Unit,
    onBackFromMod: () -> Unit,
    onInstall: (CatalogMod) -> Unit,
    onSetLocalModEnabled: (String, Boolean) -> Unit,
    onUninstall: (ManagedInstallManifest) -> Unit,
    onRemoveLocalMod: (String) -> Unit,
    onClearOperation: () -> Unit,
) {
    // Keep the grid state alive while the detail screen replaces the catalog content.
    val gridState = rememberLazyGridState()

    if (selectedMod != null) {
        LaunchedEffect(selectedMod.id) { onHydrateCatalogMod(selectedMod.id) }
        val manifest = selectedMod.managedManifest(state)
        val localMod = state.localMods.firstOrNull { selectedMod.matchesLocal(it) }
        ModDetailScreen(
            mod = selectedMod,
            installed = selectedMod.isInstalled(state),
            canGetOfficial = manifest == null && localMod != null,
            hasUpdate = manifest?.let { selectedMod.hasUpdateFor(it) } == true,
            operation = (state.operation as? OperationState.Running)
                ?.takeIf { it.downloadingModId == selectedMod.id },
            enabled = localMod?.enabled,
            padding = padding,
            onBack = onBackFromMod,
            onTagClick = { onCategoryChange(it); onBackFromMod() },
            onInstall = { onInstall(selectedMod) },
            onToggleEnabled = {
                if (localMod != null) onSetLocalModEnabled(localMod.folderName, !localMod.enabled)
            },
            onRemove = {
                if (manifest != null) onUninstall(manifest)
                else if (localMod != null) onRemoveLocalMod(localMod.folderName)
            },
        )
        return
    }

    CatalogThumbnailPrefetcher(
        mods = state.visibleMods,
        gridState = gridState,
        paused = state.operation is OperationState.Running,
    )

    PullToRefreshBox(
        isRefreshing = state.isCatalogRefreshing,
        onRefresh = onRefreshCatalog,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (state.isCatalogLoading && state.catalogMods.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = BmmColor.Gold)
                Spacer(Modifier.height(12.dp))
                Text("Loading Catalog", color = BmmColor.Cream)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(visualSettings.cardMinWidthDp.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogHeader(state, onQueryChange, onCategoryChange, onSortChange, onClearOperation)
                }
                gridItems(state.visibleMods, key = { it.id }, contentType = { "catalog-mod" }) { mod ->
                    val manifest = mod.managedManifest(state)
                    val installed = mod.isInstalled(state)
                    val localMod = state.localMods.firstOrNull { mod.matchesLocal(it) }
                    DesktopModCard(
                        mod = mod,
                        installed = installed,
                        enabled = localMod?.enabled,
                        hasUpdate = manifest?.let { mod.hasUpdateFor(it) } == true,
                        canGetOfficial = manifest == null && localMod != null,
                        operation = (state.operation as? OperationState.Running)
                            ?.takeIf { it.downloadingModId == mod.id },
                        onOpen = { onOpenMod(mod) },
                        onInstall = { onInstall(mod) },
                        onToggleEnabled = {
                            if (localMod != null) onSetLocalModEnabled(localMod.folderName, !localMod.enabled)
                        },
                        onRemove = {
                            if (manifest != null) onUninstall(manifest)
                            else if (localMod != null) onRemoveLocalMod(localMod.folderName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSortChange: (CatalogSortMode) -> Unit,
    onClearOperation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HeroHeader()
            if (state.isCatalogRefreshing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = BmmColor.Gold, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Refreshing catalog in background...", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        OutlinedTextField(
            value = state.filters.query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BmmColor.MutedCream) },
            placeholder = {
                Text("Search mods...", color = BmmColor.MutedCream, style = MaterialTheme.typography.bodyLarge)
            },
            textStyle = MaterialTheme.typography.bodyLarge,
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryBar(state.categories, state.filters.selectedCategory, onCategoryChange, Modifier.weight(1f))
            SortBar(state.filters.sortMode, onSortChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CatalogThumbnailPrefetcher(
    mods: List<CatalogMod>,
    gridState: LazyGridState,
    paused: Boolean,
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val thumbnailWidthPx = (ThumbnailPrefetchWidthDp * context.resources.displayMetrics.density).toInt()
    val thumbnailHeightPx = (thumbnailWidthPx / ThumbnailAspectRatio).toInt()
    val queuedUrls = remember { mutableSetOf<String>() }
    val requests = remember { mutableListOf<Disposable>() }

    DisposableEffect(Unit) {
        onDispose { requests.forEach(Disposable::dispose) }
    }

    LaunchedEffect(mods, gridState, paused) {
        requests.forEach(Disposable::dispose)
        requests.clear()
        queuedUrls.clear()
        if (paused) return@LaunchedEffect

        // Let on-screen cards claim the network and decoder first on a cold start.
        delay(ThumbnailPrefetchStartDelayMs)

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleItem ->
                val firstAhead = (lastVisibleItem + 1).coerceAtLeast(0)
                val lastAhead = (firstAhead + ThumbnailPrefetchWindow).coerceAtMost(mods.size)
                for (index in firstAhead until lastAhead) {
                    val url = mods[index].thumbnailUrl.trim()
                    if (url.isBlank() || !queuedUrls.add(url)) continue
                    requests += imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(thumbnailWidthPx, thumbnailHeightPx)
                            .precision(Precision.INEXACT)
                            .dispatcher(ThumbnailPrefetchDispatcher)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
    }
}

private const val ThumbnailPrefetchWindow = 12
private const val ThumbnailPrefetchStartDelayMs = 5_000L
private const val ThumbnailPrefetchWidthDp = 192
private const val ThumbnailAspectRatio = 1.72f
private val ThumbnailPrefetchDispatcher = Dispatchers.IO.limitedParallelism(2)

// ═══════════════════════════════════════════════════════════════════════
//  ModCard — vertical layout like PC: image top, title, buttons bottom
// ═══════════════════════════════════════════════════════════════════════

@Composable
internal fun ModCard(
    mod: CatalogMod,
    installed: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    BmmColor.Panel.copy(alpha = 0.95f),
                    RoundedCornerShape(8.dp),
                )
                .clip(RoundedCornerShape(8.dp)),
        ) {
            // ── Image area with stripe background (PC style) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                // Stripe gradient background (always shown, like PC)
                ModStripedBackground(mod = mod)
                // Image overlays on top
                if (mod.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = mod.thumbnailUrl,
                        contentDescription = mod.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No image: show large initial
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = mod.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            // ── Title ──
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    mod.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = BmmColor.Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    mod.author.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = BmmColor.MutedCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // ── Action buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed) {
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BmmColor.Danger),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = BmmColor.Gold, strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = onInstall,
                        enabled = mod.supportsAutomaticInstall,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BmmColor.Green),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download", style = MaterialTheme.typography.labelSmall, color = BmmColor.Cream)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ModStripedBackground — PC-style stripe gradient (always visible)
// ═══════════════════════════════════════════════════════════════════════

@Composable
internal fun ModStripedBackground(mod: CatalogMod, modifier: Modifier = Modifier) {
    val color1 = mod.accentColor().copy(alpha = 0.7f)
    val color2 = mod.accentColor().copy(alpha = 0.45f)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color1)
        val stripe = 10.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = color2,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stripe,
            )
            x += stripe * 2f
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ModThumbnail — shared thumbnail with async image + fallback
// ═══════════════════════════════════════════════════════════════════════

@Composable
internal fun ModThumbnail(mod: CatalogMod, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loading by remember(mod.thumbnailUrl) { mutableStateOf(mod.thumbnailUrl.isNotBlank()) }
    val imageRequest = remember(mod.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(mod.thumbnailUrl)
            .precision(Precision.INEXACT)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier
            .background(BmmColor.Panel)
            .clip(RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (mod.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = mod.title,
                contentScale = ContentScale.Crop,
                onLoading = { loading = true },
                onSuccess = { loading = false },
                onError = { loading = false },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = BmmColor.Gold,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            loading = false
        }
    }
}
