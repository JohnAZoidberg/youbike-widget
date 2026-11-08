package me.danielschaefer.android.youbike

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.danielschaefer.android.youbike.worker.WidgetUpdateWorker

enum class ThemeMode(val nightMode: Int) {
    System(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light(AppCompatDelegate.MODE_NIGHT_NO),
    Dark(AppCompatDelegate.MODE_NIGHT_YES)
}

object ThemePreference {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "theme_mode"

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        WidgetUpdateWorker.runOnce(context)
    }

    fun load(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.System.name)
        return ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(load(context).nightMode)
    }

    /** Emits the current theme mode and every subsequent change. */
    fun modeFlow(context: Context): Flow<ThemeMode> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME) trySend(load(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun isDark(context: Context, mode: ThemeMode = load(context)): Boolean {
        return when (mode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
