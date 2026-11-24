package com.gendev.tiktokviewer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.SearchMode
import com.gendev.tiktokviewer.ui.components.GendevPulseLoader
import com.gendev.tiktokviewer.ui.components.ResilientErrorCard
import com.gendev.tiktokviewer.ui.theme.AmoledBlack
import com.gendev.tiktokviewer.ui.theme.DarkGlassBg
import com.gendev.tiktokviewer.ui.theme.DarkGlassBorder
import com.gendev.tiktokviewer.ui.theme.DarkGlassSubtleBorder
import com.gendev.tiktokviewer.ui.theme.DeepPlum
import com.gendev.tiktokviewer.ui.theme.ElectricPurple
import com.gendev.tiktokviewer.ui.theme.MutedLavender
import com.gendev.tiktokviewer.ui.theme.NeonViolet
import com.gendev.tiktokviewer.ui.theme.ObsidianElevated
import com.gendev.tiktokviewer.ui.theme.PureWhite
import com.gendev.tiktokviewer.ui.viewmodel.SearchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicSearchSheet(
    searchState: SearchUiState,
    onClose: () -> Unit,
    onSearch: (query: String, mode: SearchMode) -> Unit,
    onSelectVideo: (PublicVideo, List<PublicVideo>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    var queryText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(SearchMode.AUTO) }

    val quickTopics = listOf("#nature", "#tech", "#art", "@nasa", "@tiktok", "@natgeo")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack.copy(alpha = 0.96f))
            .safeDrawingPadding()
            .testTag("search_screen")
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        .testTag("search_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = PureWhite
                    )
                }
                Text(
                    text = stringResource(R.string.search_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = MutedLavender,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = NeonViolet
                    )
                },
                trailingIcon = {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = { queryText = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_cd),
                                tint = MutedLavender
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(queryText, selectedMode) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricPurple,
                    unfocusedBorderColor = DarkGlassBorder,
                    focusedContainerColor = ObsidianElevated,
                    unfocusedContainerColor = DarkGlassBg,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    cursorColor = NeonViolet
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SearchMode.entries) { mode ->
                    val active = selectedMode == mode
                    FilterChip(
                        selected = active,
                        onClick = { selectedMode = mode },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepPlum,
                            selectedLabelColor = NeonViolet,
                            containerColor = DarkGlassBg,
                            labelColor = MutedLavender
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = active,
                            borderColor = DarkGlassSubtleBorder,
                            selectedBorderColor = ElectricPurple
                        ),
                        modifier = Modifier.testTag("search_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ephemeral privacy guarantee strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepPlum.copy(alpha = 0.45f))
                    .border(1.dp, DarkGlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = NeonViolet,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Anonymous Discovery · Searches are never stored or tracked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedLavender
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (searchState) {
                is SearchUiState.Idle -> {
                    Text(
                        text = "Public Topics & Creators",
                        style = MaterialTheme.typography.titleSmall,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTopics.forEach { topic ->
                            Surface(
                                onClick = {
                                    queryText = topic
                                    val mode = if (topic.startsWith("#")) SearchMode.HASHTAG else SearchMode.CREATOR
                                    selectedMode = mode
                                    onSearch(topic, mode)
                                },
                                shape = RoundedCornerShape(50),
                                color = ObsidianElevated,
                                border = BorderStroke(1.dp, DarkGlassBorder),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonViolet,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                is SearchUiState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GendevPulseLoader(label = "Querying public endpoints for \"${searchState.query}\"...")
                    }
                }

                is SearchUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ResilientErrorCard(
                            title = searchState.title,
                            message = searchState.message,
                            isCaptcha = searchState.isCaptcha,
                            isRateLimited = searchState.isRateLimited,
                            isForbidden = false,
                            onRetry = { onSearch(queryText, selectedMode) },
                            onSwitchSource = { onSearch(queryText, selectedMode) }
                        )
                    }
                }

                is SearchUiState.Results -> {
                    Text(
                        text = "${searchState.videos.size} public clips via ${searchState.sourceLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedLavender
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_list")
                    ) {
                        items(searchState.videos, key = { it.id }) { video ->
                            SearchResultCard(
                                video = video,
                                onClick = { onSelectVideo(video, searchState.videos) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    video: PublicVideo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ObsidianElevated,
        border = BorderStroke(1.dp, DarkGlassSubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("search_result_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepPlum),
                contentAlignment = Alignment.Center
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.thumbnailUrl)
                            .diskCachePolicy(CachePolicy.DISABLED) // Zero disk persistence
                            .crossfade(true)
                            .build(),
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = PureWhite.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AmoledBlack.copy(alpha = 0.45f))
                        .padding(2.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "@${video.authorHandle}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NeonViolet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.sourceLabel} · ${video.playbackMode.badgeText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedLavender,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
