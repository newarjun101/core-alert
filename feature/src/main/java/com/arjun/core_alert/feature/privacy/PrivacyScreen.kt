package com.arjun.core_alert.feature.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.feature.privacy.component.PrivacyCard

@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp)
    ) {
        PrivacyCard(
            icon = R.drawable.ic_shield,
            title = stringResource(R.string.privacy_data_title),
            chip = stringResource(R.string.privacy_chip_no_tracking),
            body = stringResource(R.string.privacy_data_body)
        )
        Spacer(Modifier.height(12.dp))
        PrivacyCard(
            icon = R.drawable.ic_lock,
            title = stringResource(R.string.privacy_license_title),
            chip = stringResource(R.string.privacy_chip_encrypted),
            body = stringResource(R.string.privacy_license_body)
        )
        Spacer(Modifier.height(12.dp))
        PrivacyCard(
            icon = R.drawable.ic_user,
            title = stringResource(R.string.privacy_contact_title),
            chip = stringResource(R.string.privacy_chip_no_ads),
            body = stringResource(R.string.privacy_contact_body)
        )
    }
}
