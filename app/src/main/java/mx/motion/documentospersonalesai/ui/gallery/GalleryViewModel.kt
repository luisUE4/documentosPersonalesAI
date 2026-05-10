package mx.motion.documentospersonalesai.ui.gallery

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _transcriptionResult = MutableLiveData<String>()
    val transcriptionResult: LiveData<String> = _transcriptionResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var voskModel: Model? = null

    init {
        loadVoskModel()
    }

    private fun loadVoskModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val modelDir = File(context.filesDir, "vosk-model")
                if (modelDir.exists()) {
                    voskModel = Model(modelDir.absolutePath)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Vosk listo. Adjunta un audio."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Modelo Vosk no encontrado en: ${modelDir.absolutePath}"
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error cargando Vosk", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error cargando Vosk: ${e.message}"
                }
            }
        }
    }

    fun transcribeAudio(path: String, fileName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val displayName = fileName ?: File(path).name
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Preparando $displayName..."
            }

            try {
                // Preprocesar el audio para que sea óptimo para Vosk
                val processedPath = prepareAudioForVosk(path)
                
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Procesando $displayName..."
                }

                val text = transcribeFile(processedPath)
                val audioFile = File(path)
                val audioName = audioFile.nameWithoutExtension
                val timeStamp = SimpleDateFormat("HH.dd.MM", Locale.getDefault()).format(Date())
                val txtFileName = "${audioName}-${timeStamp}.txt"
                
                saveTranscriptionToFile(txtFileName, text)

                // Eliminar el archivo temporal si se creó uno nuevo
                if (processedPath != path) {
                    File(processedPath).delete()
                }

                withContext(Dispatchers.Main) {
                    _transcriptionResult.value = text
                    _statusMessage.value = "Audio procesado con éxito."
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error: ${e.localizedMessage}"
                    _isLoading.value = false
                }
                Log.e("GalleryViewModel", "Error en el proceso", e)
            }
        }
    }

    private suspend fun prepareAudioForVosk(inputPath: String): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val outputDir = File(context.cacheDir, "audio_temp")
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.wav")
        val outputPath = outputFile.absolutePath

        // Comando FFmpeg para convertir a:
        // - Frecuencia de muestreo: 16000 Hz (-ar 16000)
        // - Canales: 1 (mono) (-ac 1)
        // - Formato: PCM s16le (-f s16le no es necesario si la extension es wav, pero asegura compatibilidad)
        // - Forzar sobreescritura (-y)
        val command = "-y -i \"$inputPath\" -ar 16000 -ac 1 -c:a pcm_s16le \"$outputPath\""
        
        Log.d("GalleryViewModel", "Ejecutando FFmpeg: $command")
        
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) {
            Log.d("GalleryViewModel", "Conversión exitosa: $outputPath")
            outputPath
        } else {
            Log.e("GalleryViewModel", "Error en FFmpeg: ${session.failStackTrace}")
            throw Exception("No se pudo optimizar el audio para transcripción")
        }
    }

    private fun saveTranscriptionToFile(fileName: String, content: String) {
        try {
            val folder = File(getApplication<Application>().filesDir, "textos")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error guardando archivo", e)
        }
    }

    private suspend fun transcribeFile(path: String): String = withContext(Dispatchers.IO) {
        val model = voskModel ?: throw Exception("Modelo Vosk no inicializado")
        // Frecuencia de muestreo 16000 es estándar para muchos modelos Vosk small
        val recognizer = Recognizer(model, 16000.0f)
        val inputStream = FileInputStream(path)
        val buffer = ByteArray(4096)
        var nbytes: Int
        val resultText = StringBuilder()

        while (inputStream.read(buffer).also { nbytes = it } >= 0) {
            if (recognizer.acceptWaveForm(buffer, nbytes)) {
                resultText.append(extractTextFromJson(recognizer.result))
            }
        }
        resultText.append(extractTextFromJson(recognizer.finalResult))
        recognizer.close()
        inputStream.close()
        resultText.toString().trim()
    }

    private fun extractTextFromJson(json: String): String {
        // Vosk devuelve JSON como {"text" : "hola mundo"}
        val marker = "\"text\" : \""
        val start = json.indexOf(marker)
        if (start == -1) return ""
        val contentStart = start + marker.length
        val end = json.indexOf("\"", contentStart)
        return if (end != -1) json.substring(contentStart, end) + " " else ""
    }

    override fun onCleared() {
        super.onCleared()
        // Vosk Model no tiene close explicito en el wrapper base de android a veces, 
        // pero liberamos la referencia.
        voskModel = null
    }
}
