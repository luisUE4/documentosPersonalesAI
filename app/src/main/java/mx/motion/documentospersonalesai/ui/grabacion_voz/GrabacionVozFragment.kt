package mx.motion.documentospersonalesai.ui.grabacion_voz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.motion.documentospersonalesai.R
import mx.motion.documentospersonalesai.databinding.FragmentGrabacionVozBinding
import mx.motion.documentospersonalesai.utils.FileHelper

class GrabacionVozFragment : Fragment() {

    private var _binding: FragmentGrabacionVozBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: GrabacionVozViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            Toast.makeText(context, "Permiso de audio denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(GrabacionVozViewModel::class.java)
        _binding = FragmentGrabacionVozBinding.inflate(inflater, container, false)
        
        setupUI()
        observeViewModel()
        
        return binding.root
    }

    private fun setupUI() {
        binding.textAiResponse.movementMethod = ScrollingMovementMethod()

        binding.btnRecord.setOnClickListener {
            checkPermissionAndToggle()
        }

        binding.btnCopy.setOnClickListener {
            val text = binding.textAiResponse.text.toString()
            if (text.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Transcripción", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            val text = binding.textAiResponse.text.toString()
            if (text.isNotBlank()) {
                val savedFile = FileHelper.saveTextToDownloads(requireContext(), text)
                if (savedFile != null) {
                    Toast.makeText(context, "Guardado en Descargas: $savedFile", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionAndToggle() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.toggleRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.transcriptionResult.observe(viewLifecycleOwner) { text ->
            binding.textAiResponse.text = text
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                binding.tvStatusMessage.text = msg
                binding.tvStatusMessage.visibility = View.VISIBLE
                
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(2000)
                    binding.tvStatusMessage.visibility = View.GONE
                }
            }
        }

        viewModel.isModelReady.observe(viewLifecycleOwner) { isReady ->
            binding.btnRecord.isEnabled = isReady
            if (isReady) {
                binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0b3159"))
            } else {
                binding.btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#03203f"))
            }
        }

        viewModel.isRecording.observe(viewLifecycleOwner) { isRecording ->
            if (isRecording) {
                binding.btnRecord.setImageResource(R.drawable.ic_stop_square)
            } else {
                binding.btnRecord.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopRecording()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
