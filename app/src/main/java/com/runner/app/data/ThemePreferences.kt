package com.runner.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ColorSource {
    DYNAMIC,
    CUSTOM
}

data class ThemeConfig(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DYNAMIC,
    val customSeedColor: Int = 0xFF90CAF9.toInt(),
    val isAmoled: Boolean = false
) {
    companion object {
        /** 12 гармоничных Material-акцентов для палитры выбора "Свой цвет". */
        val PRESET_COLORS = listOf(
            0xFF90CAF9.toInt(), // Sky Blue (Дефолтный Runner)
            0xFF80CBC4.toInt(), // Teal
            0xFF81C784.toInt(), // Mint / Green
            0xFFAED581.toInt(), // Lime / Olive
            0xFFFFD54F.toInt(), // Amber / Gold
            0xFFFFB74D.toInt(), // Warm Orange
            0xFFFF8A65.toInt(), // Coral / Deep Orange
            0xFFE57373.toInt(), // Rose / Red
            0xFFF48FB1.toInt(), // Berry / Pink
            0xFFCE93D8.toInt(), // Orchid / Purple
            0xFFB39DDB.toInt(), // Lavender / Violet
            0xFF9FA8DA.toInt()  // Indigo / Periwinkle
        )
    }
}

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

class ThemeStore(context: Context) {

    private val dataStore = context.applicationContext.themeDataStore

    val themeFlow: Flow<ThemeConfig> = dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val sourceStr = prefs[KEY_COLOR_SOURCE] ?: ColorSource.DYNAMIC.name
        val mode = runCatching { AppThemeMode.valueOf(modeStr) }.getOrDefault(AppThemeMode.SYSTEM)
        val source = runCatching { ColorSource.valueOf(sourceStr) }.getOrDefault(ColorSource.DYNAMIC)
        val seed = prefs[KEY_CUSTOM_SEED] ?: 0xFF90CAF9.toInt()
        val amoled = prefs[KEY_AMOLED] ?: false

        ThemeConfig(
            themeMode = mode,
            colorSource = source,
            customSeedColor = seed,
            isAmoled = amoled
        )
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun updateColorSource(source: ColorSource) {
        dataStore.edit { prefs ->
            prefs[KEY_COLOR_SOURCE] = source.name
        }
    }

    suspend fun updateCustomSeedColor(color: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SEED] = color
            prefs[KEY_COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun updateAmoled(isAmoled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AMOLED] = isAmoled
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        val KEY_CUSTOM_SEED = intPreferencesKey("custom_seed_color")
        val KEY_AMOLED = booleanPreferencesKey("is_amoled")
    }
}
