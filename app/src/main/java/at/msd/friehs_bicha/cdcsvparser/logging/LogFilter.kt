package at.msd.friehs_bicha.cdcsvparser.logging

/**
 * Plan 010: the pure line filter behind the settings log viewer.
 *
 * FileLog lines look like
 * `yyyy-MM-dd HH:mm:ss.SSS LEVEL TAG: message` (LEVEL one of
 * VERBOSE/DEBUG/INFO/WARN/ERROR). Multi-line messages — most notably the
 * plan 007 "Crash" hook's stack dump — continue on FOLLOWING lines that
 * carry no head pattern; those belong to the entry they trail, so a
 * matching head keeps its run of continuation lines with it.
 */
object LogFilter {

    enum class Mode { ALL, WARNING, ERROR }

    private const val VERBOSE = 0
    private const val DEBUG = 1
    private const val INFO = 2
    private const val WARN = 3
    private const val ERROR = 4

    private val headPattern =
        Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} (VERBOSE|DEBUG|INFO|WARN|ERROR)\b""")

    private fun levelOf(line: String): Int =
        when (val level = headPattern.find(line)?.groupValues?.get(1)) {
            "VERBOSE" -> VERBOSE
            "DEBUG" -> DEBUG
            "INFO" -> INFO
            "WARN" -> WARN
            "ERROR" -> ERROR
            else -> -1
        }

    /** A head qualifies for ERROR mode at level == ERROR or when its
     *  message names a crash/exception (the "Crash" hook, uncaught
     *  throwables on lower levels). */
    private fun isErrorHead(level: Int, line: String): Boolean =
        level == ERROR || line.contains("Crash") || line.contains("Exception")

    fun filter(lines: List<String>, mode: Mode): List<String> = when (mode) {
        Mode.ALL -> lines
        else -> buildList {
            var keepRun = false
            for (line in lines) {
                val level = levelOf(line)
                keepRun = when (mode) {
                    Mode.WARNING -> level in (WARN..ERROR) || keepRun && level < 0
                    Mode.ERROR -> isErrorHead(level, line) || keepRun && level < 0
                    Mode.ALL -> true
                }
                if (keepRun) add(line)
            }
        }
    }
}
