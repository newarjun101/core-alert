package com.arjun.core_alert

import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.NightMode

internal class ThemeRepositoryImpl(private val source: PrefsDataSource) : ThemeRepository {

    private val prefs get() = source.prefs

    override val palette: AppPalette
        get() = AppPalette.fromStoredOrdinal(prefs.getInt(KEY_THEME_PALETTE, AppPalette.INDACO.ordinal))

    override fun setPalette(palette: AppPalette) = source.put(KEY_THEME_PALETTE, palette.ordinal)

    override val nightMode: NightMode
        get() = NightMode.fromStored(prefs.getInt(KEY_THEME_MODE, NightMode.FOLLOW_SYSTEM.storedValue))

    override fun setNightMode(mode: NightMode) = source.put(KEY_THEME_MODE, mode.storedValue)

    companion object {
        private const val KEY_THEME_PALETTE = "theme_palette"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
