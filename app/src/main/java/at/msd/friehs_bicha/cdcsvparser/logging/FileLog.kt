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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * This class is used to log to a file.
 * When using logging:
 * For devs use FileLog.d
 * For errors use FileLog.e
 * For warnings use FileLog.w
 * For info use FileLog.i
 *
 * All lines are queued and written by a single daemon writer thread.
 */
class FileLog {

    private data class LogEntry(val level: Int, val tag: String?, val message: String)

    companion object {
        private var logIsEnabled = true
        private var isInitialized = false
        private var LOG_FILENAME = "CDCsvParser.log"
        private val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private val dateTimeFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)

        // A level is written if it is at least this "minimum" level.
        // (Legacy naming: stored as "maxLogLevel" in the preferences.)
        private var minLogLevel = Log.DEBUG

        private val logQueue = LinkedBlockingQueue<LogEntry>()
        private var writerThread: Thread? = null

        // Bumped whenever LOG_FILENAME changes so the writer reopens the file.
        @Volatile
        private var logFileEpoch = 0

        /**
         * Initialize the FileLog
         * @param logFilename The filename of the log file
         * @param logEnabled If logging is enabled
         * @param maxLogLevel The min log level to write (-1 = keep the stored value)
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
            if (maxLogLevel != -1 && (maxLogLevel < Log.VERBOSE || maxLogLevel > Log.ERROR)) {
                e("FileLog", "Invalid min log level $maxLogLevel")
                return false
            }
            val storedLevel = PreferenceHelper.getMaxLogLevel(applicationContext)

            minLogLevel = if (maxLogLevel == -1) {
                storedLevel
            } else {
                PreferenceHelper.setMaxLogLevel(applicationContext, maxLogLevel)
                maxLogLevel
            }

            createLogFileIfNeeded()
            startWriterThread()

            isInitialized = true
            d("FileLog", "Initialized")
            return isInitialized
        }

        private fun startWriterThread() {
            if (writerThread?.isAlive == true) return
            writerThread = thread(name = "filelog-writer", isDaemon = true) {
                var buffer: BufferedWriter? = null
                var openEpoch = Int.MIN_VALUE
                var sinceFlush = 0
                while (true) {
                    val epoch = logFileEpoch
                    if (epoch != openEpoch || buffer == null) {
                        try {
                            buffer?.close()
                        } catch (_: Exception) {
                            // ignore
                        }
                        buffer = BufferedWriter(
                            FileWriter(File(applicationContext.filesDir, LOG_FILENAME), true)
                        )
                        openEpoch = epoch
                        sinceFlush = 0
                    }
                    try {
                        val entry = logQueue.poll(1, TimeUnit.SECONDS)
                        if (entry == null) {
                            if (sinceFlush > 0) {
                                buffer.flush()
                                sinceFlush = 0
                            }
                            continue
                        }
                        val timestamp = LocalDateTime.now().format(dateTimeFormatter)
                        buffer.write("$timestamp ${logLevelToString(entry.level)} ${entry.tag}: ${entry.message}")
                        buffer.newLine()
                        sinceFlush++
                        if (sinceFlush >= 20) {
                            buffer.flush()
                            sinceFlush = 0
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        try {
                            buffer?.close()
                        } catch (_: Exception) {
                            // ignore
                        }
                        buffer = null
                        try {
                            Log.e("FileLog", "Failed to write log: ${e.message}")
                        } catch (_: Exception) {
                            // ignore, logging must never crash the app
                        }
                    }
                }
                try {
                    buffer?.flush()
                    buffer?.close()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        fun setMaxLogLevel(maxLogLevel: Int) {
            if (maxLogLevel < Log.VERBOSE || maxLogLevel > Log.ERROR) {
                e("FileLog", "Invalid min log level $maxLogLevel")
                return
            }
            minLogLevel = maxLogLevel
            PreferenceHelper.setMaxLogLevel(applicationContext, maxLogLevel)
        }

        fun getMaxLogLevel(): Int {
            return minLogLevel
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
            logFileEpoch++
            PreferenceHelper.setLogFilename(applicationContext, logFilename)
            createLogFileIfNeeded()
        }

        private fun createLogFileIfNeeded() {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
            } else if (logFile.length() > 100_000) {
                // Trim very large logs: keep the last half of the lines.
                try {
                    val lines = logFile.readLines()
                    if (lines.size > 1000) {
                        val newLines = lines.subList(500, lines.size)
                        logFile.writeText(newLines.joinToString("\n"))
                    }
                } catch (e: Exception) {
                    Log.w("FileLog", "Could not trim log file: ${e.message}")
                }
            }
        }

        fun getLogSize(): Int {
            return File(applicationContext.filesDir, LOG_FILENAME).useLines { lines -> lines.count() }
        }

        fun getLog(): String {
            return File(applicationContext.filesDir, LOG_FILENAME).useLines { lines -> lines.joinToString("\n") }
        }

        fun getLogLines(): List<String> {
            return File(applicationContext.filesDir, LOG_FILENAME).useLines { lines -> lines.toList() }
        }

        fun clearLog() {
            val logFile = File(applicationContext.filesDir, LOG_FILENAME)
            PrintWriter(BufferedWriter(FileWriter(logFile, false))).close()
        }

        fun getLogFiles(): File {
            return File(applicationContext.filesDir, LOG_FILENAME)
        }

        /**
         * Send a VERBOSE log message.
         * @param tag Used to identify the source of the log message.
         * @param message The message you would like logged.
         */
        fun v(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.VERBOSE < minLogLevel) return
            Log.v(tag, message)
            if (!isInitialized) return
            logQueue.offer(LogEntry(Log.VERBOSE, tag, message))
        }

        /**
         * Send a DEBUG log message.
         * @param tag Used to identify the source of the log message.
         * @param message The message you would like logged.
         */
        fun d(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.DEBUG < minLogLevel) return
            Log.d(tag, message)
            if (!isInitialized) return
            logQueue.offer(LogEntry(Log.DEBUG, tag, message))
        }

        /**
         * Send a INFO log message.
         * @param tag Used to identify the source of the log message.
         * @param message The message you would like logged.
         */
        fun i(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.INFO < minLogLevel) return
            Log.i(tag, message)
            if (!isInitialized) return
            logQueue.offer(LogEntry(Log.INFO, tag, "Info: $message"))
        }

        /**
         * Send a ERROR log message.
         * @param tag Used to identify the source of the log message.
         * @param message The message you would like logged.
         */
        fun e(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.ERROR < minLogLevel) return
            Log.e(tag, message)
            if (!isInitialized) return
            logQueue.offer(LogEntry(Log.ERROR, tag, "Error: $message"))
        }

        /**
         * Send a WARN log message.
         * @param tag Used to identify the source of the log message.
         * @param message The message you would like logged.
         */
        fun w(tag: String?, message: String) {
            if (!logIsEnabled) return
            if (Log.WARN < minLogLevel) return
            Log.w(tag, message)
            if (!isInitialized) return
            logQueue.offer(LogEntry(Log.WARN, tag, "Warning: $message"))
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
