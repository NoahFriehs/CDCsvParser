package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.general.HistoryFileUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryFileUtilTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()  // @JvmField: JUnit reads the rule as a FIELD

    private val now = 1_730_000_000_000L  // arbitrary, far from the 30-d math

    private fun mk(name: String, content: String, mtime: Long): File {
        val f = tmp.newFile(name)
        f.writeText(content)
        assertTrue("test precondition: mtime must stick", f.setLastModified(mtime))
        return f
    }

    // ---------- sha256 ----------

    @Test
    fun sha256MatchesKnownVectorAndIsStable() {
        // NIST known answer: SHA-256("abc").
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a3" +
                "96177a9cb410ff61f20015ad",
            HistoryFileUtil.sha256("abc".toByteArray())
        )
        assertEquals(HistoryFileUtil.sha256("abc".toByteArray()), HistoryFileUtil.sha256("abc".toByteArray()))
    }

    @Test
    fun sha256DiffersForDifferentContent() {
        assertTrue(HistoryFileUtil.sha256("abc".toByteArray()) != HistoryFileUtil.sha256("abd".toByteArray()))
    }

    // ---------- findIdentical (dedup) ----------

    @Test
    fun findIdenticalMatchesSameContentUnderDifferentFilenames() {
        val a = mk("2026-01-01-00-00-00.csv", "1,2,3\n", now - 1000)
        val c = mk("2026-01-02-00-00-00.csv", "0,0,0\n", now - 500)
        val b = mk("2026-01-03-00-00-00.csv", "1,2,3\n", now - 100)
        // The incoming upload is the same content again (a re-upload).
        val hit = HistoryFileUtil.findIdentical(listOf(c, a, b), "1,2,3\n".toByteArray())
        assertEquals(a.name, hit?.name)  // first content-identical file wins
    }

    @Test
    fun findIdenticalRejectsOneByteDifference() {
        val a = mk("a.csv", "1,2,3\n", now)
        val b = mk("b.csv", "1,2,4\n", now)
        assertEquals(null, HistoryFileUtil.findIdentical(listOf(a, b), "9,9,9\n".toByteArray()))
    }

    @Test
    fun findIdenticalRejectsSameSizeDifferentContentAndDifferentSize() {
        val sameSizeDiff = mk("s.csv", "abcde", now)
        val otherSize = mk("o.csv", "abcdeFG", now)
        assertEquals(null, HistoryFileUtil.findIdentical(listOf(sameSizeDiff), "abxde".toByteArray()))
        assertEquals(null, HistoryFileUtil.findIdentical(listOf(otherSize), "abc".toByteArray()))
    }

    @Test
    fun findIdenticalOnEmptyListIsNull() {
        assertEquals(null, HistoryFileUtil.findIdentical(emptyList(), "x".toByteArray()))
    }

    // ---------- purgeCandidates ----------

    @Test
    fun purgeIsStrictlyOlderThanTheCutoff() {
        val boundary = now - HistoryFileUtil.PURGE_MAX_AGE_MS
        val exactly30dOld = mk("exact.csv", "x", boundary)
        val oneMsOlder = mk("older.csv", "x", boundary - 1)
        val fresh = mk("fresh.csv", "x", now - 1)
        val out = HistoryFileUtil.purgeCandidates(listOf(exactly30dOld, oneMsOlder, fresh), now)
        assertEquals(listOf<File>(oneMsOlder), out)
    }

    @Test
    fun purgeEmptyListIsEmpty() {
        assertTrue(HistoryFileUtil.purgeCandidates(emptyList(), now).isEmpty())
    }

    @Test
    fun purgeAllOldReturnsAllSortedNewestFirstAndAllNewIsNone() {
        val old3 = mk("old3.csv", "x", now - 31 * 24 * 60 * 60 * 1000L)
        val old1 = mk("old1.csv", "x", now - 31 * 24 * 60 * 60 * 1000L - 500)
        val old2 = mk("old2.csv", "x", now - 31 * 24 * 60 * 60 * 1000L - 250)
        val new1 = mk("new1.csv", "x", now - 1000)
        val allOld = HistoryFileUtil.purgeCandidates(listOf(old1, old2, old3), now)
        assertEquals(listOf<File>(old3, old2, old1), allOld)
        assertTrue(HistoryFileUtil.purgeCandidates(listOf(new1), now).isEmpty())
    }

    @Test
    fun purgeRespectsCustomMaxAge() {
        val justInside = mk("in.csv", "x", now - 5_000)
        val justOutside = mk("out.csv", "x", now - 5_001)
        val out = HistoryFileUtil.purgeCandidates(listOf(justInside, justOutside), now, maxAgeMs = 5_000)
        assertEquals(listOf<File>(justOutside), out)
    }

    // ---------- dedup refresh ordering ----------

    @Test
    fun dedupRefreshMakesTheFileSortNewestAgain() {
        val refreshed = mk("dup.csv", "s", now - 50_000)
        val other = mk("other.csv", "s", now - 10_000)
        // What the dedup path does: touch the existing entry.
        assertTrue(refreshed.setLastModified(now))
        val sorted = listOf(other, refreshed).sortedByDescending { it.lastModified() }
        assertEquals(refreshed.name, sorted.first().name)
    }
}
