package com.arjun.core_alert

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.arjun.core_alert.coreui.R
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.NightMode

object ThemeManager {
    var livePalette by mutableStateOf(AppPalette.INDACO)
        private set

    fun applyPalette(palette: AppPalette) {
        if (palette != livePalette) livePalette = palette
    }

    fun themeResId(palette: AppPalette): Int = when (palette) {
        AppPalette.INDACO -> R.style.Theme_CoreAlert_Indaco
        AppPalette.TEAL -> R.style.Theme_CoreAlert_Teal
        AppPalette.ARGILLA -> R.style.Theme_CoreAlert_Argilla
        AppPalette.ARDESIA -> R.style.Theme_CoreAlert_Ardesia
    }

    fun applyNightMode(mode: NightMode) {
        val storedValue = mode.storedValue
        if (AppCompatDelegate.getDefaultNightMode() != storedValue) {
            AppCompatDelegate.setDefaultNightMode(storedValue)
        }
    }

    fun isDark(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }

    @ColorInt
    fun color(context: Context, @AttrRes attribute: Int): Int {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute 0x${attribute.toString(16)} is not defined"
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
