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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.gendev.tiktokviewer.ai.AiVibeState
import com.gendev.tiktokviewer.ai.AiVibeSuggestion
import com.gendev.tiktokviewer.ai.AiVideoInsightState
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBg
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DeepPlum
import com.gendev.tiktokviewer.ui.theme.ElectricPurple
import com.gendev.tiktokviewer.ui.theme.MutedLavender
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import com.gendev.tiktokviewer.ui.theme.ObsidianElevated
import com.gendev.tiktokviewer.ui.theme.PureWhite
import com.gendev.tiktokviewer.ui.theme.RoseRestricted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantSheet(
    aiVibeState: AiVibeState,
    aiInsightState: AiVideoInsightState,
    currentVideo: PublicVideo?,
    onClose: () -> Unit,
    onAskVibe: (String) -> Unit,
    onApplyVibeToGuestScroll: (AiVibeSuggestion) -> Unit,
    onApplyVibeToStream: (AiVibeSuggestion) -> Unit,
    onSummarizeCurrentVideo: (PublicVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    var moodPrompt by remember { mutableStateOf("") }
    val quickMoods = listOf(
        "Kucing lucu & santai",
        "Resep simpel anak kos",
        "Pemandangan alam tenang",
        "Tips teknologi & gadget"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack.copy(alpha = 0.96f))
            .safeDrawingPadding()
            .testTag("ai_assistant_sheet")
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ai_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = PureWhite
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = NeonViolet
                )
                Text(
                    text = "AI Smart Scroll · Gemini",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PureWhite
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ObsidianElevated,
                border = BorderStroke(1.dp, DarkGlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cari tontonan pakai bahasa santai",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    Text(
                        text = "Tanpa login & tanpa riwayat tersimpan. Tulis suasana atau topik yang ingin Anda scroll.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedLavender
                    )

                    OutlinedTextField(
                        value = moodPrompt,
                        onValueChange = { moodPrompt = it },
                        placeholder = {
                            Text(
                                text = "Contoh: video alam tenang atau masak praktis",
                                color = MutedLavender,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (moodPrompt.isNotEmpty()) {
                                IconButton(onClick = { moodPrompt = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.clear_cd),
                                        tint = MutedLavender
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (moodPrompt.isNotBlank()) onAskVibe(moodPrompt)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricPurple,
                            unfocusedBorderColor = DarkGlassBorder,
                            focusedContainerColor = DarkGlassBg,
                            unfocusedContainerColor = DarkGlassBg,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            cursorColor = NeonViolet
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_vibe_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickMoods.forEach { mood ->
                            Surface(
                                onClick = {
                                    moodPrompt = mood
                                    onAskVibe(mood)
                                },
                                shape = RoundedCornerShape(50),
                                color = DeepPlum.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, DarkGlassBorder)
                            ) {
                                Text(
                                    text = mood,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonViolet,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onAskVibe(moodPrompt) },
                        enabled = moodPrompt.isNotBlank() && aiVibeState !is AiVibeState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricPurple,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("ai_vibe_submit_button")
                    ) {
                        if (aiVibeState is AiVibeState.Loading) {
                            CircularProgressIndicator(
                                color = PureWhite,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Meracik feed dengan Gemini...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Temukan Feed dengan AI")
                        }
                    }

                    when (aiVibeState) {
                        is AiVibeState.Ready -> {
                            val suggestion = aiVibeState.suggestion
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DeepPlum.copy(alpha = 0.55f))
                                    .border(1.dp, ElectricPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = suggestion.vibeLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite
                                )
                                Text(
                                    text = suggestion.hashtags.joinToString(" ") { "#$it" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonViolet
                                )
                                Button(
                                    onClick = { onApplyVibeToStream(suggestion) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = PureWhite
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_apply_stream_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buka Stream Lancar")
                                }
                                OutlinedButton(
                                    onClick = { onApplyVibeToGuestScroll(suggestion) },
                                    border = BorderStroke(1.dp, DarkGlassBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_apply_guest_button")
                                ) {
                                    Text("Scroll di TikTok Web", color = NeonViolet)
                                }
                            }
                        }

                        is AiVibeState.Error -> {
                            Text(
                                text = aiVibeState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = RoseRestricted
                            )
                        }

                        else -> Unit
                    }
                }
            }

            if (currentVideo != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianElevated,
                    border = BorderStroke(1.dp, DarkGlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ringkas & Terjemahkan Video Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite
                        )
                        Text(
                            text = "@${currentVideo.authorHandle}: ${currentVideo.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedLavender,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        OutlinedButton(
                            onClick = { onSummarizeCurrentVideo(currentVideo) },
                            enabled = aiInsightState !is AiVideoInsightState.Loading &&
                                aiInsightState !is AiVideoInsightState.Streaming,
                            border = BorderStroke(1.dp, ElectricPurple),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("ai_summarize_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Summarize,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ringkas Caption Pakai Gemini", color = PureWhite)
                        }

                        when (aiInsightState) {
                            is AiVideoInsightState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NeonViolet,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Menganalisis caption...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedLavender
                                    )
                                }
                            }

                            is AiVideoInsightState.Streaming -> {
                                Text(
                                    text = aiInsightState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite
                                )
                            }

                            is AiVideoInsightState.Ready -> {
                                Text(
                                    text = aiInsightState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite
                                )
                            }

                            is AiVideoInsightState.Error -> {
                                Text(
                                    text = aiInsightState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoseRestricted
                                )
                            }

                            AiVideoInsightState.Idle -> Unit
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
