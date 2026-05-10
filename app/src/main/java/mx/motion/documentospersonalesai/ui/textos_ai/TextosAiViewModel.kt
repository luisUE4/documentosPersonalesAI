package mx.motion.documentospersonalesai.ui.textos_ai

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TextosAiViewModel(application: Application) : AndroidViewModel(application) {

    private val _promptText = MutableLiveData<String>()
    val promptText: LiveData<String> = _promptText

    private val _aiResponse = MutableLiveData<String>()
    val aiResponse: LiveData<String> = _aiResponse

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var inferenceJob: Job? = null

    fun setupModel() {
        if (engine != null) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            
            val (isCompatible, reason) = isDeviceCompatible()
            if (!isCompatible) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _aiResponse.value = "Error: $reason"
                }
                return@launch
            }

            try {
                val modelDir = getApplication<Application>().filesDir
                val modelFile = File(modelDir, "gemma-4-E4B-it.litertlm")

                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _aiResponse.value = "Error: No se encontró el modelo Gemma 4 en la carpeta interna."
                    }
                    return@launch
                }

                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    maxNumTokens = 2048
                )

                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                conversation = newEngine.createConversation()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _statusMessage.value = "Modelo Gemma 4 listo."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _aiResponse.value = "Error al cargar IA: ${e.localizedMessage}"
                }
            }
        }
    }

    fun sendPrompt(instruction: String, contextText: String) {
        if (engine == null || conversation == null) {
            _aiResponse.value = "La IA no está inicializada."
            return
        }

        _promptText.value = instruction
        _isLoading.value = true
        inferenceJob?.cancel()
        
        inferenceJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseBuilder = StringBuilder()
                
                // Unir la instrucción con el contexto (texto manual + archivos)
                val finalPrompt = if (contextText.isNotBlank()) {
                    "CONTEXTO:\n$contextText\n\nINSTRUCCIÓN:\n$instruction"
                } else {
                    instruction
                }

                val contents = Contents.of(listOf(Content.Text(finalPrompt)))

                conversation?.sendMessageAsync(contents)?.collect { token ->
                    if (responseBuilder.isEmpty()) {
                        withContext(Dispatchers.Main) { _isLoading.value = false }
                    }
                    responseBuilder.append(token)
                    withContext(Dispatchers.Main) {
                        _aiResponse.value = responseBuilder.toString()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _aiResponse.value = "Error: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    fun stopInference() {
        inferenceJob?.cancel()
        _isLoading.value = false
        _aiResponse.value = (_aiResponse.value ?: "") + " [DETENIDO]"
    }

    private fun isDeviceCompatible(): Pair<Boolean, String?> {
        val context = getApplication<Application>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        if (totalRamMb < 5500) return false to "Se requieren 6GB de RAM."
        return true to null
    }

    fun closeModel() {
        engine?.close()
        engine = null
        conversation = null
        Log.d("TextosAiViewModel", "Motor Gemma cerrado.")
    }

    override fun onCleared() {
        super.onCleared()
        closeModel()
    }
}