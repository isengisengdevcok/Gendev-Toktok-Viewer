package com.gendev.tiktokviewer.ai

import android.util.Log
import com.gendev.tiktokviewer.domain.model.PublicVideo
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GendevGeminiAI"
private const val AI_TIMEOUT_MS = 45_000L

data class AiVibeSuggestion(
    val vibeLabel: String,
    val searchQuery: String,
    val hashtags: List<String>,
    val guestTopicTag: String
)

sealed interface AiVibeState {
    data object Idle : AiVibeState
    data class Loading(val prompt: String) : AiVibeState
    data class Ready(val suggestion: AiVibeSuggestion) : AiVibeState
    data class Error(val message: String) : AiVibeState
}

sealed interface AiVideoInsightState {
    data object Idle : AiVideoInsightState
    data object Loading : AiVideoInsightState
    data class Streaming(val text: String) : AiVideoInsightState
    data class Ready(val text: String) : AiVideoInsightState
    data class Error(val message: String) : AiVideoInsightState
}

interface GeminiScrollAssistant {
    suspend fun buildVibeDiscovery(userMoodOrTopic: String): AiVibeSuggestion
    fun streamVideoInsight(video: PublicVideo): Flow<AiVideoInsightState>
}

/**
 * Stateless Google Gemini assistant powered by Firebase AI Logic (`gemini-3.8-flash`).
 * Never stores user prompts, watch history, or device identifiers.
 */
class FirebaseGeminiScrollAssistant : GeminiScrollAssistant {

    private val vibeSchema = Schema.obj(
        properties = mapOf(
            "vibeLabel" to Schema.string(
                description = "Short friendly Indonesian title describing this scroll feed (2-5 words)"
            ),
            "searchQuery" to Schema.string(
                description = "Concise public search query (1-3 words) for TikTok public discovery"
            ),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "3 clean hashtags without # symbol relevant to the user mood"
            ),
            "guestTopicTag" to Schema.string(
                description = "Single clean alphanumeric hashtag without # symbol for public tag URL"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = vibeSchema
            }
        )
    }

    private val insightModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    override suspend fun buildVibeDiscovery(userMoodOrTopic: String): AiVibeSuggestion {
        val cleanPrompt = userMoodOrTopic.trim()
        require(cleanPrompt.isNotEmpty()) { "Masukkan topik atau suasana yang ingin ditonton." }

        val instruction = """
            Kamu adalah kurator feed video publik TikTok anonim untuk pengguna Indonesia.
            Pengguna ingin scroll video tanpa login dengan topik/mood: "$cleanPrompt".
            Hasilkan JSON dengan:
            - vibeLabel: label singkat Bahasa Indonesia (contoh: "Kucing Lucu & Santai")
            - searchQuery: kata kunci pencarian singkat yang efektif menemukan video publik (1-3 kata)
            - hashtags: tepat 3 hashtag pendek tanpa tanda # (contoh: ["kucinglucu", "catsoftiktok", "hewan"])
            - guestTopicTag: 1 tag utama tanpa spasi dan tanpa # untuk halaman tag publik.
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                structuredModel.generateContent(instruction)
            }
            val rawJson = response.text ?: throw IllegalStateException("Gemini mengembalikan respons kosong.")
            val json = JSONObject(rawJson)
            val label = json.optString("vibeLabel").ifBlank { cleanPrompt }
            val query = json.optString("searchQuery").ifBlank { cleanPrompt }
            val tagsArray = json.optJSONArray("hashtags")
            val tags = buildList {
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) {
                        val cleaned = tagsArray.optString(i).trim().removePrefix("#")
                            .replace(Regex("[^A-Za-z0-9_]"), "")
                        if (cleaned.isNotBlank()) add(cleaned)
                    }
                }
            }.ifEmpty {
                listOf(cleanPrompt.replace(Regex("[^A-Za-z0-9_]"), "").ifBlank { "fyp" })
            }
            val guestTag = json.optString("guestTopicTag")
                .trim()
                .removePrefix("#")
                .replace(Regex("[^A-Za-z0-9_]"), "")
                .ifBlank { tags.first() }

            AiVibeSuggestion(
                vibeLabel = label,
                searchQuery = query,
                hashtags = tags.take(3),
                guestTopicTag = guestTag
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Vibe discovery timed out", e)
            throw IllegalStateException("Permintaan AI melebihi batas waktu 45 detik. Coba lagi.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Vibe discovery failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gagal menghubungi Gemini AI.", e)
        }
    }

    override fun streamVideoInsight(video: PublicVideo): Flow<AiVideoInsightState> = flow {
        emit(AiVideoInsightState.Loading)
        val prompt = """
            Ringkas dan terjemahkan ke Bahasa Indonesia secara singkat (maksimal 2 kalimat santai) isi/konteks video publik berikut tanpa menambah informasi yang tidak ada:
            Kreator: @${video.authorHandle}
            Caption: "${video.title}"
            Hashtag: ${video.hashtags.joinToString(" ") { "#$it" }}
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                insightModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiVideoInsightState.Streaming(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isNotBlank()) {
                emit(AiVideoInsightState.Ready(finalText))
            } else {
                emit(AiVideoInsightState.Error("Gemini tidak mengembalikan ringkasan untuk video ini."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI insight streaming timed out", e)
            emit(AiVideoInsightState.Error("Ringkasan AI timeout setelah 45 detik."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI insight streaming failed", e)
            emit(AiVideoInsightState.Error(e.localizedMessage ?: "Gagal memuat ringkasan Gemini AI."))
        }
    }
}
