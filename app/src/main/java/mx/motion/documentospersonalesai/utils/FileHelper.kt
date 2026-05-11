package mx.motion.documentospersonalesai.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object FileHelper {

    private const val COUNTER_FILE_NAME = "ia_texto_counter.txt"
    private const val STATS_DIR = "estadistica"

    fun getNextCounter(context: Context): Int {
        return try {
            val statsDir = File(context.filesDir, STATS_DIR)
            val counterFile = File(statsDir, COUNTER_FILE_NAME)
            if (counterFile.exists()) {
                counterFile.readText().trim().toInt()
            } else 1
        } catch (e: Exception) {
            1
        }
    }

    private fun incrementCounter(context: Context, currentValue: Int) {
        try {
            val statsDir = File(context.filesDir, STATS_DIR)
            if (!statsDir.exists()) statsDir.mkdirs()
            val counterFile = File(statsDir, COUNTER_FILE_NAME)
            counterFile.writeText((currentValue + 1).toString())
        } catch (e: Exception) {
            Log.e("FileHelper", "Error incrementando contador", e)
        }
    }

    fun saveTextToDownloads(context: Context, text: String): String? {
        val counter = getNextCounter(context)
        val fileName = "ia_texto_$counter.txt"
        
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(text.toByteArray())
                }
                incrementCounter(context, counter)
                fileName
            }
        } catch (e: Exception) {
            Log.e("FileHelper", "Error guardando texto en descargas", e)
            null
        }
    }
}
