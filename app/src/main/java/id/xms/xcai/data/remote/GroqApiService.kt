package id.xms.xcai.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

interface GroqApiService {

    @POST("openai/v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqChatRequest
    ): GroqChatResponse

    @POST("openai/v1/chat/completions")
    suspend fun sendVisionMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqVisionRequest
    ): GroqChatResponse

    companion object {
        private const val BASE_URL = "https://api.groq.com/"

        private fun createGson(): Gson {
            return GsonBuilder()
                .registerTypeAdapter(ContentPart::class.java, ContentPartSerializer())
                .create()
        }

        fun create(): GroqApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(createGson()))
                .build()
                .create(GroqApiService::class.java)
        }
    }
}

// Custom serializer for ContentPart sealed class
class ContentPartSerializer : JsonSerializer<ContentPart> {
    override fun serialize(
        src: ContentPart,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return when (src) {
            is ContentPart.TextPart -> context.serialize(src, ContentPart.TextPart::class.java)
            is ContentPart.ImagePart -> context.serialize(src, ContentPart.ImagePart::class.java)
        }
    }
}
