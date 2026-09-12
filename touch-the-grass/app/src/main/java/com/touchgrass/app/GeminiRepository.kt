package com.touchgrass.app

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiRepository {

    private const val TAG = "GeminiRepository"
    private const val MODEL = "gemini-3.6-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun endpoint(): String =
        "$BASE_URL/$MODEL:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

    /** Asks Gemini for a single outdoor photo mission, as a plain English sentence. */
    suspend fun generateMission(previousMission: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "generateMission: requesting new mission")
            val avoidLine = if (previousMission.isNullOrBlank()) {
                ""
            } else {
                "\nThe previous mission was: \"$previousMission\". Give a different one this time.\n"
            }
            val prompt = """
                Request a user to take a picture of a plastic bottle inside the room.
            """.trimIndent()

            val body = JSONObject().apply {
                put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray().put(
                                JSONObject().put("text", prompt)
                            )
                        )
                    )
                )
                put(
                    "generationConfig", JSONObject().apply {
                        put("temperature", 1.3)
                    }
                )
            }

            val request = Request.Builder()
                .url(endpoint())
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "generateMission failed: ${response.code} $raw")
                    return@withContext Result.failure(Exception("Gemini error ${response.code}"))
                }
                val text = extractText(raw)?.trim()
                    ?: return@withContext Result.failure(Exception("Empty mission response"))
                Log.d(TAG, "generateMission: got \"$text\"")
                Result.success(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateMission exception", e)
            Result.failure(e)
        }
    }

    /**
     * Sends the submitted photo + mission text to Gemini and asks it to judge
     * whether the photo satisfies the mission. Returns true if approved.
     */
    suspend fun verifyPhoto(missionText: String, jpegBytes: ByteArray): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                val prompt = """
                    The user's mission was: "$missionText"
                    Look at the attached photo. Decide if it genuinely and plausibly
                    fulfills the mission and appears to be taken outdoors, right now,
                    by the user (not a screenshot, not a photo of a screen, not a
                    stock-looking image).
                    Reply with EXACTLY one word: APPROVE or REJECT. No other text.
                """.trimIndent()

                val parts = JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(
                        JSONObject().put(
                            "inline_data", JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", base64Image)
                        )
                    )

                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                }

                val request = Request.Builder()
                    .url(endpoint())
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "verifyPhoto failed: ${response.code} $raw")
                        return@withContext Result.failure(Exception("Gemini error ${response.code}"))
                    }
                    val verdict = extractText(raw)?.trim()?.uppercase().orEmpty()
                    Result.success(verdict.contains("APPROVE"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "verifyPhoto exception", e)
                Result.failure(e)
            }
        }

    private fun extractText(rawJson: String): String? {
        return try {
            val json = JSONObject(rawJson)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: $rawJson", e)
            null
        }
    }
}
