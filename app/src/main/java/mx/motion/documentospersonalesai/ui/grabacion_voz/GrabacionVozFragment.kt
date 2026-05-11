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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.R
import mx.motion.documentospersonalesai.databinding.FragmentGrabacionVozBinding

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
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.isRecording.observe(viewLifecycleOwner) { isRecording ->
            if (isRecording) {
                binding.btnRecord.text = "Detener Grabación"
                binding.btnRecord.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
                binding.tvTimer.visibility = View.VISIBLE
            } else {
                binding.btnRecord.text = "Grabar Voz"
                binding.btnRecord.setIconResource(android.R.drawable.ic_btn_speak_now)
                binding.tvTimer.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopRecording()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
