package mx.motion.documentospersonalesai.ui.grabacion_voz

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import org.vosk.Model
import org.vosk.Recognizer
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale

class GrabacionVozViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _transcriptionResult = MutableLiveData<String>()
    val transcriptionResult: LiveData<String> = _transcriptionResult

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    private val _isModelReady = MutableLiveData<Boolean>(false)
    val isModelReady: LiveData<Boolean> = _isModelReady

    private var voskModel: Model? = null
    private var recordingJob: Job? = null
    private val fullTranscription = StringBuilder()
    private var currentCounterForSession: Int = 1

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
                        _isModelReady.value = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Modelo Vosk no encontrado."
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

        currentCounterForSession = readCounter()
        _isRecording.value = true
        //
        fullTranscription.setLength(0)
        _transcriptionResult.value = ""

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val recognizer = Recognizer(model, sampleRate.toFloat())
            val tempFile = File(getApplication<Application>().cacheDir, "temp_audio.raw")
            val outputStream = FileOutputStream(tempFile)

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                
                while (_isRecording.value == true) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // 1. Guardar datos crudos (PCM)
                        outputStream.write(buffer, 0, read)
                        
                        // 2. Procesar para transcripción
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            val text = extractTextFromJson(recognizer.result)
                            if (text.isNotBlank()) {
                                fullTranscription.append(text).append(" ")
                                withContext(Dispatchers.Main) {
                                    _transcriptionResult.value = fullTranscription.toString().trim()
                                }
                            }
                        } else {
                            val partial = extractPartialTextFromJson(recognizer.partialResult)
                            withContext(Dispatchers.Main) {
                                _transcriptionResult.value = fullTranscription.toString() + partial
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GrabacionVozViewModel", "Error durante grabación", e)
            } finally {
                withContext(NonCancellable) {
                    try {
                        recorder.stop()
                        recorder.release()
                        outputStream.close()
                        recognizer.close()
                    } catch (e: Exception) {
                        Log.e("GrabacionVozViewModel", "Error cerrando recursos", e)
                    }
                    finalizeRecording(tempFile)
                }
            }
        }
    }

    fun stopRecording() {
        if (_isRecording.value == true) {
            _isRecording.value = false
            recordingJob?.cancel()
        }
    }

    private suspend fun finalizeRecording(rawFile: File) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val recordingsDir = File(context.cacheDir, "temp_mp3")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()

            val fileName = "${currentCounterForSession}_personal_audio.mp3"
            val mp3File = File(recordingsDir, fileName)

            // Convertir PCM RAW a MP3 usando FFmpeg en cache interna primero
            val command = "-y -f s16le -ar 16000 -ac 1 -i \"${rawFile.absolutePath}\" -b:a 128k \"${mp3File.absolutePath}\""
            
            Log.d("GrabacionVozViewModel", "Convirtiendo a MP3: $command")
            val session = FFmpegKit.execute(command)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                rawFile.delete()
                
                // Mover el archivo a la carpeta Downloads pública usando MediaStore
                val success = saveToDownloads(mp3File)
                
                if (success) {
                    incrementCounter(currentCounterForSession + 1)
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        _statusMessage.value = "guardado en carpeta de descargas: $fileName"
                    } else {
                        _statusMessage.value = "Error al exportar a Descargas"
                    }
                }
            } else {
                Log.e("GrabacionVozViewModel", "Error en conversión FFmpeg: ${session.failStackTrace}")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error al guardar MP3"
                }
            }
        }
    }

    private fun readCounter(): Int {
        return try {
            val statsDir = File(getApplication<Application>().filesDir, "estadistica")
            val counterFile = File(statsDir, "grabaciones_counter.txt")
            if (counterFile.exists()) {
                counterFile.readText().trim().toInt()
            } else 1
        } catch (e: Exception) {
            1
        }
    }

    private fun incrementCounter(newValue: Int) {
        try {
            val statsDir = File(getApplication<Application>().filesDir, "estadistica")
            if (!statsDir.exists()) statsDir.mkdirs()
            val counterFile = File(statsDir, "grabaciones_counter.txt")
            counterFile.writeText(newValue.toString())
        } catch (e: Exception) {
            Log.e("GrabacionVozViewModel", "Error incrementando contador", e)
        }
    }

    private fun saveToDownloads(file: File): Boolean {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                file.delete()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("GrabacionVozViewModel", "Error moviendo a descargas", e)
            false
        }
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

    override fun onCleared() {
        super.onCleared()
        _isRecording.value = false
        recordingJob?.cancel()
    }
}
