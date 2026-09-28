package com.arjun.core_alert

import org.koin.dsl.module

val dataModule = module {
    single { PrefsDataSource(get()) }

    single<ContactRepository> { ContactRepositoryImpl(get()) }
    single<AlertSettingsRepository> { AlertSettingsRepositoryImpl(get()) }
    single<ThemeRepository> { ThemeRepositoryImpl(get()) }
    single<OverrideStateRepository> { OverrideStateRepositoryImpl(get()) }
    single<MessageBindingRepository> { MessageBindingRepositoryImpl(get()) }

    factory<RepeatCallRepository> { (phoneBusy: Boolean) ->
        RepeatCallRepositoryImpl(get(), get(), phoneBusy)
    }
}
