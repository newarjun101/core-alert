package com.arjun.core_alert.feature.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.arjun.core_alert.feature.R

enum class AppRoute(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    HOME("home", R.string.nav_home, R.drawable.ic_home),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_sliders),
    PRIVACY("privacy", R.string.nav_privacy, R.drawable.ic_info);

    companion object {
        fun fromRoute(route: String?): AppRoute = entries.firstOrNull { it.route == route } ?: HOME
    }
}
