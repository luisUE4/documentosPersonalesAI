package mx.motion.documentospersonalesai.ui.home

import android.app.Application
import android.app.ActivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import android.content.Context
import android.util.Log

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    ///TODO funcionalidad para convertir audio a texto y preguntar informacion
    ///TODO dictado de prompt con voz

    private val _promptText = MutableLiveData<String>()
    val promptText: LiveData<String> = _promptText

    private val _aiResponse = MutableLiveData<String>()
    val aiResponse: LiveData<String> = _aiResponse

    private val _showErrorAlert = MutableLiveData<String?>()
    val showErrorAlert: LiveData<String?> = _showErrorAlert

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _statusText = MutableLiveData<String?>()
    val statusText: LiveData<String?> = _statusText

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var inferenceJob: kotlinx.coroutines.Job? = null

    private fun formatHtml(text: String): String {
        val formatted = text.replace("\n", "<br>")
        return "<b><font color='#FFD700'>$formatted</font></b>"
    }

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
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _showErrorAlert.value = reason ?: "El dispositivo no es compatible con el modelo de IA."
                _aiResponse.value = "Error: $reason"
            }
            return
        }

        try {
            val modelDir = getApplication<Application>().filesDir
            // El formato recomendado para LiteRT-LM es .litertlm
            // Para Gemma 4 E2B (Effective 2B), el archivo suele pesar ~1.3GB
            val modelFile = File(modelDir, "gemma-4-E4B-it.litertlm")
            val modelPath = modelFile.absolutePath

            if (!modelFile.exists()) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _showErrorAlert.value = "No se encontró el modelo Gemma 4 en: $modelPath. Asegúrate de descargar la versión .litertlm oficial."
                    _aiResponse.value = "Error: Modelo no encontrado."
                }
                return
            }

            Log.d("HomeViewModel", "Cargando motor LiteRT-LM desde: $modelPath")
            withContext(Dispatchers.Main) {
                _aiResponse.value = formatHtml("Intentando cargar la inteligencia artificial ... espera porfavor.")
            }

            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(), // Usar GPU para el modelo de lenguaje
                visionBackend = Backend.GPU(), // Usar GPU para el codificador de imágenes
                maxNumTokens = 2048
            )

            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            
            // Crear la sesión de conversación
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
                _aiResponse.value = "Modelo Gemma 4 (LiteRT-LM) listo para usar."
                Log.d("HomeViewModel", "LiteRT-LM Engine inicializado con éxito en $durationSeconds segundos.")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                val errorMsg = e.localizedMessage ?: "Error desconocido"
                _aiResponse.value = "Error al cargar LiteRT-LM: $errorMsg"
                _showErrorAlert.value = "Error de inicialización: El archivo podría estar corrupto o no ser compatible con el backend GPU."
                Log.e("HomeViewModel", "Error fatal al inicializar: $errorMsg", e)
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
                // Si ya hay 20 o más registros, limpiamos y guardamos solo el nuevo
                statsFile.writeText("$seconds\n")
                Log.d("HomeViewModel", "Límite de 20 registros alcanzado. Archivo reiniciado con el nuevo tiempo.")
            } else {
                statsFile.appendText("$seconds\n")
            }
            Log.d("HomeViewModel", "Tiempo de inicialización guardado: $seconds s")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error al guardar el tiempo de inicialización", e)
        }
    }

    fun sendPrompt(prompt: String, imagePaths: List<String> = emptyList()) {
        _promptText.value = prompt
        
        _aiResponse.value = "Gemma 4 está procesando..."
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

            if (engine == null || conversation == null) {
                // El error ya fue reportado en setupModelInternal
                return@launch
            }

            try {
                val responseBuilder = StringBuilder()
                
                // Preparar el contenido (Texto + Imagen(es) opcional)
                val contentList = mutableListOf<Content>()
                
                imagePaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        contentList.add(Content.ImageFile(path))
                    }
                }
                
                contentList.add(Content.Text(prompt))
                val contents = Contents.of(contentList)

                // LiteRT-LM usa Flows de Kotlin para streaming por defecto
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
                        _aiResponse.value = "Error en inferencia: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    fun stopInference() {
        inferenceJob?.cancel()
        _isLoading.value = false
        _aiResponse.value = _aiResponse.value + " [DETENIDO]"
    }

    fun clearErrorAlert() {
        _showErrorAlert.value = null
    }

    fun isDeviceCompatible(): Pair<Boolean, String?> {
        val context = getApplication<Application>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)

        // Gemma 4 E2B corre mejor con 6GB+ RAM
        if (totalRamMb < 5500) {
            return false to "Se requieren al menos 6GB de RAM total (detectado: ${totalRamMb}MB) para Gemma 4."
        }

        if (memoryInfo.lowMemory || availableRamMb < 1500) {
            return false to "Memoria RAM disponible insuficiente (${availableRamMb}MB)."
        }

        val configInfo = activityManager.deviceConfigurationInfo
        if (configInfo.reqGlEsVersion < 0x30001) {
            return false to "Se requiere OpenGL ES 3.1+ para el motor LiteRT-LM."
        }

        return true to null
    }

    fun closeModel() {
        engine?.close()
        engine = null
        conversation = null
        Log.d("HomeViewModel", "Recursos de LiteRT-LM liberados.")
    }

    override fun onCleared() {
        super.onCleared()
        closeModel()
    }
}
