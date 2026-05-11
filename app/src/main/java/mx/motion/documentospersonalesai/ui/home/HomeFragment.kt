package mx.motion.documentospersonalesai.ui.home

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mx.motion.documentospersonalesai.R
import mx.motion.documentospersonalesai.databinding.FragmentHomeBinding
import mx.motion.documentospersonalesai.databinding.ItemAttachmentBinding
import mx.motion.documentospersonalesai.utils.FileHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var attachmentAdapter: AttachmentAdapter

    private var loadingDialog: AlertDialog? = null
    private var photoUri: Uri? = null
    private val attachedImagePaths = mutableListOf<String>()

    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                val currentText = binding.etInput.text.toString()
                if (currentText.isNotEmpty() && !currentText.endsWith(" ")) {
                    binding.etInput.append(" ")
                }
                binding.etInput.append(spokenText)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            photoUri = it
            copyUriToInternalStorage(it)
            showAttachedInfo()
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            copyPdfToInternalStorage(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val path = tempCameraPath ?: return@registerForActivityResult
            attachedImagePaths.add(path)
            optimizeCapturedPhoto(path)
            binding.etInput.setText("extrae el texto de la foto")
            binding.textAiResponse.text = "Foto lista para procesar."
            showAttachedInfo()
        } else {
            binding.textAiResponse.text = "Error al capturar la foto."
        }
    }

    private var tempCameraPath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()

        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        homeViewModel.promptText.observe(viewLifecycleOwner) {
            binding.textInputAiPrompt.text = it
            binding.layoutPromptContainer.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        homeViewModel.aiResponse.observe(viewLifecycleOwner) {
            binding.textAiResponse.text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
            val scrollAmount = binding.textAiResponse.layout?.getLineTop(binding.textAiResponse.lineCount) ?: 0
            val viewHeight = binding.textAiResponse.height - binding.textAiResponse.paddingTop - binding.textAiResponse.paddingBottom
            if (scrollAmount > viewHeight) {
                binding.textAiResponse.scrollTo(0, scrollAmount - viewHeight)
            }
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showLoadingDialog() else hideLoadingDialog()
        }

        homeViewModel.statusText.observe(viewLifecycleOwner) { status ->
            loadingDialog?.findViewById<TextView>(R.id.tv_status_text)?.apply {
                text = status
                visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }

        homeViewModel.showErrorAlert.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                AlertDialog.Builder(requireContext())
                    .setTitle("Error de Modelo")
                    .setMessage(it)
                    .setPositiveButton("OK") { _, _ -> homeViewModel.clearErrorAlert() }
                    .setOnDismissListener { homeViewModel.clearErrorAlert() }
                    .show()
            }
        }

        binding.btnSend.setOnClickListener { sendQuery() }
        binding.btnMic.setOnClickListener { startVoiceRecognition() }
        binding.btnCamera.setOnClickListener { openCamera() }
        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnPdf.setOnClickListener { pickPdfLauncher.launch(arrayOf("application/pdf")) }

        binding.btnStop.setOnClickListener {
            homeViewModel.stopInference()
        }

        binding.btnCopy.setOnClickListener {
            val textToCopy = binding.textAiResponse.text.toString()
            if (textToCopy.isNotBlank()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Respuesta de IA", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            val textToSave = binding.textAiResponse.text.toString()
            if (textToSave.isNotBlank()) {
                val savedFile = FileHelper.saveTextToDownloads(requireContext(), textToSave)
                if (savedFile != null) {
                    Toast.makeText(requireContext(), "Guardado en Descargas: $savedFile", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.etInput.setOnEditorActionListener { _, actionId, event ->
            val isEnterKeyPressed = event != null && 
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && 
                    event.action == android.view.KeyEvent.ACTION_DOWN
            
            if (actionId == EditorInfo.IME_ACTION_SEND || isEnterKeyPressed) {
                sendQuery()
                true // Consumir el evento para evitar saltos de línea
            } else {
                false
            }
        }

        return root
    }

    private fun setupRecyclerView() {
        attachmentAdapter = AttachmentAdapter(attachedImagePaths) { path ->
            attachedImagePaths.remove(path)
            showAttachedInfo()
        }
        binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvAttachments.adapter = attachmentAdapter
    }

    private fun sendQuery() {
        val query = binding.etInput.text.toString()
        if (query.isNotBlank()) {
            homeViewModel.sendPrompt(query, attachedImagePaths.toList())
            hideKeyboard()
            binding.etInput.text.clear()
            removeAttachedFile()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora para dictar tu instrucción")
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "El dictado por voz no está disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeAttachedFile() {
        attachedImagePaths.clear()
        photoUri = null
        showAttachedInfo()
    }

    private fun showAttachedInfo() {
        attachmentAdapter.notifyDataSetChanged()
        binding.layoutAttachedContainer.visibility = if (attachedImagePaths.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun copyPdfToInternalStorage(uri: Uri) {
        try {
            val pdfFolder = File(requireContext().filesDir, "fotosPDF")
            if (!pdfFolder.exists()) pdfFolder.mkdirs()

            var pdfName = "document"
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    pdfName = cursor.getString(nameIndex).replace(".pdf", "", ignoreCase = true)
                }
            }

            val timeStamp: String = SimpleDateFormat("HH.dd.MM", Locale.getDefault()).format(Date())
            val tempFile = File(requireContext().cacheDir, "temp.pdf")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val maxSize = 1080
                val ratio = Math.min(maxSize.toFloat() / page.width, maxSize.toFloat() / page.height)
                val newWidth = (page.width * ratio).toInt()
                val newHeight = (page.height * ratio).toInt()

                val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val imageFile = File(pdfFolder, "pagina${i + 1}-${pdfName}-${timeStamp}.png")
                imageFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                attachedImagePaths.add(imageFile.absolutePath)
                bitmap.recycle()
                page.close()
            }

            renderer.close()
            pfd.close()
            tempFile.delete()

            binding.etInput.setText("analiza este documento pdf")
            showAttachedInfo()
        } catch (e: Exception) {
            binding.textAiResponse.text = "Error al procesar PDF: ${e.localizedMessage}"
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
            loadingDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
        
        // Actualizar el status inicial si ya existe en el ViewModel
        loadingDialog?.findViewById<TextView>(R.id.tv_status_text)?.apply {
            val status = homeViewModel.statusText.value
            text = status
            visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        tempCameraPath = photoFile.absolutePath
        photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(photoUri!!)
    }

    private fun optimizeCapturedPhoto(path: String) {
        try {
            val file = File(path)
            val originalBitmap = BitmapFactory.decodeFile(path) ?: return
            val maxSize = 1080
            if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                val ratio = Math.min(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                val newWidth = (originalBitmap.width * ratio).toInt()
                val newHeight = (originalBitmap.height * ratio).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                file.outputStream().use { out -> scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                originalBitmap.recycle()
                scaledBitmap.recycle()
            } else {
                originalBitmap.recycle()
            }
        } catch (e: Exception) {}
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = File(requireContext().filesDir, "images")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun copyUriToInternalStorage(uri: Uri) {
        try {
            val photoFile = createImageFile()
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                val maxSize = 1080
                val ratio = Math.min(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                val newWidth = (originalBitmap.width * ratio).toInt()
                val newHeight = (originalBitmap.height * ratio).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                photoFile.outputStream().use { out -> scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                originalBitmap.recycle()
                if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
            }
            attachedImagePaths.add(photoFile.absolutePath)
            binding.etInput.setText("extrae el texto de la foto")
            binding.textAiResponse.text = "Imagen cargada y optimizada."
        } catch (e: Exception) {
            binding.textAiResponse.text = "Error al cargar/optimizar imagen."
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.closeModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class AttachmentAdapter(
        private val paths: List<String>,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<AttachmentAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemAttachmentBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val path = paths[position]
            val file = File(path)
            
            val bitmap = BitmapFactory.decodeFile(path)
            holder.binding.ivThumbnail.setImageBitmap(bitmap)
            
            holder.binding.tvPageNumber.text = file.name.substringBefore("-")
            
            holder.binding.btnRemoveItem.setOnClickListener {
                onRemove(path)
            }
        }

        override fun getItemCount() = paths.size
    }
}
