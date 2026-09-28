package com.arjun.core_alert

import com.arjun.core_alert.models.BuildInfo
import org.koin.dsl.module

val appModule = module {
    single { BuildInfo(BuildConfig.DEBUG) }
}
