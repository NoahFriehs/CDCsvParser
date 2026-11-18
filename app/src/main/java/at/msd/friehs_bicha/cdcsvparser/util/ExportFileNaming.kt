package at.msd.friehs_bicha.cdcsvparser.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The suggested file name of the SAF data export: the app prefix + the
 * pick time (so repeated exports of the same state stay separate
 * archives) + the .csv extension. Machine-English and locale-neutral
 * (the artifact is an archive, not a display string).
 */
object ExportFileNaming {

    private val PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    /** The suggested name for an export picked at [now]. */
    fun suggestedName(now: LocalDateTime): String =
        "cdcsvparser-export-" + PATTERN.format(now) + ".csv"
}
