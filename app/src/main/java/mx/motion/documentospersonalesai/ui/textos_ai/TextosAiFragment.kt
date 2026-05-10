package mx.motion.documentospersonalesai.ui.textos_ai

import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.R
import mx.motion.documentospersonalesai.databinding.FragmentTextosAiBinding

class TextosAiFragment : Fragment() {

    private var _binding: FragmentTextosAiBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TextosAiViewModel
    private var loadingDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(TextosAiViewModel::class.java)
        _binding = FragmentTextosAiBinding.inflate(inflater, container, false)
        
        setupUI()
        observeViewModel()
        
        viewModel.setupModel()
        
        return binding.root
    }

    private fun setupUI() {
        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        binding.btnSend.setOnClickListener {
            val instruction = binding.etInput.text.toString()
            if (instruction.isBlank()) {
                Toast.makeText(context, "Escribe una instrucción", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
    }

    private fun observeViewModel() {
        viewModel.aiResponse.observe(viewLifecycleOwner) { response ->
            binding.textAiResponse.text = response
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