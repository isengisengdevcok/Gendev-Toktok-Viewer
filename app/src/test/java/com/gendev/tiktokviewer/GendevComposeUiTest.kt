package com.gendev.tiktokviewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.gendev.tiktokviewer.data.privacy.PrivacyPolicyEnforcer
import com.gendev.tiktokviewer.domain.model.PlaybackMode
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.gendev.tiktokviewer.domain.model.VideoSourceKind
import com.gendev.tiktokviewer.ui.components.ResilientErrorCard
import com.gendev.tiktokviewer.ui.screens.SimpleSettingsSheet
import com.gendev.tiktokviewer.ui.screens.ViewerFeedScreen
import com.gendev.tiktokviewer.ui.theme.GendevTheme
import com.gendev.tiktokviewer.ui.theme.GendevThemePreset
import com.gendev.tiktokviewer.ui.viewmodel.FeedUiState
import com.gendev.tiktokviewer.ui.viewmodel.ViewerEngineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class GendevComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleVideos = listOf(
        PublicVideo(
            id = "1001",
            title = "First public clip #nature",
            authorHandle = "creator_one",
            authorDisplayName = "@creator_one",
            hashtags = listOf("nature"),
            thumbnailUrl = null,
            directMediaUrl = "https://cdn.example/1001.mp4",
            embedUrl = "https://www.tiktok.com/embed/v3/1001",
            canonicalUrl = "https://www.tiktok.com/@creator_one/video/1001",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            sourceLabel = "Public Stream · TikWM",
            playbackMode = PlaybackMode.EXO_DIRECT_STREAM
        ),
        PublicVideo(
            id = "1002",
            title = "Second public clip #tech",
            authorHandle = "creator_two",
            authorDisplayName = "@creator_two",
            hashtags = listOf("tech"),
            thumbnailUrl = null,
            directMediaUrl = "https://cdn.example/1002.mp4",
            embedUrl = "https://www.tiktok.com/embed/v3/1002",
            canonicalUrl = "https://www.tiktok.com/@creator_two/video/1002",
            sourceKind = VideoSourceKind.PROXITOK_PUBLIC,
            sourceLabel = "Public Stream · TikWM",
            playbackMode = PlaybackMode.EXO_DIRECT_STREAM
        )
    )

    @Test
    fun `3_vertical_swipe_clean_ui_play_pause_and_zero_social_buttons`() {
        var lastPage = 0
        var pausedState by mutableStateOf(false)
        var settingsOpened = false
        var refreshed = false

        composeTestRule.setContent {
            GendevTheme {
                ViewerFeedScreen(
                    feedState = FeedUiState.Ready(sampleVideos, "Public Stream · TikWM"),
                    currentIndex = 0,
                    isPaused = pausedState,
                    isMuted = false,
                    ultraCleanMode = false,
                    okHttpClient = PrivacyPolicyEnforcer.createPrivacyOkHttpClient(),
                    onPageChanged = { lastPage = it },
                    onTogglePlayPause = { pausedState = !pausedState },
                    onToggleMute = {},
                    onOpenSettings = { settingsOpened = true },
                    onRefreshFeed = { refreshed = true },
                    onReturnToUnifiedScroll = {},
                    onRetry = {},
                    onSwitchSource = {},
                    onFallbackToEmbed = { _, _ -> },
                    viewerEngineMode = ViewerEngineMode.NATIVE_STREAM
                )
            }
        }

        composeTestRule.waitForIdle()
        // Top clutter, tabs, caption blocks, and Scroll/Stream switchers must not be displayed
        composeTestRule.onNodeWithText("GENDEV").assertDoesNotExist()
        composeTestRule.onNodeWithText("Scroll").assertDoesNotExist()
        composeTestRule.onNodeWithText("Stream").assertDoesNotExist()
        composeTestRule.onNodeWithText("@creator_one").assertDoesNotExist()
        composeTestRule.onNodeWithTag("topic_pill_Trending").assertDoesNotExist()

        composeTestRule.onNodeWithTag("video_page_1001").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_feed_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mute_toggle_button").assertIsDisplayed()

        // Tap refresh button
        composeTestRule.onNodeWithTag("refresh_feed_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(refreshed)

        // Toggle Play/Pause button
        composeTestRule.onNodeWithTag("play_pause_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(pausedState)

        composeTestRule.onNodeWithTag("play_pause_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, pausedState)

        // Open simple settings sheet
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(settingsOpened)

        // Verify zero social buttons exist
        composeTestRule.onNodeWithText("Like").assertDoesNotExist()
        composeTestRule.onNodeWithText("Comment").assertDoesNotExist()
        composeTestRule.onNodeWithText("Follow").assertDoesNotExist()
        composeTestRule.onNodeWithText("Login").assertDoesNotExist()

        // Perform vertical swipe up
        composeTestRule.onNodeWithTag("vertical_video_pager").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        assertTrue(lastPage == 1)
    }

    @Test
    fun `simple_settings_sheet_switches_theme_and_ultra_clean_mode`() {
        var selectedTheme by mutableStateOf(GendevThemePreset.AMOLED_DARK)
        var ultraClean by mutableStateOf(false)

        composeTestRule.setContent {
            GendevTheme(preset = selectedTheme) {
                SimpleSettingsSheet(
                    currentTheme = selectedTheme,
                    ultraCleanMode = ultraClean,
                    bannerMessage = null,
                    onSelectTheme = { selectedTheme = it },
                    onToggleUltraCleanMode = { ultraClean = it },
                    onOpenSearch = {},
                    onOpenAiAssistant = {},
                    onReloadFeed = {},
                    onWipeCache = {},
                    onOpenAdvancedStatus = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_preset_emerald").performClick()
        composeTestRule.onNodeWithTag("ultra_clean_switch").performClick()
        composeTestRule.waitForIdle()

        assertEquals(GendevThemePreset.EMERALD_DARK, selectedTheme)
        assertTrue(ultraClean)
    }

    @Test
    fun `captcha_and_rate_limit_error_card_exposes_manual_retry_and_switch`() {
        var retried = false
        var switched = false
        composeTestRule.setContent {
            GendevTheme {
                ResilientErrorCard(
                    title = "Verification Required (CAPTCHA)",
                    message = "Upstream requested CAPTCHA verification.",
                    isCaptcha = true,
                    isRateLimited = false,
                    isForbidden = false,
                    onRetry = { retried = true },
                    onSwitchSource = { switched = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("resilient_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_button").performClick()
        composeTestRule.onNodeWithTag("switch_source_button").performClick()
        assertTrue(retried)
        assertTrue(switched)
    }
}
