package mx.motion.documentospersonalesai.ui.home

import android.app.Application
import android.app.ActivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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

    private var llmInference: LlmInference? = null

    init {
        // El modelo se cargará cuando el fragmento esté activo si es necesario
    }

    fun setupModel() {
        if (llmInference != null) return

        viewModelScope.launch(Dispatchers.IO) {

            if (!canLoadLargeModel()) {
                // Aquí podrías cargar un modelo más ligero como Gemma-4-E2B
                // o mostrar un mensaje al usuario para que cierre otras apps.
                withContext(Dispatchers.Main) {
                    _showErrorAlert.value = "El dispositivo no tiene suficiente memoria para cargar el modelo de inteligencia artificial."
                    _aiResponse.value = "Error: El dispositivo no tiene suficiente memoria para cargar el modelo de inteligencia artificial."
                }
                return@launch
            }

            try {
                // Configuración para cargar el modelo local (Gemma)

                val modelDir = getApplication<Application>().filesDir

                // Crear la carpeta si no existe
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                //val visionPath = File(modelDir, "vision_encoder_q4.onnx").absolutePath
                val modelFile = File(modelDir, "decoder_model_merged_q4.onnx")
                val decoderPath = modelFile.absolutePath

                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) {
                        _showErrorAlert.value = "No se encontró el modelo Gemma en: $decoderPath. Por favor, asegúrate de haberlo subido al dispositivo."
                        _aiResponse.value = "Error: Modelo no encontrado."
                    }
                    return@launch
                }

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(decoderPath)
                    .setMaxTokens(512)
                    .setTemperature(0.7f)
                    .build()

                //recomendado por el chat con gemini web
//                val options = LlmInference.LlmInferenceOptions.builder()
//                    .setModelAssetPath("/data/local/tmp/gemma4.bin") // Ruta donde descargaste el asset
//                    .setMaxTokens(2048)
//                    .setTopK(40)
//                    .setTemperature(0.7f)
//                    .setRandomSeed(101)
//                    .build()

                llmInference = LlmInference.createFromOptions(getApplication(), options)
                
                withContext(Dispatchers.Main) {
                    _aiResponse.value = "Modelo Gemma cargado localmente."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _aiResponse.value = "Error al cargar Gemma: ${e.localizedMessage}. Asegúrate de tener 'gemma.bin' en assets."
                }
            }
        }
    }

    fun sendPrompt(prompt: String) {
        _promptText.value = prompt
        
        if (llmInference == null) {
            _aiResponse.value = "El motor de IA no está inicializado."
            return
        }

        _aiResponse.value = "Gemma está procesando..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Inferencia local síncrona dentro de una corrutina
                val response = llmInference?.generateResponse(prompt)
                withContext(Dispatchers.Main) {
                    _aiResponse.value = response ?: "Sin respuesta del modelo."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _aiResponse.value = "Error en inferencia: ${e.localizedMessage}"
                }
            }
        }
    }

    fun clearErrorAlert() {
        _showErrorAlert.value = null
    }

//    suspend fun analyzeDocument(bitmap: Bitmap, prompt: String): String = withContext(Dispatchers.Default) {
//        // Nota: La API de visión de Gemma 4 requiere pasar la imagen y el texto juntos
//        // El método específico puede variar según la versión exacta del SDK en 2026
//        // val response = llmInference?.generateResponse(bitmap, prompt)
//        // return@withContext response ?: "Error en la inferencia"
//        return@withContext "Análisis de imagen no soportado en esta versión de LlmInference"
//    }

    fun canLoadLargeModel(): Boolean {
        val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Convertir bytes a MB para facilitar el cálculo
        val availableMegs = memoryInfo.availMem / (1024 * 1024)
        val totalMegs = memoryInfo.totalMem / (1024 * 1024)

        Log.d("MemoryCheck", "Memoria disponible: $availableMegs MB de $totalMegs MB")

        // Umbral de seguridad para un modelo de 3GB (E4B)
        // Aunque el modelo esté en disco, necesita RAM para activaciones y contexto.
        val minRequiredRam = 1200 // MB (Ajusta según pruebas en gama media)

        return if (memoryInfo.lowMemory) {
            false // El sistema ya está en estado de memoria baja
        } else {
            availableMegs > minRequiredRam
        }
    }


    fun closeModel() {
        llmInference?.close()
        llmInference = null
        Log.d("HomeViewModel", "Modelo Gemma cerrado para liberar recursos.")
    }

    override fun onCleared() {
        super.onCleared()
        closeModel()
    }
}
