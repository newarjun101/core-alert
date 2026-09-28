package com.arjun.core_alert

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import com.arjun.core_alert.feature.home.HomeViewModel
import com.arjun.core_alert.feature.navigation.CoreAlertRoot
import com.arjun.core_alert.ui.theme.CoreAlertTheme
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : BaseActivity() {

    private lateinit var alertSettings: AlertSettingsRepository
    private val home: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alertSettings = get()

        setContent {
            CoreAlertTheme(palette = ThemeManager.livePalette) {
                CoreAlertRoot()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        home.refreshStrings()
    }

    override fun onResume() {
        super.onResume()
        home.onResume()
        ensureMonitoringRunning()
    }

    /**
     * The switch reflects the stored preference, but the process (and with it the
     * foreground service) can die after an update, a crash or an OEM battery kill.
     * Re-start the service whenever monitoring is supposed to be on and it is not.
     */
    private fun ensureMonitoringRunning() {
        if (!alertSettings.isServiceEnabled || CallMonitorService.getInstance() != null) return
        try {
            CallMonitorService.start(this)
            Timber.tag(TAG).i("Monitoring service was not running; restarted from stored preference.")
        } catch (e: Exception) {
            Timber.tag(TAG).w("Could not restart monitoring service: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        home.onPause()
    }

    companion object {
        private const val TAG = "CoreAlert"
    }
}
