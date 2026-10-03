package com.balatromodmanager.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Precision
import com.balatromodmanager.MainUiState
import com.balatromodmanager.OperationState
import com.balatromodmanager.accentColor
import com.balatromodmanager.isInstalled
import com.balatromodmanager.managedManifest
import com.balatromodmanager.catalog.CatalogMod
import com.balatromodmanager.catalog.CatalogSortMode
import com.balatromodmanager.catalog.ManagedInstallManifest
import com.balatromodmanager.domain.hasUpdateFor
import com.balatromodmanager.domain.matchesLocal
import com.balatromodmanager.settings.VisualSettings
import com.balatromodmanager.ui.CategoryBar
import com.balatromodmanager.ui.HeroHeader
import com.balatromodmanager.ui.SortBar
import com.balatromodmanager.ui.theme.BmmColor
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
    onRefreshCatalogModDetails: (String, String) -> Unit,
    onHydrateCatalogMod: (String) -> Unit,
    onHydrateCatalogVersion: (String, String) -> Unit,
    visualSettings: VisualSettings,
    selectedMod: CatalogMod?,
    onOpenMod: (CatalogMod) -> Unit,
    onOpenDependency: (CatalogMod) -> Unit,
    onBackFromMod: () -> Unit,
    onInstall: (CatalogMod) -> Unit,
    onSetLocalModEnabled: (String, Boolean) -> Unit,
    onUninstall: (ManagedInstallManifest) -> Unit,
    onRemoveLocalMod: (String) -> Unit,
    onClearOperation: () -> Unit,
) {
    // Keep the grid state alive while the detail screen replaces the catalog content.
    val gridState = rememberLazyGridState()
    val localToggleBusy = (state.operation as? OperationState.Running)?.isLocalToggle == true

    if (selectedMod != null) {
        LaunchedEffect(selectedMod.id) { onHydrateCatalogMod(selectedMod.id) }
        val manifest = selectedMod.managedManifest(state)
        val localMod = state.localMods.firstOrNull { selectedMod.matchesLocal(it) }
        ModDetailScreen(
            mod = selectedMod,
            catalogMods = state.catalogMods,
            installed = selectedMod.isInstalled(state),
            canGetOfficial = manifest == null && localMod != null,
            hasUpdate = manifest?.let { selectedMod.hasUpdateFor(it) } == true,
            operation = (state.operation as? OperationState.Running)
                ?.takeIf { it.downloadingModId == selectedMod.id },
            busy = state.operation is OperationState.Running,
            localToggleBusy = localToggleBusy,
            enabled = localMod?.enabled,
            isRefreshing = state.isCatalogRefreshing,
            installedVersion = manifest?.version?.takeIf(String::isNotBlank)
                ?: localMod?.version?.takeIf(String::isNotBlank),
            padding = padding,
            onBack = onBackFromMod,
            onTagClick = { onCategoryChange(it); onBackFromMod() },
            onInstall = onInstall,
            onRefresh = { versionNumber -> onRefreshCatalogModDetails(selectedMod.id, versionNumber) },
            onHydrateVersion = { versionNumber -> onHydrateCatalogVersion(selectedMod.id, versionNumber) },
            onOpenDependency = onOpenDependency,
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

    val showInitialCatalogLoader = state.isCatalogLoading && state.catalogMods.isEmpty()
    PullToRefreshBox(
        isRefreshing = state.isCatalogRefreshing && !showInitialCatalogLoader,
        onRefresh = onRefreshCatalog,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (showInitialCatalogLoader) {
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
                columns = GridCells.Adaptive(visualSettings.cardSize.minimumCellWidthDp.dp),
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
                    ModCard(
                        mod = mod,
                        installed = installed,
                        enabled = localMod?.enabled,
                        hasUpdate = manifest?.let { mod.hasUpdateFor(it) } == true,
                        canGetOfficial = manifest == null && localMod != null,
                        operation = (state.operation as? OperationState.Running)
                            ?.takeIf { it.downloadingModId == mod.id },
                        busy = state.operation is OperationState.Running,
                        localToggleBusy = localToggleBusy,
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
    val onSearchChange: (String) -> Unit = { query ->
        if (query.isNotBlank() && state.filters.selectedCategory != null) {
            onCategoryChange(null)
        }
        onQueryChange(query)
    }

    BoxWithConstraints {
        val wideLayout = maxWidth >= 600.dp
        val contentWidth = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(if (wideLayout) 8.dp else 12.dp)) {
            if (wideLayout) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Mod Catalog", style = MaterialTheme.typography.titleLarge, color = BmmColor.Cream)
                    CatalogSearchField(state.filters.query, onSearchChange, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HeroHeader()
                    if (state.isCatalogRefreshing) CatalogRefreshIndicator()
                }
                CatalogSearchField(state.filters.query, onSearchChange, Modifier.fillMaxWidth())
            }

            if (wideLayout && state.isCatalogRefreshing) CatalogRefreshIndicator()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val filterWidth = minOf(contentWidth / 2, if (wideLayout) 280.dp else contentWidth)
                CategoryBar(
                    state.categories,
                    state.filters.selectedCategory,
                    onCategoryChange,
                    Modifier.weight(1f).widthIn(max = filterWidth),
                )
                SortBar(
                    state.filters.sortMode,
                    onSortChange,
                    Modifier.weight(1f).widthIn(max = filterWidth),
                )
            }
        }
    }
}

@Composable
private fun CatalogSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
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
}

@Composable
private fun CatalogRefreshIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = BmmColor.Gold, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        Spacer(Modifier.width(6.dp))
        Text("Refreshing catalog in background...", color = BmmColor.MutedCream, style = MaterialTheme.typography.labelSmall)
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
private const val ThumbnailAspectRatio = 1f
private val ThumbnailPrefetchDispatcher = Dispatchers.IO.limitedParallelism(2)



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


@Composable
internal fun ModThumbnail(mod: CatalogMod, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loading by remember(mod.thumbnailUrl) { mutableStateOf(mod.thumbnailUrl.isNotBlank()) }
    val imageRequest = remember(mod.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(mod.thumbnailUrl)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (mod.thumbnailUrl.isNotBlank()) {
            key(mod.thumbnailUrl) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = mod.title,
                    contentScale = ContentScale.Fit,
                    onLoading = { loading = true },
                    onSuccess = { loading = false },
                    onError = { loading = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = BmmColor.Gold,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
