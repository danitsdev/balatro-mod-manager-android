package com.balatromobilemodmanager.settings

data class VisualSettings(
    val animatedBackground: Boolean = false,
    val cardScale: Float = 1f,
    val darkMode: Boolean = false,
) {
    val cardMinWidthDp: Float get() = 165f * cardScale
}
