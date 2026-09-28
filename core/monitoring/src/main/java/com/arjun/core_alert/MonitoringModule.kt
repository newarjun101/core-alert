package com.arjun.core_alert

import org.koin.dsl.module

val monitoringModule = module {
    single { VipMessageAlertsProvider(get()) }
}
