package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.R
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The SETUP legs of the persistence-across-restart sequence. Each test
 * builds the pre-restart state that the matching assertion leg in
 * [PersistenceRestartAssertTest] verifies AFTER the host has killed the
 * process (a host-side `am force-stop`, root context - the only restart
 * the same-process same-uid connected layout permits).
 *
 * Every leg wipes the persisted state first, so each starts at the
 * pristine first-install point: the very first boot of this install
 * shows the sign-in screen (the first-start flag is part of the wiped
 * settings store) - [PersistenceRestartSupport.ensureOnMain] skips it,
 * which also clears the flag: every later cold start (the restarts
 * under test) goes straight to the main screen.
 *
 * Run through the lane, ONE leg per `am instrument` invocation - never
 * as part of the plain suite (the lane orchestrates the kill between a
 * leg and its assertion).
 */
internal class PersistenceRestartSetupTest : PersistenceRestartSupport(wipesAtStart = true) {

    override fun beforeLaunch() {
        // The fixture is written BEFORE the launch: the main screen
        // snapshots the files dir at composition time, a file created
        // later would not make the history button appear.
        File(target.filesDir, FIXTURE_NAME).writeText(FIXTURE_CSV)
    }

    /**
     * Leg 1 setup: an imported fixture (the guarded Room save + the C++
     * state save both run during the parse) and the two fast-start
     * toggles flipped through the settings screen, so the settings
     * store is written by the app process itself - the only writer the
     * restarted process will read.
     */
    @Test
    fun importFixtureAndArmFastStart() {
        ensureOnMain()
        importFixture()

        // The guarded save runs right after the first posted overview:
        // wait on its own log marker instead of assuming it happened.
        val saveLines = waitForLogMarker(appLog, appLogAtLaunch, ROOM_SAVE_MARKER)
        assertTrue(
            "guarded Room save never logged; app log tail: ${saveLines.takeLast(15)}",
            saveLines.any { it.contains(ROOM_SAVE_MARKER) },
        )

        openSettings()
        toggleSwitch(str(R.string.store_data_local_needed_for_faststart))
        toggleSwitch(str(R.string.enable_faststart))
        // Let the settings store flush its writes before the host kills
        // the process: an un-flushed toggle would make the restart land
        // on the main screen and the assertion fail for the wrong reason.
        sleep(DATASTORE_FLUSH_MS)
    }

    /**
     * Leg 2 setup: the same two fast-start toggles flipped through the
     * settings screen on an otherwise pristine install (no import) -
     * the plain settings-store-across-restart case.
     */
    @Test
    fun flipFastStartToggles() {
        ensureOnMain()
        openSettings()
        toggleSwitch(str(R.string.store_data_local_needed_for_faststart))
        toggleSwitch(str(R.string.enable_faststart))
        sleep(DATASTORE_FLUSH_MS)
    }
}
