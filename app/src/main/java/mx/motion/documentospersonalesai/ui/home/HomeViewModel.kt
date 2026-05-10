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
import java.io.File
import android.content.Context
import android.util.Log

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _promptText = MutableLiveData<String>()
    val promptText: LiveData<String> = _promptText

    private val _aiResponse = MutableLiveData<String>()
    val aiResponse: LiveData<String> = _aiResponse

    private val _showErrorAlert = MutableLiveData<String?>()
    val showErrorAlert: LiveData<String?> = _showErrorAlert

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private fun formatHtml(text: String): String {
        val formatted = text.replace("\n", "<br>")
        return "<b><font color='#FFD700'>$formatted</font></b>"
    }

    fun setupModel() {
        if (engine != null) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            val (isCompatible, reason) = isDeviceCompatible()
            if (!isCompatible) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _showErrorAlert.value = reason ?: "El dispositivo no es compatible con el modelo de IA."
                    _aiResponse.value = "Error: $reason"
                }
                return@launch
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
                    return@launch
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

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _aiResponse.value = "Modelo Gemma 4 (LiteRT-LM) listo para usar."
                    Log.d("HomeViewModel", "LiteRT-LM Engine inicializado con éxito.")
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
    }

    fun sendPrompt(prompt: String, imagePaths: List<String> = emptyList()) {
        _promptText.value = prompt
        
        if (engine == null || conversation == null) {
            _aiResponse.value = "El motor de IA no está inicializado.."
            return
        }

        _aiResponse.value = "Gemma 4 está procesando..."
        _isLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
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
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _aiResponse.value = "Error en inferencia: ${e.localizedMessage}"
                }
            }
        }
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
