package mx.motion.documentospersonalesai.ui.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

   private var _binding: FragmentHomeBinding? = null

   // This property is only valid between onCreateView and
   // onDestroyView.
   private val binding get() = _binding!!

   override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
   ): View {
      val homeViewModel =
         ViewModelProvider(this).get(HomeViewModel::class.java)

      _binding = FragmentHomeBinding.inflate(inflater, container, false)
      val root: View = binding.root

      val textView: TextView = binding.textHome
      homeViewModel.text.observe(viewLifecycleOwner) {
         textView.text = it
      }

      binding.btnSend.setOnClickListener {
         val texto = binding.etInput.text.toString()
         // Aquí mandas el texto a tu acción
         AlertDialog.Builder(requireContext())
            .setTitle("Alerta")
            .setMessage(texto)
            .setPositiveButton("Aceptar", null)
            .show()
      }

      return root
   }

   override fun onDestroyView() {
      super.onDestroyView()
      _binding = null
   }
}