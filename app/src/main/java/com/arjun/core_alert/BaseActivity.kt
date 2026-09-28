package com.arjun.core_alert

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.koin.android.ext.android.get

abstract class BaseActivity : AppCompatActivity() {

    private val theme: ThemeRepository by lazy { get() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.themeResId(theme.palette))
        ThemeManager.applyPalette(theme.palette)
        super.onCreate(savedInstanceState)
        // Single-activity Compose UI: draw behind the system bars and let the
        // Scaffold / drawer apply the insets themselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LanguageManager.reapply(this)
        applySystemBars()
        window.decorView.dispatchConfigurationChanged(Configuration(resources.configuration))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    @Suppress("DEPRECATION")
    protected fun applySystemBars() {
        val background = ThemeManager.color(this, android.R.attr.colorBackground)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false

        val darkIcons = !ThemeManager.isDark(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
