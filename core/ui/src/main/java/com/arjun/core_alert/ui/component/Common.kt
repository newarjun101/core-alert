package com.arjun.core_alert.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.ui.theme.CoreAlertColors
import com.arjun.core_alert.ui.theme.coreAlertColors

/** Uppercase overline used above grouped controls. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = coreAlertColors().inkSecondary) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
    )
}

/** Standard surface card (hairline border, no elevation, 20 dp corners). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = coreAlertColors().outline,
    shape: Shape = RoundedCornerShape(20.dp),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(0.dp),
        content = content
    )
}

/** Rounded icon badge used at the head of every card / permission row. */
@Composable
fun IconBadge(
    iconRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    padding: Dp = 10.dp,
    tint: Color = coreAlertColors().accent,
    background: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(size.value * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(padding * 2.2f),
            tint = tint
        )
    }
}

/** Icon badge + title (+ optional subtitle) used by every settings card. */
@Composable
fun CardHeader(
    iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = coreAlertColors().accent,
    iconSize: Dp = 42.dp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(iconRes = iconRes, tint = tint, size = iconSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = coreAlertColors().ink
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Soft pill used for small numeric highlights (percentages, empty-state hint). */
@Composable
fun SoftPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Green/red status line used by the permission rows. */
@Composable
fun StatusText(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val colors = coreAlertColors()
    Text(
        text = text,
        modifier = modifier,
        color = if (ok) colors.statusOk else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

/** Vertical stack with the standard 12 dp rhythm. */
@Composable
fun CardContent(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.padding(padding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** Single line of secondary body copy. */
@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        overflow = TextOverflow.Ellipsis
    )
}

/** Section title used above grouped controls inside a card. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Full-width helper spacer. */
@Composable
fun VGap(height: Dp) = Spacer(Modifier.height(height))

/** Trailing value chip (e.g. "100%"). */
@Composable
fun ValueBadge(text: String) {
    SoftPill(text = text, modifier = Modifier.fillMaxWidth(0f))
}


/** Radio row used by the sound / call-mode groups. */
@Composable
fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
