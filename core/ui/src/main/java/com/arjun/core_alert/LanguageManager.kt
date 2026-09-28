package com.arjun.core_alert

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import com.arjun.core_alert.models.AppLanguage
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "corealert_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(prefs(context).getString(KEY_LANGUAGE, null))

    fun apply(context: Context, language: AppLanguage, activity: Activity? = null) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        val locale = localeOf(language.tag)
        Locale.setDefault(locale)
        update(context, locale)
        if (activity != null) {
            update(activity, locale)
            activity.onConfigurationChanged(Configuration(activity.resources.configuration))
        }
    }

    fun reapply(context: Context) {
        val locale = localeOf(prefs(context).getString(KEY_LANGUAGE, null))
        if (context.resources.configuration.locales[0] == locale) return
        update(context, locale)
    }

    fun wrap(base: Context): Context {
        val locale = localeOf(prefs(base).getString(KEY_LANGUAGE, null))
        Locale.setDefault(locale)
        if (locale == systemLocale(base)) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    private fun update(context: Context, locale: Locale) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    private fun localeOf(tag: String?): Locale =
        if (tag.isNullOrEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)

    private fun systemLocale(base: Context): Locale = Resources.getSystem().configuration.locales[0]

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
