package com.balatromodmanager.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VisualSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    fun observe(): Flow<VisualSettings> {
        return dataStore.data.map { preferences ->
            VisualSettings(
                animatedBackground = preferences[Keys.animatedBackground] ?: false,
                cardSize = preferences[Keys.cardSize]?.let { saved ->
                    CatalogCardSize.entries.firstOrNull { it.name == saved }
                } ?: preferences.legacyCardSize(),
                darkMode = preferences[Keys.darkMode] ?: true,
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun save(settings: VisualSettings) = withContext(Dispatchers.IO) {
        dataStore.edit { preferences ->
            preferences[Keys.animatedBackground] = settings.animatedBackground
            preferences[Keys.cardSize] = settings.cardSize.name
            preferences[Keys.darkMode] = settings.darkMode
            preferences.remove(Keys.cardScale)
            preferences.remove(Keys.cardMinWidthDp)
        }
    }

    private fun Preferences.legacyCardSize(): CatalogCardSize {
        val minimumWidth = this[Keys.cardScale]?.times(165f)
            ?: this[Keys.cardMinWidthDp]
        return when {
            minimumWidth == null -> CatalogCardSize.MEDIUM
            minimumWidth < 140f -> CatalogCardSize.SMALL
            minimumWidth > 200f -> CatalogCardSize.LARGE
            else -> CatalogCardSize.MEDIUM
        }
    }

    private object Keys {
        val animatedBackground = booleanPreferencesKey("background_enabled")
        val cardMinWidthDp = floatPreferencesKey("catalog_card_min_width_dp")
        val cardScale = floatPreferencesKey("card_scale")
        val cardSize = stringPreferencesKey("catalog_card_size")
        val darkMode = booleanPreferencesKey("dark_mode")
    }
}
