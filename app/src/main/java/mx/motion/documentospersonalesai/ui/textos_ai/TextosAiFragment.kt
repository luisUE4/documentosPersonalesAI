package mx.motion.documentospersonalesai.ui.textos_ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.R
import mx.motion.documentospersonalesai.databinding.FragmentTextosAiBinding
import mx.motion.documentospersonalesai.utils.FileHelper
import java.util.Locale

class TextosAiFragment : Fragment() {

    private var _binding: FragmentTextosAiBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TextosAiViewModel
    private var loadingDialog: AlertDialog? = null

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(TextosAiViewModel::class.java)
        _binding = FragmentTextosAiBinding.inflate(inflater, container, false)
        
        setupUI()
        observeViewModel()
        
        return binding.root
    }

    private fun setupUI() {
        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        binding.btnSend.setOnClickListener { sendQuery() }

        binding.btnMic.setOnClickListener { startVoiceRecognition() }

        binding.etInput.setOnEditorActionListener { _, actionId, event ->
            val isEnterKeyPressed = event != null &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN

            if (actionId == EditorInfo.IME_ACTION_SEND || isEnterKeyPressed) {
                sendQuery()
                true
            } else {
                false
            }
        }

        binding.btnStop.setOnClickListener {
            viewModel.stopInference()
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text
                    if (text != null) {
                        binding.textoParaAnalizar.append(text)
                    }
                }
            }
        }

        binding.btnCopy.setOnClickListener {
            val text = binding.textAiResponse.text.toString()
            if (text.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Respuesta IA", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            val textToSave = binding.textAiResponse.text.toString()
            if (textToSave.isNotBlank()) {
                val savedFile = FileHelper.saveTextToDownloads(requireContext(), textToSave)
                if (savedFile != null) {
                    Toast.makeText(context, "Guardado en Descargas: $savedFile", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendQuery() {
        val instruction = binding.etInput.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(context, "Escribe una instrucción", Toast.LENGTH_SHORT).show()
            return
        }

        val manualText = binding.textoParaAnalizar.text.toString()
        val fullContext = if (manualText.isNotBlank()) {
            "TEXTO PARA ANALIZAR:\n$manualText"
        } else {
            ""
        }

        viewModel.sendPrompt(instruction, fullContext)
        binding.etInput.text.clear()
        hideKeyboard()
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

    private fun observeViewModel() {
        viewModel.aiResponse.observe(viewLifecycleOwner) { response ->
            binding.textAiResponse.text = response
        }

        viewModel.statusText.observe(viewLifecycleOwner) { status ->
            loadingDialog?.findViewById<TextView>(R.id.tv_status_text)?.apply {
                text = status
                visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }

        viewModel.promptText.observe(viewLifecycleOwner) { prompt ->
            if (prompt.isNotBlank()) {
                binding.layoutPromptContainer.visibility = View.VISIBLE
                binding.textInputAiPrompt.text = prompt
            } else {
                binding.layoutPromptContainer.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showLoadingDialog() else hideLoadingDialog()
            binding.btnSend.isEnabled = !isLoading
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
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

        loadingDialog?.findViewById<TextView>(R.id.tv_status_text)?.apply {
            val status = viewModel.statusText.value
            text = status
            visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.closeModel()
        _binding = null
    }
}