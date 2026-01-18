package id.xms.xcai.data.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Helper class for Android's built-in Speech Recognition
 * Uses Google's Speech-to-Text (free, no API key needed)
 */
class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: (Boolean) -> Unit
) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    init {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechRecognizerHelper", "Speech recognition not available on this device")
        }
    }
    
    /**
     * Start listening for speech input
     * @param languageCode Language code (default: "id-ID" for Indonesian)
     */
    fun startListening(languageCode: String = "id-ID") {
        if (isListening) {
            Log.w("SpeechRecognizerHelper", "Already listening, ignoring start request")
            return
        }
        
        try {
            // Create new recognizer instance
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            
            // Create intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            Log.d("SpeechRecognizerHelper", "Starting speech recognition for language: $languageCode")
            speechRecognizer?.startListening(intent)
            isListening = true
            onListening(true)
            
        } catch (e: Exception) {
            Log.e("SpeechRecognizerHelper", "Error starting speech recognition: ${e.message}")
            onError("Gagal memulai pengenalan suara")
            cleanup()
        }
    }
    
    /**
     * Stop listening
     */
    fun stopListening() {
        Log.d("SpeechRecognizerHelper", "Stopping speech recognition")
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerHelper", "Error stopping: ${e.message}")
        }
    }
    
    /**
     * Cancel and cleanup
     */
    fun cancel() {
        cleanup()
    }
    
    private fun cleanup() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SpeechRecognizerHelper", "Error during cleanup: ${e.message}")
        }
        speechRecognizer = null
        isListening = false
        onListening(false)
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizerHelper", "Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizerHelper", "Speech started")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - could use for visual feedback
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizerHelper", "Speech ended")
            }
            
            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e("SpeechRecognizerHelper", "Recognition error: $errorMessage (code: $error)")
                
                // Don't report NO_MATCH as error - just empty result
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    onError(errorMessage)
                }
                cleanup()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcribedText = matches?.firstOrNull() ?: ""
                
                Log.d("SpeechRecognizerHelper", "Recognition result: $transcribedText")
                
                if (transcribedText.isNotBlank()) {
                    onResult(transcribedText)
                } else {
                    onError("Tidak ada suara terdeteksi. Coba lagi.")
                }
                cleanup()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("SpeechRecognizerHelper", "Partial result: ${matches?.firstOrNull()}")
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
    
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Masalah audio"
            SpeechRecognizer.ERROR_CLIENT -> "Error client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon diperlukan"
            SpeechRecognizer.ERROR_NETWORK -> "Koneksi bermasalah"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout koneksi"
            SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada suara terdeteksi"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenal suara sibuk"
            SpeechRecognizer.ERROR_SERVER -> "Error server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara"
            else -> "Error tidak dikenal"
        }
    }
    
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
}
