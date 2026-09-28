package com.arjun.core_alert

import android.app.Application
import android.content.Context
import com.arjun.core_alert.feature.featureModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class CoreAlertApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        startKoin {
            androidContext(this@CoreAlertApp)
            modules(appModule, featureModule, dataModule, monitoringModule)
        }
        ThemeManager.applyNightMode(get<ThemeRepository>().nightMode)
    }
}
