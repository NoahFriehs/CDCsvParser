package at.msd.friehs_bicha.cdcsvparser.logging

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.*

class FileLog {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var m_context: Context? = null
        private var isInitialized = false
        private var LOG_FILENAME = "CDCsvParser.log"
        private val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        fun init(context: Context, logFilename: String = "log/CDCsvParser.log") {
            m_context = context
            LOG_FILENAME = logFilename

            val logFile = File(m_context!!.filesDir, LOG_FILENAME)
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
            }
            else if (logFile.length() > 1000) {
                logFile.delete()
                logFile.createNewFile()
            }

            isInitialized = true
            d("FileLog", "Initialized")
        }

        fun d(tag: String?, message: String) {
            Log.d(tag, message)
            if (!isInitialized) return
            writeToFile(tag, message)
        }

        private fun writeToFile(tag: String?, message: String) {
            val logFile = File(m_context!!.filesDir, LOG_FILENAME)
            val fileWriter = FileWriter(logFile, true)
            val timeStamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.GERMANY).format(Date())
            fileWriter.write("$timeStamp $tag: $message\n")
            fileWriter.close()
        }
    }
}