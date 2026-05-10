package mx.motion.documentospersonalesai.ui.home

import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private var photoUri: Uri? = null
    private var currentPhotoPath: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            photoUri = it
            // Para LiteRT-LM necesitamos la ruta del archivo. 
            // Como PickVisualMedia devuelve una URI de contenido, la copiaremos a nuestra carpeta de imágenes.
            copyUriToInternalStorage(it)
            showPhotoInfo()
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            optimizeCapturedPhoto() // Redimensionar después de capturar
            binding.etInput.setText("extrae el texto de la foto")
            binding.textAiResponse.text = "Foto lista para procesar."
            showPhotoInfo()
        } else {
            binding.textAiResponse.text = "Error al capturar la foto."
            currentPhotoPath = null
            binding.layoutAttachedPhoto.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Habilitar scroll en el TextView de respuesta
        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        // Observar el prompt enviado
        homeViewModel.promptText.observe(viewLifecycleOwner) {
            binding.textInputAiPrompt.text = it
        }

        // Observar la respuesta de la IA
        homeViewModel.aiResponse.observe(viewLifecycleOwner) {
            binding.textAiResponse.text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
        }

        // Observar alertas de error
        homeViewModel.showErrorAlert.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                AlertDialog.Builder(requireContext())
                    .setTitle("Error de Modelo")
                    .setMessage(it)
                    .setPositiveButton("OK") { _, _ ->
                        homeViewModel.clearErrorAlert()
                    }
                    .setOnDismissListener {
                        homeViewModel.clearErrorAlert()
                    }
                    .show()
            }
        }

        binding.btnSend.setOnClickListener {
            sendQuery()
        }

        binding.btnCamera.setOnClickListener {
            openCamera()
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnRemovePhoto.setOnClickListener {
            removeAttachedPhoto()
        }

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendQuery()
                true
            } else {
                false
            }
        }

        return root
    }

    private fun sendQuery() {
        val query = binding.etInput.text.toString()
        if (query.isNotBlank()) {
            homeViewModel.sendPrompt(query, currentPhotoPath)
            hideKeyboard()
            binding.etInput.text.clear()
            removeAttachedPhoto() // Limpiar UI después de enviar
        }
    }

    private fun removeAttachedPhoto() {
        currentPhotoPath = null
        photoUri = null
        binding.layoutAttachedPhoto.visibility = View.GONE
    }

    private fun showPhotoInfo() {
        currentPhotoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)
                val info = "${file.name} (${options.outWidth}x${options.outHeight} px)"
                binding.tvPhotoInfo.text = info
                binding.layoutAttachedPhoto.visibility = View.VISIBLE
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        currentPhotoPath = photoFile.absolutePath
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(photoUri!!)
    }

    private fun optimizeCapturedPhoto() {
        currentPhotoPath?.let { path ->
            try {
                val file = File(path)
                val originalBitmap = BitmapFactory.decodeFile(path) ?: return
                
                val maxSize = 1080
                val width = originalBitmap.width
                val height = originalBitmap.height
                
                if (width > maxSize || height > maxSize) {
                    val (newWidth, newHeight) = if (width > height) {
                        val ratio = width.toFloat() / maxSize
                        maxSize to (height / ratio).toInt()
                    } else {
                        val ratio = height.toFloat() / maxSize
                        (width / ratio).toInt() to maxSize
                    }
                    
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                    file.outputStream().use { outputStream ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    originalBitmap.recycle()
                    scaledBitmap.recycle()
                } else {
                    originalBitmap.recycle()
                }
            } catch (e: Exception) {
                // Manejar error de optimización silenciosamente o loguear
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = File(requireContext().filesDir, "images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun copyUriToInternalStorage(uri: Uri) {
        try {
            val photoFile = createImageFile()
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                // Decodificar el bitmap original
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                
                // Calcular dimensiones para resize (max 1080p manteniendo aspecto)
                val maxSize = 1080
                val width = originalBitmap.width
                val height = originalBitmap.height
                
                val (newWidth, newHeight) = if (width > height) {
                    if (width > maxSize) {
                        val ratio = width.toFloat() / maxSize
                        maxSize to (height / ratio).toInt()
                    } else width to height
                } else {
                    if (height > maxSize) {
                        val ratio = height.toFloat() / maxSize
                        (width / ratio).toInt() to maxSize
                    } else width to height
                }

                // Crear el bitmap escalado
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                
                // Guardar en el archivo con compresión JPEG
                photoFile.outputStream().use { outputStream ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
                
                // Liberar memoria de los bitmaps
                originalBitmap.recycle()
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
            }
            currentPhotoPath = photoFile.absolutePath
            binding.etInput.setText("extrae el texto de la foto")
            binding.textAiResponse.text = "Imagen de galería cargada y optimizada."
        } catch (e: Exception) {
            binding.textAiResponse.text = "Error al cargar/optimizar imagen."
        }
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.setupModel()
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.closeModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}