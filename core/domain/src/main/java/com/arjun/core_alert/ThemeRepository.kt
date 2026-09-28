package com.arjun.core_alert

import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.NightMode

interface ThemeRepository {

    val palette: AppPalette

    fun setPalette(palette: AppPalette)

    val nightMode: NightMode

    fun setNightMode(mode: NightMode)
}
