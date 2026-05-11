package mx.motion.documentospersonalesai.ui.gallery

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.databinding.FragmentGalleryBinding
import mx.motion.documentospersonalesai.utils.FileHelper
import java.io.File
import java.io.FileOutputStream

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var galleryViewModel: GalleryViewModel

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            copyAudioToInternalStorage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        galleryViewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        // Observadores
        galleryViewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            binding.textStatus.text = message
        }

        galleryViewModel.transcriptionResult.observe(viewLifecycleOwner) { text ->
            binding.textAiResponse.text = text
        }

        galleryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnAudioFile.isEnabled = !isLoading
            binding.layoutLoadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        galleryViewModel.progressMinutes.observe(viewLifecycleOwner) { progress ->
            binding.tvLoadingProgress.text = progress
        }

        binding.btnCancelTranscription.setOnClickListener {
            galleryViewModel.cancelTranscription()
        }

        binding.btnAudioFile.setOnClickListener {
            binding.textAiResponse.text = ""
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnCopy.setOnClickListener {
            val textToCopy = binding.textAiResponse.text.toString()
            if (textToCopy.isNotBlank()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Transcripción de Audio", textToCopy)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(requireContext(), "Texto copiado al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            val textToSave = binding.textAiResponse.text.toString()
            if (textToSave.isNotBlank()) {
                val savedFile = FileHelper.saveTextToDownloads(requireContext(), textToSave)
                if (savedFile != null) {
                    android.widget.Toast.makeText(requireContext(), "Guardado en Descargas: $savedFile", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(requireContext(), "Error al guardar", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        return root
    }

    private fun copyAudioToInternalStorage(uri: Uri) {
        try {
            val audioFolder = File(requireContext().filesDir, "audios")
            if (!audioFolder.exists()) audioFolder.mkdirs()

            var fileName = "audio_${System.currentTimeMillis()}.wav"
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val destFile = File(audioFolder, fileName)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            binding.textStatus.text = "Procesando $fileName..."
            
            // Iniciar trascripción inmediatamente al seleccionar
            galleryViewModel.transcribeAudio(destFile.absolutePath, fileName)
            
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Error al cargar audio: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
