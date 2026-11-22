package at.msd.friehs_bicha.cdcsvparser

import androidx.test.uiautomator.By
import at.msd.friehs_bicha.cdcsvparser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ASSERTION legs of the persistence-across-restart sequence. The
 * launch the base class performs in @Before IS the restart under test:
 * the setup leg's process was dead since the host's force-stop, so
 * everything these tests observe comes from persisted storage only (the
 * in-memory world - C++ core, LiveData, the settings cache - is gone).
 *
 * These legs must never wipe the state: the base class is constructed
 * with `wipesAtStart = false` on purpose.
 *
 * Run through the lane, ONE leg per `am instrument` invocation - never
 * as part of the plain suite (the lane orchestrates the kill between a
 * setup leg and its assertion).
 */
internal class PersistenceRestartAssertTest : PersistenceRestartSupport(wipesAtStart = false) {

    /**
     * Leg 1 assertion: the imported model survived the process - the
     * restarted fast start found and loaded the persisted C++ state
     * (the marker in the core log), the background rebuild re-parsed
     * the fixture and re-posted (the overview structure), and the
     * restored model carries exactly the fixture rows + the history
     * entry.
     */
    @Test
    fun importedModelSurvivesProcessRestart() {
        // The fast start lands DIRECTLY on the parse screen with the
        // rehydrated model - its chart section only appears once a
        // re-post landed (the loaded state first, then the background
        // rebuild's re-post with the daily series).
        waitFor(
            By.text(str(R.string.overview_charts)),
            "rehydrated overview not posted after restart",
            180_000,
        )
        waitText(str(R.string.money_spent_label), 180_000)
        // Settle the two-post sequence: counting structure from a
        // mid-recomposition snapshot is a race.
        sleep(5_000)

        // The C++ state side: init() in the fresh process must have
        // found and loaded the persisted blob from the setup leg's
        // import (the C-side marker in the core log). The background
        // rebuild is a KOTLIN log line (it re-parses and re-posts from
        // the core; the daily series only exist after that re-post).
        val coreTail = readLogTail(coreLog, coreLogAtLaunch)
        val appTail = readLogTail(appLog, appLogAtLaunch)
        assertTrue(
            "C++ fast start did not load the persisted state; core log tail: " +
                coreTail.takeLast(15),
            coreTail.any { it.contains(CORE_STATE_LOAD_MARKER) },
        )
        assertTrue(
            "the fast start ran without a saved state; app log tail: " +
                appTail.takeLast(15),
            appTail.none { it.contains(CORE_STATE_MISSING_MARKER) },
        )
        assertTrue(
            "the fast-start background rebuild never re-posted; app log tail: " +
                appTail.takeLast(15),
            appTail.any { it.contains(CORE_REBUILD_DONE_MARKER) },
        )

        // The restored rows: exactly the two fixture rows, by text
        // (wallet name = CSV content, never a price value).
        tap(waitText(str(R.string.all_transactions), 180_000))
        val rows = device.findObjects(By.text(WALLET_NAME))
        assertEquals("restored transaction rows", 2, rows.size)

        // The history entry: back on the main screen the imported file
        // is still there (the history dialog lists the fixture row).
        device.pressBack()
        waitText(str(R.string.overview_charts), 60_000)
        device.pressBack()
        waitText(str(R.string.welcome_message))
        tap(waitFor(By.text(HISTORY_STAMP_PATTERN), "history button not visible"))
        waitFor(By.textContains(FIXTURE_NAME), "fixture history row not found")
        device.pressBack()
    }

    /**
     * Leg 2 assertion: the two settings-store toggles made through the
     * settings screen in the setup leg are still selected after a full
     * process death - read straight from the a11y checked state, no
     * price involved.
     */
    @Test
    fun fastStartTogglesSurviveProcessRestart() {
        ensureOnMain()
        openSettings()
        assertTrue(
            "'store data local' toggle lost across the restart - screen had: ${visibleTexts()}",
            switchIsChecked(str(R.string.store_data_local_needed_for_faststart)),
        )
        assertTrue(
            "'enable fast start' toggle lost across the restart - screen had: ${visibleTexts()}",
            switchIsChecked(str(R.string.enable_faststart)),
        )
    }
}
