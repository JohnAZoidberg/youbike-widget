package me.danielschaefer.android.youbike

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val nightMode: Int) {
    System(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light(AppCompatDelegate.MODE_NIGHT_NO),
    Dark(AppCompatDelegate.MODE_NIGHT_YES);
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
    }

    fun load(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.System.name)
        return ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(load(context).nightMode)
    }
}
