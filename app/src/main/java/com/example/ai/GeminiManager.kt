package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GroundingSource(
    val title: String,
    val url: String
)

data class GroundedResponse(
    val text: String,
    val sources: List<GroundingSource> = emptyList()
)

data class ImageGenerationResult(
    val success: Boolean,
    val base64Data: String? = null,
    val mimeType: String = "image/png",
    val textDescription: String? = null,
    val errorMessage: String? = null
)

data class VideoGenerationResult(
    val success: Boolean,
    val interactionId: String? = null,
    val videoBase64: String? = null,
    val errorMessage: String? = null
)

object GeminiManager {
    private const val TAG = "GeminiManager"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Multi-turn chat with Luxury Salon AI Concierge
     * Model: gemini-3.8-flash for general chat / gemini-4-argon for deep consultations
     */
    suspend fun chatWithConcierge(
        history: List<ChatMessage>,
        userMessage: String,
        useArgon: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Set GEMINI_API_KEY in the Secrets panel."))
        }

        val model = if (useArgon) "gemini-4-argon" else "gemini-3.8-flash"
        val url = "$BASE_URL/models/$model:generateContent?key=$apiKey"

        try {
            val root = JSONObject()

            // System Instruction
            val sysInstruction = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are SalonSphere Concierge, an elite salon management and beauty styling assistant. You help salon owners, stylists, and clients with booking advice, hairstyle recommendations, hair color chemistry, appointment scheduling strategies, aftercare routines, and salon operations.")
                    })
                }
                put("parts", parts)
            }
            root.put("systemInstruction", sysInstruction)

            // Conversation history
            val contents = JSONArray()
            for (msg in history) {
                val item = JSONObject().apply {
                    put("role", if (msg.role == "model") "model" else "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", msg.text))
                    }
                    put("parts", parts)
                }
                contents.put(item)
            }
            // Append current user message
            val currentTurn = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray().apply {
                    put(JSONObject().put("text", userMessage))
                }
                put("parts", parts)
            }
            contents.put(currentTurn)
            root.put("contents", contents)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat failed: $responseString")
                return@withContext Result.failure(Exception("API Error (${response.code}): $responseString"))
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text", "") ?: "No response from AI"

            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Chat error", e)
            Result.failure(e)
        }
    }

    /**
     * Search Grounding using gemini-3.8-flash with googleSearch tool
     * For trending hairstyle, beauty, and salon techniques
     */
    suspend fun searchBeautyTrends(query: String): Result<GroundedResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val url = "$BASE_URL/models/gemini-3.8-flash:generateContent?key=$apiKey"
        try {
            val root = JSONObject()
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Provide current 2026 beauty and hairstyle trends or information for: $query. Include specific styling techniques, popular color palettes, and professional recommendations."))
                    })
                })
            }
            root.put("contents", contents)

            // Search Grounding Tool
            val tools = JSONArray().apply {
                put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            }
            root.put("tools", tools)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Search Error (${response.code}): $responseString"))
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val text = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""

            // Extract grounding sources
            val sources = mutableListOf<GroundingSource>()
            val groundingMetadata = firstCandidate?.optJSONObject("groundingMetadata")
            val searchChunks = groundingMetadata?.optJSONArray("groundingChunks")
            if (searchChunks != null) {
                for (i in 0 until searchChunks.length()) {
                    val web = searchChunks.optJSONObject(i)?.optJSONObject("web")
                    if (web != null) {
                        val title = web.optString("title", "Web Source")
                        val uri = web.optString("uri", "")
                        if (uri.isNotBlank()) {
                            sources.add(GroundingSource(title, uri))
                        }
                    }
                }
            }

            Result.success(GroundedResponse(text, sources))
        } catch (e: Exception) {
            Log.e(TAG, "Search Grounding error", e)
            Result.failure(e)
        }
    }

    /**
     * Maps Grounding using gemini-3.8-flash with googleMaps tool
     * For locating beauty supply distributors, cosmetics stores, or partner salons
     */
    suspend fun findNearbySuppliersOrSalons(locationOrQuery: String): Result<GroundedResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val url = "$BASE_URL/models/gemini-3.8-flash:generateContent?key=$apiKey"
        try {
            val root = JSONObject()
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Find professional beauty salons, cosmetics suppliers, and hair care stores near $locationOrQuery. Provide recommendations, addresses, specialties, and contact details."))
                    })
                })
            }
            root.put("contents", contents)

            // Maps Grounding Tool
            val tools = JSONArray().apply {
                put(JSONObject().apply {
                    put("googleMaps", JSONObject())
                })
            }
            root.put("tools", tools)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Maps Grounding Error (${response.code}): $responseString"))
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val text = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""

            val sources = mutableListOf<GroundingSource>()
            val groundingMetadata = firstCandidate?.optJSONObject("groundingMetadata")
            val webChunks = groundingMetadata?.optJSONArray("groundingChunks")
            if (webChunks != null) {
                for (i in 0 until webChunks.length()) {
                    val web = webChunks.optJSONObject(i)?.optJSONObject("web")
                    if (web != null) {
                        val title = web.optString("title", "Location")
                        val uri = web.optString("uri", "")
                        if (uri.isNotBlank()) {
                            sources.add(GroundingSource(title, uri))
                        }
                    }
                }
            }

            Result.success(GroundedResponse(text, sources))
        } catch (e: Exception) {
            Log.e(TAG, "Maps Grounding error", e)
            Result.failure(e)
        }
    }

    /**
     * High Thinking Mode using gemini-4-argon with thinkingLevel: HIGH
     * For complex schedule optimization, revenue analysis, and custom bridal styling coordination
     */
    suspend fun optimizeSalonOperations(
        prompt: String,
        bookingsSummary: String,
        staffSummary: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val url = "$BASE_URL/models/gemini-4-argon:generateContent?key=$apiKey"
        try {
            val root = JSONObject()
            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", "You are an expert Salon Operations Architect and Mathematical Scheduling Optimizer. You resolve complex staff booking conflicts, calculate optimal service turn-around times, project stylist commission revenue, and recommend actionable schedule improvements."))
                })
            }
            root.put("systemInstruction", systemInstruction)

            val fullPrompt = """
                Task: $prompt
                
                Current Bookings:
                $bookingsSummary
                
                Staff Roster & Working Hours:
                $staffSummary
                
                Perform a deep, step-by-step mathematical reasoning to solve this problem, optimize utilization, prevent stylist burnout, and maximize customer satisfaction.
            """.trimIndent()

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", fullPrompt))
                    })
                })
            }
            root.put("contents", contents)

            // Thinking Config with HIGH level (per mandate: do not set maxOutputTokens)
            val generationConfig = JSONObject().apply {
                val thinkingConfig = JSONObject().apply {
                    put("thinkingLevel", "HIGH")
                }
                put("thinkingConfig", thinkingConfig)
            }
            root.put("generationConfig", generationConfig)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Thinking Mode Error (${response.code}): $responseString"))
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")

            // Find model text output
            var resultText = ""
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i)
                    if (p != null && p.has("text")) {
                        resultText += p.optString("text") + "\n"
                    }
                }
            }

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Thinking Mode error", e)
            Result.failure(e)
        }
    }

    /**
     * Fast Quick-Action Helper with gemini-3.1-flash-lite
     * For rapid client message templates, SMS confirmations, or notes summarization
     */
    suspend fun quickFastReply(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val url = "$BASE_URL/models/gemini-3.1-flash-lite:generateContent?key=$apiKey"
        try {
            val root = JSONObject()
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Write a concise, polished salon text or message: $prompt"))
                    })
                })
            }
            root.put("contents", contents)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Fast Reply Error (${response.code}): $responseString"))
            }

            val respJson = JSONObject(responseString)
            val text = respJson.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "") ?: ""

            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Fast Reply error", e)
            Result.failure(e)
        }
    }

    /**
     * Create or edit styling images using gemini-nano-banana-2.1
     * Generates hairstyles, hair colors, makeup look previews, and nail art.
     */
    suspend fun generateOrEditStyleImage(
        prompt: String,
        baseImageB64: String? = null
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ImageGenerationResult(false, errorMessage = "Gemini API key is not configured.")
        }

        val url = "$BASE_URL/models/gemini-nano-banana-2.1:generateContent?key=$apiKey"
        try {
            val root = JSONObject()
            val parts = JSONArray()

            val fullPrompt = "High fashion beauty salon look, photorealistic 4K studio lighting, professional hair stylist masterpiece: $prompt"
            parts.put(JSONObject().put("text", fullPrompt))

            if (!baseImageB64.isNullOrBlank()) {
                parts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", baseImageB64)
                    })
                })
            }

            root.put("contents", JSONArray().apply {
                put(JSONObject().put("parts", parts))
            })

            // Image Generation Configuration
            val genConfig = JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
                put("imageConfig", JSONObject().apply {
                    put("aspectRatio", "1:1")
                    put("imageSize", "1K")
                })
            }
            root.put("generationConfig", genConfig)

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ImageGenerationResult(false, errorMessage = "Image Gen Error (${response.code}): $responseString")
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates")
            val candidateParts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")

            var extractedImageBase64: String? = null
            var extractedMime = "image/png"
            var extractedText: String? = null

            if (candidateParts != null) {
                for (i in 0 until candidateParts.length()) {
                    val p = candidateParts.optJSONObject(i) ?: continue
                    if (p.has("inlineData")) {
                        val inline = p.optJSONObject("inlineData")
                        extractedImageBase64 = inline?.optString("data")
                        extractedMime = inline?.optString("mimeType", "image/png") ?: "image/png"
                    }
                    if (p.has("text")) {
                        extractedText = p.optString("text")
                    }
                }
            }

            if (extractedImageBase64 != null) {
                ImageGenerationResult(
                    success = true,
                    base64Data = extractedImageBase64,
                    mimeType = extractedMime,
                    textDescription = extractedText
                )
            } else {
                ImageGenerationResult(
                    success = false,
                    textDescription = extractedText,
                    errorMessage = "Model returned response without image data."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image generation error", e)
            ImageGenerationResult(false, errorMessage = e.message ?: "Failed to generate image")
        }
    }

    /**
     * Animate image into video using gemini-omni-1.1-flash via Interactions API
     * Aspect ratio: 16:9 (landscape) or 9:16 (portrait)
     */
    suspend fun generateTransformationVideo(
        imageB64: String,
        prompt: String,
        aspectRatio: String = "9:16",
        onProgress: (String) -> Unit = {}
    ): VideoGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext VideoGenerationResult(false, errorMessage = "Gemini API key is not configured.")
        }

        try {
            onProgress("Submitting video animation request...")
            val createUrl = "$BASE_URL/interactions?key=$apiKey"

            val root = JSONObject().apply {
                put("model", "gemini-omni-1.1-flash")
                put("background", false)
                put("store", true)
                put("stream", false)

                val inputObj = JSONObject().apply {
                    put("text", "Cinematic 360-degree salon transformation video showcasing this client's hair styling and beauty look: $prompt")
                    put("image", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", imageB64)
                    })
                }
                put("input", inputObj.toString())

                put("response_format", JSONObject().apply {
                    put("type", "video")
                    put("aspect_ratio", if (aspectRatio == "16:9") "16:9" else "9:16")
                    put("duration", "5s")
                    put("resolution", "720p")
                    put("delivery", "inline")
                })
            }

            val body = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(createUrl).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext VideoGenerationResult(false, errorMessage = "Video Creation Error (${response.code}): $responseString")
            }

            val respJson = JSONObject(responseString)
            val interactionId = respJson.optString("id", "")
            var status = respJson.optString("status", "")

            onProgress("Rendering video (status: $status)...")

            // Poll interaction until completion or error
            var pollCount = 0
            var finalResponseJson = respJson

            while ((status == "in_progress" || status == "queued" || status.isBlank()) && pollCount < 30) {
                delay(4000L)
                pollCount++
                onProgress("Rendering salon video (${pollCount * 4}s)...")

                val getUrl = "$BASE_URL/interactions/$interactionId?key=$apiKey"
                val getReq = Request.Builder().url(getUrl).get().build()
                val getResp = okHttpClient.newCall(getReq).execute()
                val getRespStr = getResp.body?.string() ?: ""
                if (getResp.isSuccessful) {
                    finalResponseJson = JSONObject(getRespStr)
                    status = finalResponseJson.optString("status", "")
                }
            }

            val steps = finalResponseJson.optJSONArray("steps")
            var videoBase64: String? = null

            if (steps != null) {
                for (i in 0 until steps.length()) {
                    val step = steps.optJSONObject(i) ?: continue
                    val contentArr = step.optJSONArray("content") ?: continue
                    for (j in 0 until contentArr.length()) {
                        val content = contentArr.optJSONObject(j) ?: continue
                        if (content.optString("type") == "video" || content.optString("mime_type").startsWith("video")) {
                            videoBase64 = content.optString("data")
                            if (!videoBase64.isNullOrBlank()) break
                        }
                    }
                    if (!videoBase64.isNullOrBlank()) break
                }
            }

            VideoGenerationResult(
                success = true,
                interactionId = interactionId,
                videoBase64 = videoBase64
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video generation error", e)
            VideoGenerationResult(false, errorMessage = e.message ?: "Failed to generate video")
        }
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
