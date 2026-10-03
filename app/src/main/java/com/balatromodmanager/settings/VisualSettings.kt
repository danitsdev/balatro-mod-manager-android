package com.balatromodmanager.settings

data class VisualSettings(
    val animatedBackground: Boolean = false,
    val cardSize: CatalogCardSize = CatalogCardSize.MEDIUM,
    val darkMode: Boolean = true,
)

enum class CatalogCardSize(val columnsPerRow: Int) {
    SMALL(3),
    MEDIUM(2),
    LARGE(1),
}
