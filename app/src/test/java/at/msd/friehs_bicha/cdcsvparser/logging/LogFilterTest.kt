package at.msd.friehs_bicha.cdcsvparser.logging

import org.junit.Assert.assertEquals
import org.junit.Test

/** Plan 010: the settings log viewer's pure filter (JVM testable). */
class LogFilterTest {

    private val head = "2026-10-15 14:50:01.123 "

    private fun line(level: String, body: String) = "$head$level $body"

    @Test
    fun allModeKeepsEverything() {
        val lines = listOf(
            line("INFO", "Settings: started"),
            line("WARN", "PriceApi: 429 rate limited"),
            "    at com.example.Foo.bar(Foo.kt:1)",
        )
        assertEquals(lines, LogFilter.filter(lines, LogFilter.Mode.ALL))
    }

    @Test
    fun warningModeKeepsWarnAndErrorEntries() {
        val lines = listOf(
            line("INFO", "A: fine"),
            line("WARN", "B: 429 rate limited"),
            line("ERROR", "C: boom"),
            line("DEBUG", "D: after the error"),
        )
        val out = LogFilter.filter(lines, LogFilter.Mode.WARNING)
        assertEquals(
            listOf(line("WARN", "B: 429 rate limited"), line("ERROR", "C: boom")),
            out,
        )
    }

    @Test
    fun errorModeKeepsErrorLevelAndCrashEntries() {
        val lines = listOf(
            line("INFO", "A: fine"),
            line("WARN", "B: something Exception-ish in the message"),
            line("WARN", "C: plain warning"),
            line("ERROR", "D: boom"),
        )
        val out = LogFilter.filter(lines, LogFilter.Mode.ERROR)
        assertEquals(
            listOf(
                line("WARN", "B: something Exception-ish in the message"),
                line("ERROR", "D: boom"),
            ),
            out,
        )
    }

    @Test
    fun crashHookMultiLineDumpIsKeptWhole() {
        // The plan 007 A2 crash entry as it arrives after a file read:
        // an ERROR head line (tag "Crash", message "uncaught on 'main': …")
        // followed by stack-frame continuation lines that match no head
        // pattern. Both must survive the ERROR filter together.
        val crashHead = line(
            "ERROR",
            "Crash: uncaught on 'main': java.lang.IndexOutOfBoundsException: Index 3 out of bounds",
        )
        val frame1 = "    at com.example.Chart.draw(Chart.kt:10)"
        val frame2 = "    at com.example.Chart.run(Chart.kt:20)"
        val lines = listOf(
            line("INFO", "before"),
            crashHead,
            frame1,
            frame2,
            line("INFO", "after"),
        )
        assertEquals(
            listOf(crashHead, frame1, frame2),
            LogFilter.filter(lines, LogFilter.Mode.ERROR),
        )
    }

    @Test
    fun continuationLinesTrailingAPlainInfoEntryAreDropped() {
        val lines = listOf(
            line("INFO", "A: multi"),
            "    at com.example.Foo.bar(Foo.kt:1)",
            line("WARN", "B: kept"),
        )
        assertEquals(
            listOf(line("WARN", "B: kept")),
            LogFilter.filter(lines, LogFilter.Mode.WARNING),
        )
    }
}
