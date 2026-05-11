package mx.motion.documentospersonalesai.ui.grabacion_voz

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.File

class GrabacionVozViewModel(application: Application) : AndroidViewModel(application), RecognitionListener {

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _transcriptionResult = MutableLiveData<String>()
    val transcriptionResult: LiveData<String> = _transcriptionResult

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    private var voskModel: Model? = null
    private var speechService: SpeechService? = null
    
    private val fullTranscription = StringBuilder()

    init {
        loadVoskModel()
    }

    private fun loadVoskModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val modelDir = File(context.filesDir, "vosk-model")
                if (modelDir.exists()) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Cargando modelo de voz..."
                    }
                    voskModel = Model(modelDir.absolutePath)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Vosk listo. Presiona para grabar."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Modelo Vosk no encontrado en: ${modelDir.absolutePath}"
                    }
                }
            } catch (e: Exception) {
                Log.e("GrabacionVozViewModel", "Error cargando Vosk", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error cargando Vosk: ${e.message}"
                }
            }
        }
    }

    fun toggleRecording() {
        if (_isRecording.value == true) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val model = voskModel ?: run {
            _statusMessage.value = "Modelo no cargado"
            return
        }

        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            _isRecording.value = true
            _statusMessage.value = "Escuchando..."
            fullTranscription.setLength(0)
            _transcriptionResult.value = ""
        } catch (e: Exception) {
            _statusMessage.value = "Error al iniciar grabación: ${e.message}"
            Log.e("GrabacionVozViewModel", "Error en startRecording", e)
        }
    }

    fun stopRecording() {
        speechService?.stop()
        speechService = null
        _isRecording.value = false
        _statusMessage.value = "Grabación finalizada."
    }

    override fun onResult(hypothesis: String) {
        val text = extractTextFromJson(hypothesis)
        if (text.isNotBlank()) {
            fullTranscription.append(text).append(" ")
            _transcriptionResult.value = fullTranscription.toString().trim()
        }
    }

    override fun onFinalResult(hypothesis: String) {
        val text = extractTextFromJson(hypothesis)
        if (text.isNotBlank()) {
            fullTranscription.append(text).append(" ")
            _transcriptionResult.value = fullTranscription.toString().trim()
        }
    }

    override fun onPartialResult(hypothesis: String) {
        val partial = extractPartialTextFromJson(hypothesis)
        if (partial.isNotBlank()) {
            _transcriptionResult.value = fullTranscription.toString() + partial
        }
    }

    override fun onError(e: Exception) {
        _statusMessage.value = "Error: ${e.message}"
        _isRecording.value = false
    }

    override fun onTimeout() {
        _isRecording.value = false
        _statusMessage.value = "Tiempo agotado."
    }

    private fun extractTextFromJson(json: String): String {
        val marker = "\"text\" : \""
        val start = json.indexOf(marker)
        if (start == -1) return ""
        val contentStart = start + marker.length
        val end = json.indexOf("\"", contentStart)
        return if (end != -1) json.substring(contentStart, end) else ""
    }

    private fun extractPartialTextFromJson(json: String): String {
        val marker = "\"partial\" : \""
        val start = json.indexOf(marker)
        if (start == -1) return ""
        val contentStart = start + marker.length
        val end = json.indexOf("\"", contentStart)
        return if (end != -1) json.substring(contentStart, end) else ""
    }

    fun closeResources() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        voskModel = null
    }

    override fun onCleared() {
        super.onCleared()
        closeResources()
    }
}
