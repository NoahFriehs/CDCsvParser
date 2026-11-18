package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worker's alert-hook decision (pure): the core's alert state
 * machine evaluates ONLY on a price pass that produced fresh values.
 * A no-op pass - a 429 blackout, a failed bulk fetch, an offline probe -
 * must NOT evaluate (no fresh values means no new crossings; the state
 * keeps latching in the core until the next real pass).
 */
class PriceRefreshWorkerTest {

    @Test
    fun freshPassRunsTheAlertCheck() {
        assertTrue(PriceRefreshWorker.shouldRunAlertCheck(true))
    }

    @Test
    fun noFreshValuesNeverRunsTheAlertCheck() {
        // The 429/no-op tick contract: nothing refreshed -> no check.
        assertFalse(PriceRefreshWorker.shouldRunAlertCheck(false))
    }
}
