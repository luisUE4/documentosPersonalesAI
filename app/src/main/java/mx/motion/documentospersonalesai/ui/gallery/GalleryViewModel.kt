package mx.motion.documentospersonalesai.ui.gallery

import android.app.Application
import android.app.ActivityManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _transcriptionResult = MutableLiveData<String>()
    val transcriptionResult: LiveData<String> = _transcriptionResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _progressMinutes = MutableLiveData<String>()
    val progressMinutes: LiveData<String> = _progressMinutes

    private var voskModel: Model? = null
    private var transcriptionJob: Job? = null

    init {
        // LibVosk.setLogLevel es opcional si hay problemas con los enums
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
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch(Dispatchers.IO) {
            val displayName = fileName ?: File(path).name
            val startTime = System.currentTimeMillis()
            
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Preparando $displayName..."
                _progressMinutes.value = "Calculando..."
            }

            try {
                // 1. Optimizar el audio principal
                val processedPath = prepareAudioForVosk(path)
                
                // 2. Dividir en fragmentos guardándolos en la carpeta solicitada
                val chunks = splitAudioPersistently(processedPath, displayName)
                val totalMinutes = chunks.size * 2 // Cada fragmento son ~2 min
                var processedMinutes = 0
                
                withContext(Dispatchers.Main) {
                    _progressMinutes.value = "0 / $totalMinutes min"
                }

                // 3. Semáforo para limitar concurrencia (máximo 3 hilos)
                val semaphore = Semaphore(3)
                
                val text = if (chunks.size > 1) {
                    chunks.mapIndexed { index, chunkPath ->
                        async(Dispatchers.Default) {
                            semaphore.withPermit {
                                val chunkResult = transcribeFile(chunkPath)
                                // Eliminar fragmento inmediatamente para ahorrar espacio
                                File(chunkPath).delete()
                                
                                processedMinutes += 2
                                withContext(Dispatchers.Main) {
                                    _progressMinutes.value = "${minOf(processedMinutes, totalMinutes)} / $totalMinutes min"
                                }
                                chunkResult
                            }
                        }
                    }.awaitAll().joinToString(" ")
                } else {
                    val result = transcribeFile(processedPath)
                    withContext(Dispatchers.Main) {
                        _progressMinutes.value = "Completado"
                    }
                    result
                }

                val duration = (System.currentTimeMillis() - startTime) / 1000
                val audioFile = File(path)
                val audioName = audioFile.nameWithoutExtension
                val timeStamp = SimpleDateFormat("HH.dd.MM", Locale.getDefault()).format(Date())
                val txtFileName = "$audioName-$timeStamp.txt"
                
                saveTranscriptionToFile(txtFileName, text)

                if (processedPath != path) {
                    File(processedPath).delete()
                }

                withContext(Dispatchers.Main) {
                    _transcriptionResult.value = text
                    _statusMessage.value = "Procesado en ${duration}s."
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Transcripción cancelada."
                        _isLoading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Error: ${e.localizedMessage}"
                        _isLoading.value = false
                    }
                    Log.e("GalleryViewModel", "Error en el proceso", e)
                }
            } finally {
                // Limpiar cualquier residuo en caso de error o cancelación
                cleanTempFolders()
            }
        }
    }

    fun cancelTranscription() {
        transcriptionJob?.cancel()
    }

    private fun cleanTempFolders() {
        val context = getApplication<Application>()
        File(context.filesDir, "audioPartes").deleteRecursively()
        File(context.cacheDir, "audio_temp").deleteRecursively()
    }

    private suspend fun splitAudioPersistently(inputPath: String, originalName: String): List<String> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val outputDir = File(context.filesDir, "audioPartes")
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()

        // Formato solicitado: parteN.nombreAudio.hora.dia.mes.wav
        val timeStamp = SimpleDateFormat("HH.dd.MM", Locale.getDefault()).format(Date())
        val nameBase = originalName.substringBeforeLast(".")
        val outputPattern = File(outputDir, "parte%d.$nameBase.$timeStamp.wav").absolutePath
        
        val command = "-y -i \"$inputPath\" -f segment -segment_time 120 -c copy \"$outputPattern\""
        
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) {
            outputDir.listFiles()?.sortedBy { it.name }?.map { it.absolutePath } ?: listOf(inputPath)
        } else {
            listOf(inputPath)
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
        val recognizer = Recognizer(model, 16000.0f)
        val inputStream = FileInputStream(path)
        val buffer = ByteArray(16384) // Aumentado de 4KB a 16KB
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
        voskModel = null
    }
}
