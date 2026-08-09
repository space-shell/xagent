package sh.paseochat.launcher.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class VoiceController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    var isListening by mutableStateOf(false)
        private set
    var partialText by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var onFinal: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(errorType: Int) {
            isListening = false
            partialText = ""
            val msg = errorMessage(errorType)
            error = msg
            onError?.invoke(msg)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.extractText().orEmpty()
            if (text.isNotBlank()) partialText = text
        }

        override fun onResults(results: Bundle?) {
            val text = results?.extractText().orEmpty()
            isListening = false
            if (text.isNotBlank()) {
                partialText = text
                onFinal?.invoke(text)
            } else {
                error = "No speech detected"
                onError?.invoke(error!!)
            }
        }
    }

    fun start() {
        error = null
        partialText = ""
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "Voice input unavailable on this device"
            onError?.invoke(error!!)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer?.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
        isListening = true
    }

    fun stop() {
        recognizer?.stopListening()
        isListening = false
    }

    fun reset() {
        partialText = ""
        error = null
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun Bundle.extractText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorMessage(type: Int): String = when (type) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn\u2019t catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language unsupported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Recognition error"
    }
}

@Composable
fun rememberVoiceController(): VoiceController {
    val context = LocalContext.current
    val controller = remember { VoiceController(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { controller.destroy() }
    }
    return controller
}
