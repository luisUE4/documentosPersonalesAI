package mx.motion.documentospersonalesai.ui.textos_ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import mx.motion.documentospersonalesai.databinding.FragmentTextosAiBinding

class TextosAiFragment : Fragment() {

   private var _binding: FragmentTextosAiBinding? = null

   // This property is only valid between onCreateView and
   // onDestroyView.
   private val binding get() = _binding!!

   override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
   ): View {
      val textosAiViewModel =
         ViewModelProvider(this).get(TextosAiViewModel::class.java)

      _binding = FragmentTextosAiBinding.inflate(inflater, container, false)
      val root: View = binding.root

      val textView: TextView = binding.textTextosAi
      textosAiViewModel.text.observe(viewLifecycleOwner) {
         textView.text = it
      }
      return root
   }

   override fun onDestroyView() {
      super.onDestroyView()
      _binding = null
   }
}