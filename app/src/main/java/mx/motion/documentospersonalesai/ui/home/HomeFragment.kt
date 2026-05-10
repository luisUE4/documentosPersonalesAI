package mx.motion.documentospersonalesai.ui.home

import android.os.Bundle
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Observar el prompt enviado
        homeViewModel.promptText.observe(viewLifecycleOwner) {
            binding.textInputAiPrompt.text = it
        }

        // Observar la respuesta de la IA
        homeViewModel.aiResponse.observe(viewLifecycleOwner) {
            binding.textAiResponse.text = it
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
            homeViewModel.sendPrompt(query)
            hideKeyboard()
            binding.etInput.text.clear()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
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