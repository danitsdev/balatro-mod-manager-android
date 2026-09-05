package com.balatromodmanager.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
                cardScale = (preferences[Keys.cardScale]
                    ?: preferences[Keys.cardMinWidthDp]?.div(165f)
                    ?: 1f).coerceIn(0.75f, 1.4f),
                darkMode = preferences[Keys.darkMode] ?: false,
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun save(settings: VisualSettings) = withContext(Dispatchers.IO) {
        dataStore.edit { preferences ->
            preferences[Keys.animatedBackground] = settings.animatedBackground
            preferences[Keys.cardScale] = settings.cardScale.coerceIn(0.75f, 1.4f)
            preferences[Keys.darkMode] = settings.darkMode
        }
    }

    private object Keys {
        val animatedBackground = booleanPreferencesKey("background_enabled")
        val cardMinWidthDp = floatPreferencesKey("catalog_card_min_width_dp")
        val cardScale = floatPreferencesKey("card_scale")
        val darkMode = booleanPreferencesKey("dark_mode")
    }
}
