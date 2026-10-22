package at.msd.friehs_bicha.cdcsvparser.general

import java.io.File
import java.security.MessageDigest

/**
 * Pure kernels for the upload history list : content-identity
 * detection on re-import and the "older than 30 days" purge set. No state,
 * no clock of their own — `now` is passed in — so both are covered by the
 * JVM tests.
 */
object HistoryFileUtil {

    /** The list must hold strictly MORE than this many files for the purge action to appear. */
    const val PURGE_THRESHOLD = 100

    /** Files strictly older than this (by mtime) are purgeable. */
    const val PURGE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

    /** Lowercase hex digest; used only for content-identity comparisons. */
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * First file of [existing] that is content-identical to [incomingBytes]:
     * equal-size pre-filter (the cheap miss check), then a full SHA-256
     * compare on the size-equal candidates. Null when nothing matches.
     */
    fun findIdentical(existing: List<File>, incomingBytes: ByteArray): File? {
        val incomingHash = sha256(incomingBytes)
        return existing.firstOrNull {
            it.length() == incomingBytes.size.toLong() &&
                sha256(it.readBytes()) == incomingHash
        }
    }

    /**
     * Files strictly older than [now] - [maxAgeMs] (mtime-based: the dedup
     * refresh touches the same clock, so a re-imported upload is protected
     * for another full window). Newest first, like the history list; an
     * empty result is valid.
     */
    fun purgeCandidates(files: List<File>, now: Long, maxAgeMs: Long = PURGE_MAX_AGE_MS): List<File> =
        files
            .filter { it.lastModified() < now - maxAgeMs }
            .sortedByDescending { it.lastModified() }
}
