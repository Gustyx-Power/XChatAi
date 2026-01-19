package id.xms.xcai.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val domain: String,
    val fullContent: String? = null
)

class WebSearchService {

    companion object {
        private const val SERPER_API_URL = "https://google.serper.dev/search"
    }

    // API key is passed from ChatRepository (fetched from Firebase)
    suspend fun searchWeb(query: String, serperApiKey: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("WebSearchService", "Starting Serper.dev search: $query")
            
            // Search across multiple regions for global coverage
            val idDeferred = async { searchSerper(query, serperApiKey, "id") }
            val usDeferred = async { searchSerper(query, serperApiKey, "us") }
            
            val allResults = awaitAll(idDeferred, usDeferred).flatten()
            
            // Deduplicate by URL
            val distinctResults = allResults.distinctBy { it.url }
            Log.d("WebSearchService", "Serper returned ${distinctResults.size} unique results")
            
            distinctResults.take(10) // Return top 10 results
        } catch (e: Exception) {
            Log.e("WebSearchService", "Serper search failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun searchSerper(query: String, apiKey: String, region: String): List<SearchResult> {
        val url = URL(SERPER_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-API-KEY", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Build request body
            val requestBody = JSONObject().apply {
                put("q", query)
                put("gl", region) // Geographic location (id, us, uk, etc.)
                put("num", 10) // Number of results
            }

            // Send request
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(requestBody.toString())
            }

            val responseCode = connection.responseCode
            Log.d("WebSearchService", "Serper ($region) response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseSerperResponse(response)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e("WebSearchService", "Serper error: $error")
                emptyList()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSerperResponse(response: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        try {
            val json = JSONObject(response)
            
            // Parse organic search results
            val organicArray = json.optJSONArray("organic") ?: return emptyList()
            
            for (i in 0 until organicArray.length()) {
                val item = organicArray.getJSONObject(i)
                
                val title = item.optString("title", "")
                val urlString = item.optString("link", "")
                val snippet = item.optString("snippet", "")
                
                val domain = try {
                    java.net.URI(urlString).host?.removePrefix("www.") ?: "web"
                } catch (e: Exception) {
                    "web"
                }
                
                if (title.isNotBlank() && urlString.isNotBlank()) {
                    results.add(
                        SearchResult(
                            title = title,
                            snippet = snippet,
                            url = urlString,
                            domain = domain,
                            fullContent = snippet // Use snippet as content
                        )
                    )
                }
            }
            
            Log.d("WebSearchService", "Parsed ${results.size} results from Serper")
            
        } catch (e: Exception) {
            Log.e("WebSearchService", "Error parsing Serper response: ${e.message}")
        }
        
        return results
    }
}
