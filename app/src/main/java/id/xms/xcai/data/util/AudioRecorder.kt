package id.xms.xcai.data.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Simple audio recorder utility for Speech-to-Text
 */
class AudioRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    /**
     * Start recording audio
     * @return true if recording started successfully
     */
    fun startRecording(): Boolean {
        return try {
            // Create output file
            outputFile = File.createTempFile(
                "audio_${System.currentTimeMillis()}_",
                ".m4a",
                context.cacheDir
            )
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }
            
            Log.d("AudioRecorder", "Recording started: ${outputFile?.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording: ${e.message}")
            cleanup()
            false
        }
    }
    
    /**
     * Stop recording and return the audio file
     * @return The recorded audio file, or null if recording failed
     */
    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            Log.d("AudioRecorder", "Recording stopped: ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording: ${e.message}")
            cleanup()
            null
        }
    }
    
    /**
     * Cancel recording and delete the file
     */
    fun cancelRecording() {
        cleanup()
    }
    
    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing MediaRecorder: ${e.message}")
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = mediaRecorder != null
}
