package at.msd.friehs_bicha.cdcsvparser.logging

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.*

/**
* This class is used to log to a file
*  when using logging:
* For devs use FileLog.d
* For errors use FileLog.e
* For warnings use FileLog.w
* For info use FileLog.i
 */
class FileLog {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var m_context: Context? = null
        private var isInitialized = false
        private var LOG_FILENAME = "CDCsvParser.log"
        private val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"


        /**
         * Initialize the FileLog
         * @param context The context of the application
         * @param logFilename The filename of the log file
         */
        fun init(context: Context, logFilename: String = "log/CDCsvParser.log") {
            m_context = context
            LOG_FILENAME = logFilename

            val logFile = File(m_context!!.filesDir, LOG_FILENAME)
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
            } else if (logFile.parentFile == null || logFile.parentFile?.exists() == false) {
                logFile.parentFile?.mkdirs()
            } else {
                val lines = logFile.readLines()
                if (lines.size > 1000) {
                    val newLines = lines.subList(500, lines.size)
                    logFile.writeText(newLines.joinToString("\n"))
                }
            }

            isInitialized = true
            d("FileLog", "Initialized")
        }


        /**
         * Send a DEBUG log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun d(tag: String?, message: String) {
            Log.d(tag, message)
            if (!isInitialized) return
            writeToFile(tag, message)
        }


        /**
         * Send a INFO log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun i(tag: String?, message: String) {
            Log.i(tag, message)
            if (!isInitialized) return
            writeToFile(tag, "Info: $message")
        }


        /**
         * Send a ERROR log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun e(tag: String?, message: String) {
            Log.e(tag, message)
            if (!isInitialized) return
            writeToFile(tag, "Error: $message")
        }


        /**
         * Send a WARN log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun w(tag: String?, message: String) {
            Log.w(tag, message)
            if (!isInitialized) return
            writeToFile(tag, "Warning: $message")
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