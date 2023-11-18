package at.msd.friehs_bicha.cdcsvparser.logging

import android.util.Log
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars.applicationContext
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        private var logIsEnabled = true
        private var isInitialized = false
        private var LOG_FILENAME = "CDCsvParser.log"
        private val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private val dateTimeFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)
        private var maxLogLevel = Log.DEBUG


        /**
         * Initialize the FileLog
         * @param logFilename The filename of the log file
         * @param logEnabled If logging is enabled
         * @param maxLogLevel The max log level
         * @return If the initialization was successful or not
         */
        fun init(logFilename: String? = null, logEnabled: Boolean = true, maxLogLevel: Int = -1): Boolean {
            logIsEnabled = logEnabled
            if (!logIsEnabled) return isInitialized
            LOG_FILENAME = if (logFilename == null) {
                PreferenceHelper.getLogFilename(applicationContext)
            } else {
                PreferenceHelper.setLogFilename(applicationContext, logFilename)
                logFilename
            }
            val mLLI = maxLogLevel
            if (mLLI != -1 && mLLI < Log.VERBOSE || mLLI > Log.ERROR) {
                e("FileLog", "Invalid max log level $mLLI from ${Thread.currentThread().stackTrace[3]}")
                return false
            }
            val mLLS = PreferenceHelper.getMaxLogLevel(applicationContext)

            this.maxLogLevel = if (mLLI == -1) {
                mLLS
            } else {
                PreferenceHelper.setMaxLogLevel(applicationContext, mLLI)
                mLLI
            }

            createLogFileIfNeeded()

            isInitialized = true
            d("FileLog", "Initialized")
            return isInitialized
        }


        fun setMaxLogLevel(maxLogLevel: Int) {
            if (maxLogLevel < Log.VERBOSE || maxLogLevel > Log.ERROR)
            {
                e("FileLog", "Invalid max log level $maxLogLevel from ${Thread.currentThread().stackTrace[3]}")
                return
            }
            FileLog.maxLogLevel = maxLogLevel
            PreferenceHelper.setMaxLogLevel(applicationContext, maxLogLevel)
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
            PreferenceHelper.setLogFilename(applicationContext, logFilename)
            createLogFileIfNeeded()
        }

        private fun createLogFileIfNeeded() {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
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
        }

        fun getLogSize(): Int {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            return logFile.useLines { lines -> lines.count() }
        }

        fun getLog(): String {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            return logFile.useLines { lines -> lines.joinToString("\n") }
        }

        fun getLogLines(): List<String> {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            return logFile.useLines { lines -> lines.toList() }

        }

        fun clearLog() {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            val fileWriter = FileWriter(logFile, false)
            fileWriter.close()
        }

        fun getLogFiles(): File {
            return File(applicationContext.filesDir, LOG_FILENAME)
        }


        /**
         * Send a VERBOSE log message.
         * @param tag Used to identify the source of a log message.  It usually identifies
         *       the class or activity where the log call occurs.
         * @param message The message you would like logged.
         */
        fun v(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.VERBOSE < maxLogLevel) return
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
            if (Log.DEBUG < maxLogLevel) return
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
            if (Log.INFO < maxLogLevel) return
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
            if (Log.ERROR < maxLogLevel) return
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
            if (Log.WARN < maxLogLevel) return
            Log.w(tag, message)
            if (!isInitialized) return
            writeToFile(Log.WARN, tag, "Warning: $message")
        }

        private fun writeToFile(logLevel: Int, tag: String?, message: String) {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            val printWriter = PrintWriter(BufferedWriter(FileWriter(logFile, true)))
            val timeStamp = LocalDateTime.now().format(dateTimeFormatter)
            val logLevelString = logLevelToString(logLevel)
            printWriter.println("$timeStamp $logLevelString $tag: $message")
            printWriter.close()
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