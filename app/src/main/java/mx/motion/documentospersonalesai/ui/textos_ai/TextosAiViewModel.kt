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
import kotlinx.coroutines.delay
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

    private val _statusText = MutableLiveData<String?>()
    val statusText: LiveData<String?> = _statusText

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var inferenceJob: Job? = null

    private suspend fun setupModelInternal() {
        if (engine != null) return

        val startTime = System.currentTimeMillis()
        val avgTime = getAverageInitializationTime().toInt().coerceAtLeast(1)

        val countdownJob = viewModelScope.launch(Dispatchers.Main) {
            for (i in avgTime downTo 0) {
                _statusText.value = "inicializando Inteligencia $i segundos restantes"
                delay(1000)
            }
            _statusText.value = "inicializando Inteligencia ... casi listo"
        }

        withContext(Dispatchers.Main) { _isLoading.value = true }

        val (isCompatible, reason) = isDeviceCompatible()
        if (!isCompatible) {
            countdownJob.cancel()
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _aiResponse.value = "Error: $reason"
            }
            return
        }

        try {
            val modelDir = getApplication<Application>().filesDir
            val modelFile = File(modelDir, "gemma-4-E4B-it.litertlm")

            if (!modelFile.exists()) {
                countdownJob.cancel()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _aiResponse.value = "Error: No se encontró el modelo Gemma 4 en la carpeta interna."
                }
                return
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

            countdownJob.cancel()
            withContext(Dispatchers.Main) {
                _statusText.value = "procesando instruccion ..."
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000.0
            saveInitializationTime(durationSeconds)

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _statusMessage.value = "Modelo Gemma 4 listo."
            }
        } catch (e: Exception) {
            countdownJob.cancel()
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _aiResponse.value = "Error al cargar IA: ${e.localizedMessage}"
            }
        }
    }

    private fun getAverageInitializationTime(): Double {
        return try {
            val statsFile = File(File(getApplication<Application>().filesDir, "estadistica"), "ai_tiempo_de_inicializado.txt")
            if (statsFile.exists()) {
                val lines = statsFile.readLines()
                if (lines.isNotEmpty()) {
                    lines.mapNotNull { it.toDoubleOrNull() }.average()
                } else 30.0
            } else 30.0
        } catch (e: Exception) {
            30.0
        }
    }

    private fun saveInitializationTime(seconds: Double) {
        try {
            val statsDir = File(getApplication<Application>().filesDir, "estadistica")
            if (!statsDir.exists()) statsDir.mkdirs()
            val statsFile = File(statsDir, "ai_tiempo_de_inicializado.txt")
            
            val lines = if (statsFile.exists()) statsFile.readLines() else emptyList()
            
            if (lines.size >= 20) {
                statsFile.writeText("$seconds\n")
            } else {
                statsFile.appendText("$seconds\n")
            }
        } catch (e: Exception) {
            Log.e("TextosAiViewModel", "Error al guardar tiempo", e)
        }
    }

    fun sendPrompt(instruction: String, contextText: String) {
        _promptText.value = instruction
        _isLoading.value = true
        inferenceJob?.cancel()
        
        inferenceJob = viewModelScope.launch(Dispatchers.IO) {
            if (engine == null || conversation == null) {
                setupModelInternal()
            } else {
                withContext(Dispatchers.Main) {
                    _statusText.value = null
                }
            }

            if (engine == null || conversation == null) return@launch

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