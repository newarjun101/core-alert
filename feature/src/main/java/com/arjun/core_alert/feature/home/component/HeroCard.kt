package com.arjun.core_alert.feature.home.component

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.theme.coreAlertColors

@Composable
internal fun HeroCard(active: Boolean, onServiceToggled: (Boolean) -> Unit) {
    val colors = coreAlertColors()
    val heroBg = if (active) colors.heroBg else colors.heroOffBg
    val stateColor = if (active) colors.heroOn else colors.heroOffOn
    val labelColor = if (active) colors.heroFaded else colors.heroOffMuted

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(heroBg)
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x00000000), Color(0x33000000))
                        )
                    )
            )
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroBell(active = active)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_monitoring_label),
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = stringResource(
                            if (active) R.string.home_service_state_on else R.string.home_service_state_off
                        ),
                        color = stateColor,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 16.sp)
                    )
                    if (!active) {
                        Text(
                            text = stringResource(R.string.home_service_hint_off),
                            modifier = Modifier.padding(top = 6.dp),
                            color = colors.heroOffMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp
                        )
                    }
                }
                Switch(
                    checked = active,
                    onCheckedChange = { onServiceToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0x8AFFFFFF),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color(0xFFC7CEF5),
                        uncheckedTrackColor = colors.outlineStrong,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            if (active) {
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeroChip(stringResource(R.string.hero_silent))
                    HeroChip(stringResource(R.string.hero_vibrate))
                    HeroChip(stringResource(R.string.hero_dnd))
                }
            }
        }
    }
}

@Composable
private fun HeroBell(active: Boolean) {
    val colors = coreAlertColors()
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val progress2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1600, StartOffsetType.Delay)
        ),
        label = "ring2"
    )

    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPulseRing(progress1)
                drawPulseRing(progress2)
            }
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color(0x29FFFFFF), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (active) Color.White else colors.heroOffBadgeBg,
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (active) R.drawable.ic_bell else R.drawable.ic_bell_off),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (active) colors.heroBg else colors.heroOffBadgeIcon
                )
            }
        }
    }
}

private fun DrawScope.drawPulseRing(progress: Float) {
    val scale = 1.15f + (1.9f - 1.15f) * progress
    val alpha = 0.6f * (1f - progress)
    val radius = size.minDimension / 2f * 0.62f * scale
    val stroke = 0.9f * (size.width / 24f)
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = radius,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}

@Composable
private fun HeroChip(text: String) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(Color(0x24FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Color.White
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
