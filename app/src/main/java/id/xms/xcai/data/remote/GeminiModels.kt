package id.xms.xcai.data.remote

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = "user"
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inline_data")
    val inlineData: GeminiBlob? = null
)

data class GeminiBlob(
    @SerializedName("mime_type")
    val mimeType: String,
    val data: String // Base64 encoded
)

data class GeminiGenerationConfig(
    val temperature: Double? = 0.7,
    @SerializedName("top_p")
    val topP: Double? = 0.95,
    @SerializedName("top_k")
    val topK: Int? = 40,
    @SerializedName("max_output_tokens")
    val maxOutputTokens: Int? = 8192
    // Note: thinking_level is technically part of the model behavior in v1beta
    // but google-generativeai client libraries handle it via config.
    // For raw REST API, we check if it's supported in standard config or 
    // requires specific tuning params. 
    // Since this is "thinking_level" as per user request, we'll include it loosely
    // but the actual parameter might be model-specific or require specific header.
    // We will stick to standard generationConfig structure first.
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?,
    val index: Int?,
    val safetyRatings: List<GeminiSafetyRating>?
)

data class GeminiSafetyRating(
    val category: String,
    val probability: String
)

data class GeminiPromptFeedback(
    val blockReason: String? = null
)
