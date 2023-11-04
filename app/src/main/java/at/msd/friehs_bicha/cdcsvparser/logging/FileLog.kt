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
        private lateinit var m_context: Context
        private var logIsEnabled = true
        private var isInitialized = false
        private var LOG_FILENAME = "CDCsvParser.log"
        private val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private var maxLogLevel = Log.DEBUG


        /**
         * Initialize the FileLog
         * @param context The context of the application
         * @param logFilename The filename of the log file
         */
        fun init(context: Context, logFilename: String = "log/CDCsvParser.log", logEnabled: Boolean = true, maxLogLevel: Int = Log.DEBUG) {
            logIsEnabled = logEnabled
            if (!logIsEnabled) return
            m_context = context
            LOG_FILENAME = logFilename
            FileLog.maxLogLevel = maxLogLevel

            val logFile = File(m_context.filesDir, LOG_FILENAME)
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
            } else if (logFile.parentFile == null || logFile.parentFile?.exists() == false) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
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


        fun setMaxLogLevel(maxLogLevel: Int) {
            FileLog.maxLogLevel = maxLogLevel
        }

        fun getMaxLogLevel(): Int {
            return maxLogLevel
        }

        fun setLogEnabled(logEnabled: Boolean) {
            logIsEnabled = logEnabled
        }

        fun getLogEnabled(): Boolean {
            return logIsEnabled
        }

        fun getLogFilename(): String {
            return LOG_FILENAME
        }

        fun setLogFilename(logFilename: String) {
            LOG_FILENAME = logFilename
        }

        fun getLogSize(): Int {
            val logFile = File(m_context.filesDir, LOG_FILENAME)
            return logFile.readLines().size
        }

        fun getLog(): String {
            val logFile = File(m_context.filesDir, LOG_FILENAME)
            return logFile.readText()
        }

        fun clearLog() {
            val logFile = File(m_context.filesDir, LOG_FILENAME)
            logFile.writeText("")
        }

        fun getLogFiles(): File {
            return File(m_context.filesDir, LOG_FILENAME)
        }


        /**
         * Send a VERBOSE log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *       the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun v(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.VERBOSE > maxLogLevel) return
            Log.v(tag, message)
            if (!isInitialized) return
            writeToFile(Log.VERBOSE, tag, message)
        }

        /**
         * Send a DEBUG log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun d(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.DEBUG > maxLogLevel) return
            Log.d(tag, message)
            if (!isInitialized) return
            writeToFile(Log.DEBUG, tag, message)
        }


        /**
         * Send a INFO log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun i(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.INFO > maxLogLevel) return
            Log.i(tag, message)
            if (!isInitialized) return
            writeToFile(Log.INFO, tag, "Info: $message")
        }


        /**
         * Send a ERROR log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun e(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.ERROR > maxLogLevel) return
            Log.e(tag, message)
            if (!isInitialized) return
            writeToFile(Log.ERROR, tag, "Error: $message")
        }


        /**
         * Send a WARN log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *        the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun w(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.WARN > maxLogLevel) return
            Log.w(tag, message)
            if (!isInitialized) return
            writeToFile(Log.WARN, tag, "Warning: $message")
        }

        private fun writeToFile(logLevel: Int, tag: String?, message: String) {
            val logFile = File(m_context.filesDir, LOG_FILENAME)
            val fileWriter = FileWriter(logFile, true)
            val timeStamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.GERMANY).format(Date())
            val logLevelString = logLevelToString(logLevel)
            fileWriter.write("$timeStamp $logLevelString $tag: $message\n")
            fileWriter.close()
        }

        private fun logLevelToString(logLevel: Int) = when (logLevel) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }
}