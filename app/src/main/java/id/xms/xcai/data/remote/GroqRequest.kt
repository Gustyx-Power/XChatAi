package id.xms.xcai.data.remote

import com.google.gson.annotations.SerializedName

data class GroqChatRequest(
    @SerializedName("model")
    val model: String = "llama-3.3-70b-versatile",
    @SerializedName("messages")
    val messages: List<Message>,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048
)

data class Message(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
)

data class GroqChatResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("choices")
    val choices: List<Choice>,
    @SerializedName("created")
    val created: Long,
    @SerializedName("model")
    val model: String
)

data class Choice(
    @SerializedName("index")
    val index: Int,
    @SerializedName("message")
    val message: Message,
    @SerializedName("finish_reason")
    val finishReason: String
)

// ====== Vision/Multimodal Support ======

data class GroqVisionRequest(
    @SerializedName("model")
    val model: String = "meta-llama/llama-4-maverick-17b-128e-instruct",
    @SerializedName("messages")
    val messages: List<VisionMessage>,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048
)

data class VisionMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: List<ContentPart>
)

sealed class ContentPart {
    data class TextPart(
        @SerializedName("type")
        val type: String = "text",
        @SerializedName("text")
        val text: String
    ) : ContentPart()

    data class ImagePart(
        @SerializedName("type")
        val type: String = "image_url",
        @SerializedName("image_url")
        val imageUrl: ImageUrl
    ) : ContentPart()
}

data class ImageUrl(
    @SerializedName("url")
    val url: String
)
