package com.gendev.tiktokviewer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gendev.tiktokviewer.ui.theme.AmberRateLimit
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBg
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DarkGlassStrong
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import com.gendev.tiktokviewer.ui.theme.DeepPlum
import com.gendev.tiktokviewer.ui.theme.ElectricPurple
import com.gendev.tiktokviewer.ui.theme.EmeraldHealthy
import com.gendev.tiktokviewer.ui.theme.MutedLavender
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import com.gendev.tiktokviewer.ui.theme.PureWhite
import com.gendev.tiktokviewer.ui.theme.RoseRestricted
import com.gendev.tiktokviewer.ui.theme.UltravioletGlow

fun Modifier.ambientObsidianAura(): Modifier = this.drawBehind {
    drawRect(AmoledBlack)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ElectricPurple.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height * 0.18f),
            radius = size.maxDimension * 0.55f
        ),
        radius = size.maxDimension * 0.55f,
        center = Offset(size.width * 0.2f, size.height * 0.18f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(UltravioletGlow.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.78f),
            radius = size.maxDimension * 0.5f
        ),
        radius = size.maxDimension * 0.5f,
        center = Offset(size.width * 0.85f, size.height * 0.78f)
    )
}

@Composable
fun GendevLogoBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DarkGlassBg)
            .border(1.dp, DarkGlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonViolet, ElectricPurple)
                    )
                )
        )
        Text(
            text = "GENDEV",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
        Text(
            text = "ANON",
            style = MaterialTheme.typography.labelSmall,
            color = NeonViolet,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(DeepPlum)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SourceIndicatorPill(
    label: String,
    isHealthy: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isHealthy) EmeraldHealthy else AmberRateLimit
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = DarkGlassBg,
        border = BorderStroke(1.dp, DarkGlassSubtleBorder),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("source_indicator_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 125.dp)
            )
        }
    }
}

@Composable
fun EngineModeSwitcher(
    isGuestScroll: Boolean,
    onSelectGuestScroll: () -> Unit,
    onSelectNativeStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DarkGlassBg)
            .border(1.dp, DarkGlassSubtleBorder, RoundedCornerShape(50))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            onClick = onSelectGuestScroll,
            shape = RoundedCornerShape(50),
            color = if (isGuestScroll) ElectricPurple else Color.Transparent,
            modifier = Modifier.testTag("mode_guest_scroll_button")
        ) {
            Text(
                text = "Scroll",
                style = MaterialTheme.typography.labelMedium,
                color = if (isGuestScroll) PureWhite else MutedLavender,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
        Surface(
            onClick = onSelectNativeStream,
            shape = RoundedCornerShape(50),
            color = if (!isGuestScroll) ElectricPurple else Color.Transparent,
            modifier = Modifier.testTag("mode_native_stream_button")
        ) {
            Text(
                text = "Stream",
                style = MaterialTheme.typography.labelMedium,
                color = if (!isGuestScroll) PureWhite else MutedLavender,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTagName: String,
    isHighlighted: Boolean = false,
    sizeDp: Int = 48,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isHighlighted) DeepPlum.copy(alpha = 0.90f) else DarkGlassBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) NeonViolet else DarkGlassSubtleBorder
        ),
        modifier = modifier
            .size(sizeDp.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTagName)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isHighlighted) NeonViolet else PureWhite,
                modifier = Modifier.size((sizeDp * 0.45f).dp)
            )
        }
    }
}

@Composable
fun GendevPulseLoader(
    label: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "gendev_pulse")
    val pulseA by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_a"
    )
    val pulseB by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_b"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(84.dp)) {
                val maxRadius = size.minDimension / 2f
                drawCircle(
                    color = ElectricPurple.copy(alpha = (1f - pulseA) * 0.65f),
                    radius = maxRadius * pulseA,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = NeonViolet.copy(alpha = (1f - pulseB) * 0.5f),
                    radius = maxRadius * pulseB,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(NeonViolet, UltravioletGlow)),
                    radius = 12.dp.toPx()
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedLavender,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GendevInitialHeroLoader(
    sourceHint: String,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = DarkGlassStrong,
        border = BorderStroke(1.dp, DarkGlassBorder),
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GendevPulseLoader(label = sourceHint)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DeepPlum.copy(alpha = 0.7f))
                    .border(1.dp, DarkGlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = NeonViolet,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Anonymous Mode · Zero Login · Zero History",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite
                )
            }

            OutlinedButton(
                onClick = onOpenSearch,
                border = BorderStroke(1.dp, DarkGlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = NeonViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Paste TikTok Link or #Tag", color = PureWhite)
            }
        }
    }
}

@Composable
fun ResilientErrorCard(
    title: String,
    message: String,
    isCaptcha: Boolean,
    isRateLimited: Boolean,
    isForbidden: Boolean,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onOpenGuestScroll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        isCaptcha || isForbidden -> RoseRestricted
        isRateLimited -> AmberRateLimit
        else -> ElectricPurple
    }
    val statusIcon = when {
        isCaptcha -> Icons.Rounded.Security
        isRateLimited -> Icons.Rounded.Timer
        isForbidden -> Icons.Rounded.Block
        else -> Icons.Rounded.WifiOff
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = DarkGlassStrong,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("resilient_error_card")
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedLavender,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSwitchSource,
                    border = BorderStroke(1.dp, DarkGlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("switch_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = NeonViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Switch", color = PureWhite)
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricPurple,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            if (onOpenGuestScroll != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenGuestScroll,
                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("fallback_guest_scroll_button")
                ) {
                    Text("Buka Mode Scroll Tanpa Login", color = NeonViolet)
                }
            }
        }
    }
}
