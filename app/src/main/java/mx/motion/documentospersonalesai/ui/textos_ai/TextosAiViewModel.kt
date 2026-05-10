package mx.motion.documentospersonalesai.ui.textos_ai

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TextosAiViewModel : ViewModel() {

   private val _text = MutableLiveData<String>().apply {
      value = "This is textos IA Fragment"
   }
   val text: LiveData<String> = _text
}