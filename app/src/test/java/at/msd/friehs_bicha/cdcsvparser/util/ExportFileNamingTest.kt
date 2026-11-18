package at.msd.friehs_bicha.cdcsvparser.util

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNamingTest {

    @Test
    fun suggestedNameIsDeterministicForThePickTime() {
        val now = LocalDateTime.of(2026, 11, 18, 5, 7, 9)
        assertEquals("cdcsvparser-export-2026-11-18-05-07-09.csv",
            ExportFileNaming.suggestedName(now))
    }

    @Test
    fun suggestedNameAlwaysMatchesTheArchivePattern() {
        val now = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        val name = ExportFileNaming.suggestedName(now)
        assertTrue(name.startsWith("cdcsvparser-export-"))
        assertTrue(name.endsWith(".csv"))
        // "cdcsvparser-export-" (19) + yyyy-MM-dd-HH-mm-ss (19) + ".csv" (4).
        assertEquals(19 + 19 + 4, name.length)
    }
}
