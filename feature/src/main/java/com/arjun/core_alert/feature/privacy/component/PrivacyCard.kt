package com.arjun.core_alert.feature.privacy.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.theme.coreAlertColors

@Composable
internal fun PrivacyCard(icon: Int, title: String, chip: String, body: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(iconRes = icon, title = title)
            OkChip(
                label = chip,
                icon = R.drawable.ic_check,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = linkedBody(body),
                modifier = Modifier.padding(top = 10.dp),
                color = coreAlertColors().inkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

private val linkPattern = Regex(
    "(?:https?://\\S+|github\\.com/\\S+|[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}|\\+\\d[\\d ]{6,}\\d)"
)

@Composable
private fun linkedBody(body: String): AnnotatedString {
    val styles = TextLinkStyles(
        SpanStyle(
            color = coreAlertColors().accent,
            textDecoration = TextDecoration.Underline
        )
    )
    return buildAnnotatedString {
        append(body)
        linkPattern.findAll(body).forEach { match ->
            val label = match.value.trimEnd('.', ',', ';', ':', ')', ' ', '\n')
            if (label.isEmpty()) return@forEach
            val url = when {
                label.startsWith("http") -> label
                label.startsWith("github.com") -> "https://$label"
                label.contains('@') -> "mailto:$label"
                else -> "tel:${label.filter { it.isDigit() || it == '+' }}"
            }
            addLink(LinkAnnotation.Url(url, styles), match.range.first, match.range.first + label.length)
        }
    }
}

@Composable
private fun OkChip(label: String, icon: Int, modifier: Modifier = Modifier) {
    val colors = coreAlertColors()
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.accent
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
