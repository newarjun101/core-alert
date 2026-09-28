package com.arjun.core_alert.feature

import com.arjun.core_alert.feature.home.HomeViewModel
import com.arjun.core_alert.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get()) }

    viewModel { (onContactsChanged: () -> Unit) ->
        SettingsViewModel(get(), get(), get(), get(), onContactsChanged, get())
    }
}
