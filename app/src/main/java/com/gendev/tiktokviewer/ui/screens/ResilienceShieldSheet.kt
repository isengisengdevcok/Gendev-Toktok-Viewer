package com.gendev.tiktokviewer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.gendev.tiktokviewer.domain.model.CircuitState
import com.gendev.tiktokviewer.domain.model.EndpointHealth
import com.gendev.tiktokviewer.domain.model.PrivacyConfig
import com.gendev.tiktokviewer.ui.theme.AmberRateLimit
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBg
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import com.gendev.tiktokviewer.ui.theme.DeepPlum
import com.gendev.tiktokviewer.ui.theme.ElectricPurple
import com.gendev.tiktokviewer.ui.theme.EmeraldHealthy
import com.gendev.tiktokviewer.ui.theme.MutedLavender
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import com.gendev.tiktokviewer.ui.theme.ObsidianElevated
import com.gendev.tiktokviewer.ui.theme.PureWhite
import com.gendev.tiktokviewer.ui.theme.RoseRestricted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResilienceShieldSheet(
    endpointHealth: List<EndpointHealth>,
    privacyConfig: PrivacyConfig,
    cacheCount: Int,
    bannerMessage: String?,
    onClose: () -> Unit,
    onRunHealthChecks: () -> Unit,
    onRotateSource: () -> Unit,
    onResetBreakers: () -> Unit,
    onWipeCache: () -> Unit,
    onSaveSelfHostedUrl: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    var customUrlInput by remember(privacyConfig.customProxiTokBaseUrl) {
        mutableStateOf(privacyConfig.customProxiTokBaseUrl)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack.copy(alpha = 0.97f))
            .safeDrawingPadding()
            .testTag("resilience_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("resilience_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                            tint = PureWhite
                        )
                    }
                    Column {
                        Text(
                            text = "Privacy & Resilience Shield",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PureWhite
                        )
                        Text(
                            text = "Anonymous Mode · Zero Account · Circuit Breaker",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonViolet
                        )
                    }
                }
            }

            if (!bannerMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = bannerMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldHealthy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldHealthy.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldHealthy.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            // Privacy & Cache Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianElevated,
                    border = BorderStroke(1.dp, DarkGlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = NeonViolet
                            )
                            Text(
                                text = "Zero-Tracking Guarantees",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                        }
                        Text(
                            text = "• No login, cookies, sessionid, analytics, or device IDs\n" +
                                "• HTTPS-only transport with tracking query stripping\n" +
                                "• No watch history or search history stored\n" +
                                "• Ephemeral RAM metadata entries: $cacheCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedLavender
                        )
                        Button(
                            onClick = onWipeCache,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepPlum,
                                contentColor = NeonViolet
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("wipe_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.clear_cache_action))
                        }
                    }
                }
            }

            // Self-Hosted ProxiTok Adapter Configuration
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianElevated,
                    border = BorderStroke(1.dp, DarkGlassSubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Self-Hosted ProxiTok Instance (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite
                        )
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = {
                                Text("https://proxitok.yourdomain.example", color = MutedLavender)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricPurple,
                                unfocusedBorderColor = DarkGlassBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("self_hosted_input")
                        )
                        Button(
                            onClick = { onSaveSelfHostedUrl(customUrlInput) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricPurple,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("save_self_hosted_button")
                        ) {
                            Text("Apply HTTPS Proxy Instance")
                        }
                    }
                }
            }

            // Resilience Controls
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onRunHealthChecks,
                        border = BorderStroke(1.dp, DarkGlassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("health_check_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            tint = EmeraldHealthy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Health Check", color = PureWhite)
                    }

                    OutlinedButton(
                        onClick = onRotateSource,
                        border = BorderStroke(1.dp, DarkGlassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rotate_source_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate Source", color = PureWhite)
                    }

                    OutlinedButton(
                        onClick = onResetBreakers,
                        border = BorderStroke(1.dp, DarkGlassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_breakers_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = AmberRateLimit,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Breakers", color = PureWhite)
                    }
                }
            }

            item {
                Text(
                    text = "Hybrid Multi-Source Endpoints (${endpointHealth.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite
                )
            }

            items(endpointHealth, key = { it.endpointId }) { health ->
                EndpointHealthCard(health)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EndpointHealthCard(health: EndpointHealth) {
    val stateColor = when {
        health.rateLimitedUntilEpochMs != null -> AmberRateLimit
        health.circuitState == CircuitState.OPEN -> RoseRestricted
        health.circuitState == CircuitState.HALF_OPEN -> AmberRateLimit
        else -> EmeraldHealthy
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkGlassBg,
        border = BorderStroke(1.dp, DarkGlassSubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("endpoint_health_${health.endpointId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
                Column {
                    Text(
                        text = health.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${health.sourceKind.displayName} · ${health.lastStatusMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedLavender,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = health.circuitState.name,
                style = MaterialTheme.typography.labelSmall,
                color = stateColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(stateColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
