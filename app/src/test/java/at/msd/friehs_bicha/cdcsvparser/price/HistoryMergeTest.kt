package at.msd.friehs_bicha.cdcsvparser.price

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure merge logic for the incremental history cache (stale tail refresh). */
class HistoryMergeTest {

    /** Days 100..103 as previously cached (fetched day 103). */
    private val old = listOf(
        100L to 1.0,
        101L to 2.0,
        102L to 3.0,
        103L to 4.0,
    )

    @Test
    fun `tail replaces the cached points from the cut day on, old part stays`() {
        // A point on the cut day itself may have been an intraday snapshot,
        // so the fetched tail wins from cutDay onward.
        val tail = listOf(102L to 3.5, 103L to 4.5, 104L to 5.0)
        val merged = PriceHistoryProvider.merge(old, tail, 102L)
        assertEquals(
            listOf(100L to 1.0, 101L to 2.0, 102L to 3.5, 103L to 4.5, 104L to 5.0),
            merged,
        )
    }

    @Test
    fun `a tail beyond the cache extends it without touching the old part`() {
        val merged = PriceHistoryProvider.merge(old, listOf(200L to 9.0), 200L)
        assertEquals(old + (200L to 9.0), merged)
    }

    @Test
    fun `result is ascending and unique even for unordered input`() {
        val merged = PriceHistoryProvider.merge(
            old = listOf(5L to 1.0),
            tail = listOf(3L to 0.5, 7L to 2.0),
            cutDay = 3L,
        )
        assertEquals(listOf(3L to 0.5, 5L to 1.0, 7L to 2.0), merged)
    }

    @Test
    fun `overlapping days inside the tail keep the last value`() {
        val merged = PriceHistoryProvider.merge(
            old = listOf(1L to 9.9),
            tail = listOf(1L to 1.0, 2L to 2.0),
            cutDay = 1L,
        )
        assertEquals(listOf(1L to 1.0, 2L to 2.0), merged)
    }

    @Test
    fun `empty tail changes nothing`() {
        assertEquals(old, PriceHistoryProvider.merge(old, emptyList(), 102L))
    }

    @Test
    fun `empty old adopts the tail wholesale`() {
        assertEquals(
            listOf(1L to 1.0, 2L to 2.0),
            PriceHistoryProvider.merge(emptyList(), listOf(1L to 1.0, 2L to 2.0), 1L),
        )
    }
}
