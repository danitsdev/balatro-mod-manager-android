package com.balatromodmanager.settings

data class VisualSettings(
    val animatedBackground: Boolean = false,
    val cardSize: CatalogCardSize = CatalogCardSize.LARGE,
    val darkMode: Boolean = true,
)

enum class CatalogCardSize(val minimumCellWidthDp: Int, val description: String) {
    SMALL(100, "More cards"),
    MEDIUM(155, "Balanced"),
    LARGE(270, "Larger cards"),
}
