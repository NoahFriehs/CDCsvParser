package at.msd.friehs_bicha.cdcsvparser

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import at.msd.friehs_bicha.cdcsvparser.ui.activity.StartingActivity
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import java.io.File
import java.util.regex.Pattern

/**
 * Shared base for the persistence-across-restart legs.
 *
 * The restart itself is NOT a test action: the connected instrumentation
 * shares ONE process and ONE uid with the app, so any process kill a
 * test can reach either takes the runner down with the app or is refused
 * to the app uid. The process death is orchestrated by the host that
 * runs the lane (root context) BETWEEN a setup leg and its assertion
 * leg, so the sequence a lane drives is:
 *
 *     setup leg runs  ->  host: am force-stop  ->  assertion leg runs
 *
 * Every leg therefore starts from the host's perspective: the app
 * process is DEAD and no launch has happened yet in this run. The setup
 * legs wipe the persisted state of any previous run first (the pristine
 * first-install point), so the legs are independent of test order and of
 * whatever the rest of the suite left behind. The assertion legs must
 * NOT wipe - they are the ones that read back what survived the death.
 * Each leg is its own `am instrument` invocation with a single-method
 * class filter; the app (and its persisted data) survives between two
 * invocations because the lane never uninstalls it except once, at
 * the lane start.
 *
 * Assertions are on settled STRUCTURE only (the posted overview,
 * restored row counts, checked toggle state, log markers) - never on
 * live crypto prices or percentages - so an API rate limit cannot flake
 * these tests. The restart is the proof device: the force-stop kills
 * the whole in-memory world (C++ core, LiveData, the PreferenceHelper
 * cache), so anything the restarted process shows or logs comes from
 * persisted storage only. The restarted fast start does trigger a
 * background price/rebuild pass - the waits below tolerate it still
 * running and never assert on its values.
 *
 * Interaction is pure UiAutomator (no Espresso), for the same reason as
 * the smoke suite.
 */
internal abstract class PersistenceRestartSupport(private val wipesAtStart: Boolean) {

    /** The Kotlin app log (append file; byte offsets split the runs). */
    protected val appLog: File
        get() = File(target.filesDir, "log/CDCsvParser.log")

    /** The C++ core log (append file; the C-side markers land here). */
    protected val coreLog: File
        get() = File(target.filesDir, "log/core.log")

    /** Byte offset of the app log right before this leg launches the app. */
    protected var appLogAtLaunch: Long = 0L
        private set

    /** Byte offset of the core log right before this leg launches the app. */
    protected var coreLogAtLaunch: Long = 0L
        private set

    protected lateinit var scenario: ActivityScenario<StartingActivity>

    protected val target
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    protected val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    protected fun str(id: Int) = target.getString(id)

    /**
     * Leg precondition + launch. The wipe (setup legs only) deletes the
     * persisted state of any previous run, [beforeLaunch] writes what
     * this leg needs, then the app is launched - for an assertion leg
     * this launch IS the restart under test (the setup leg's process was
     * dead since the host's force-stop).
     */
    @Before
    fun setUp() {
        appLogAtLaunch = logOffset(appLog)
        coreLogAtLaunch = logOffset(coreLog)
        if (wipesAtStart) {
            wipeSessionState()
            seedSettingsBaseline()
        }
        beforeLaunch()
        scenario = ActivityScenario.launch(StartingActivity::class.java)
    }

    @After
    fun tearDown() {
        if (this::scenario.isInitialized) {
            scenario.close()
        }
    }

    /** Setup-leg hook (write the fixture). No-op by default. */
    protected open fun beforeLaunch() {}

    /**
     * Seeds the persisted settings to a known baseline through the app's
     * own settings writer - the SAME code path the UI uses, so it holds
     * in BOTH the in-memory cache (which this process's launch reads) and
     * the store on disk (which the restarted process reads). The wipe
     * alone does not suffice: the app process's DataStore instance may
     * have loaded the pre-wipe file BEFORE the wipe (load races wipe),
     * leaving stale values in memory and no deterministic on-disk state.
     * With the baseline the launch is deterministic (no one-shot sign-in
     * screen, no fast start), and the legs under test start from the
     * same known point regardless of what the rest of the suite left
     * behind.
     */
    private fun seedSettingsBaseline() {
        PreferenceHelper.setIsFirstStart(target, false)
        PreferenceHelper.setIsAppModelSavedLocal(target, false)
        PreferenceHelper.setIsDataLocal(target, false)
        PreferenceHelper.setFastStartEnabled(target, false)
        // Settle: the DataStore load of this fresh process may still be
        // running (a late load would re-clear the in-memory cache), and
        // the seed's store writes should be underway before the launch.
        sleep(1_000)
    }

    /**
     * The pristine-first-install wipe: the history CSVs (the fixture is
     * re-written right after), the C++ fast-start state dir, the
     * DataStore settings file (the directory spelling has varied across
     * DataStore versions - handle both), and the legacy prefs it
     * migrates from. Runs with the app stopped (the host force-stopped
     * it), so nothing re-flushes a stale value behind our back.
     */
    protected fun wipeSessionState() {
        target.filesDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.forEach { it.delete() }
        File(target.filesDir, "save").deleteRecursively()
        listOf(File(target.filesDir, "datastore"), File(target.filesDir, "dataStore"))
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.listFiles { f -> f.name.startsWith("settings.preferences") }
                    ?.forEach { it.delete() }
            }
        // (the data dir is the parent of filesDir; no androidx.preference
        //  extension needed for the direct file path)
        File(target.filesDir.parentFile, "shared_prefs/settings_prefs.xml")
            .delete()
    }

    // ------------------------------------------------------------------
    // Navigation + wait helpers (pure UiAutomator, house style)
    // ------------------------------------------------------------------

    /** Opens the history dialog, taps the fixture row, waits for the posted overview. */
    protected fun importFixture() {
        tap(waitFor(By.text(HISTORY_STAMP_PATTERN), "history button not visible"))
        tap(waitFor(By.textContains(FIXTURE_NAME), "fixture history row not found"))
        waitFor(
            By.text(str(R.string.overview_charts)),
            "parse overview not posted",
            180_000,
        )
    }

    /** The settings gear (action bar) from the current screen; waits for the first row. */
    protected fun openSettings() {
        tap(waitFor(By.desc(str(R.string.settings)), "settings icon not visible"))
        waitText(str(R.string.app_type_label))
    }

    /**
     * Toggles the Material3 switch of the row labeled [label] and
     * verifies the flip. The a11y tree exposes each switch as a
     * checkable sibling node sharing its label's row band (the label
     * itself is not clickable), so the target is located by anchor
     * from a window-hierarchy dump - no hardcoded coordinates. The
     * expected end state is re-read after the click: a missed click
     * fails the test instead of silently continuing.
     */
    protected fun toggleSwitch(label: String) {
        val before = requireNotNull(switchFor(label)) {
            "no switch found for '$label' - screen had: ${visibleTexts()}"
        }
        device.click(
            (before.bounds[0] + before.bounds[2]) / 2,
            (before.bounds[1] + before.bounds[3]) / 2,
        )
        device.waitForIdle()
        sleep(300)   // the state recomposition settles before re-reading
        val after = switchFor(label) ?: throw AssertionError(
            "switch of '$label' vanished after the click - screen had: ${visibleTexts()}",
        )
        assertEquals(
            "switch '$label' did not flip (was ${before.checked}, wanted ${!before.checked})",
            !before.checked,
            after.checked,
        )
    }

    /** The checked state of the switch sharing [label]'s row band. */
    protected fun switchIsChecked(label: String): Boolean {
        val switch = switchFor(label)
            ?: throw AssertionError("no switch found for '$label' - screen had: ${visibleTexts()}")
        return switch.checked
    }

    /** The checkable node sharing the row band of [label], or null. */
    protected fun switchFor(label: String): SwitchInfo? {
        val xml = dumpHierarchy()
        val labelMatch = Regex(
            "<node [^>]*text=\"" + Regex.escape(label) +
                "\"[^>]*bounds=\"\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]\""
        ).find(xml) ?: return null
        val labelTop = labelMatch.groupValues[2].toInt()
        val labelBottom = labelMatch.groupValues[4].toInt()
        val switchPattern = Regex(
            "<node [^>]*checkable=\"true\" checked=\"(true|false)\"[^>]*" +
                "bounds=\"\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]\""
        )
        for (m in switchPattern.findAll(xml)) {
            val top = m.groupValues[3].toInt()
            val bottom = m.groupValues[5].toInt()
            if (top < labelBottom && bottom > labelTop) {
                return SwitchInfo(
                    IntArray(4) { m.groupValues[it + 2].toInt() },
                    m.groupValues[1] == "true",
                )
            }
        }
        return null
    }

    protected data class SwitchInfo(val bounds: IntArray, val checked: Boolean)

    /** The on-disk byte offset of [logFile] (0 when absent). */
    protected fun logOffset(logFile: File): Long = if (logFile.exists()) logFile.length() else 0L

    /** Polls [logFile] from [fromByte] until [marker] lands (429-cooldown budget). */
    protected fun waitForLogMarker(logFile: File, fromByte: Long, marker: String): List<String> {
        val deadline = System.currentTimeMillis() + 180_000
        var lines = readLogTail(logFile, fromByte)
        while (System.currentTimeMillis() < deadline && lines.none { it.contains(marker) }) {
            sleep(1_000)
            lines = readLogTail(logFile, fromByte)
        }
        return lines
    }

    protected fun dumpHierarchy(): String {
        val out = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        return out.toString(Charsets.UTF_8)
    }

    /** Visible texts for failure diagnostics (whole on-screen a11y tree). */
    protected fun visibleTexts(max: Int = 25): String {
        val texts = Regex("text=\"([^\"]+)\"")
            .findAll(dumpHierarchy())
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        return if (texts.isEmpty()) "(none)" else texts.take(max).joinToString(" | ")
    }

    /** Condition-based sleep (UiAutomator 2.3.x has no raw wait). */
    protected fun sleep(millis: Long) {
        device.wait(Until.findObject(By.text("sleep_marker_unused")), millis)
    }

    protected fun tap(obj: UiObject2) {
        obj.click()
        device.waitForIdle()
    }

    protected fun waitFor(by: BySelector, message: String, timeout: Long = TIMEOUT_MS): UiObject2 {
        val obj = device.wait(Until.findObject(by), timeout)
        assertNotNull("$message - screen had: ${visibleTexts()}", obj)
        return obj
    }

    protected fun waitText(text: String, timeout: Long = TIMEOUT_MS): UiObject2 =
        waitFor(By.text(text), "expected text '$text'", timeout)

    /** Waits until any expected screen text is visible (splash/login in between). */
    protected fun waitAny(vararg texts: String) {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (texts.any { device.findObject(By.text(it)) != null }) return
            sleep(1_000)
        }
        throw AssertionError("None of $texts became visible within 60 s")
    }

    /** Lands on the main screen from either the login screen or a warm start. */
    protected fun ensureOnMain() {
        waitAny(str(R.string.use_app_without_login), str(R.string.welcome_message))
        val loginSkip = device.findObject(By.text(str(R.string.use_app_without_login)))
        if (loginSkip != null) {
            tap(loginSkip)
        }
        waitText(str(R.string.welcome_message))
    }

    /** Lines of [logFile] from byte [fromByte] on (ASCII markers only). */
    protected fun readLogTail(logFile: File, fromByte: Long): List<String> {
        if (!logFile.exists() || logFile.length() <= fromByte) return emptyList()
        val bytes = logFile.readBytes()
        val from = fromByte.toInt().coerceIn(0, bytes.size)
        return bytes.copyOfRange(from, bytes.size).decodeToString()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
    }

    companion object {
        const val TIMEOUT_MS = 10_000L

        /** The DataStore write settle before the host kills the process. */
        const val DATASTORE_FLUSH_MS = 2_000L

        const val FIXTURE_NAME = "2026-03-05-08-00-00.csv"
        const val WALLET_NAME = "Persist Wallet"

        /** The two-row deterministic BlockPit fixture (one wallet). */
        const val FIXTURE_CSV =
            "Date (UTC);Integration Name;Label;Outgoing Asset;Outgoing Amount;" +
                "Incoming Asset;Incoming Amount;Fee Asset (optional);" +
                "Fee Amount (optional);Comment (optional);Trx. ID (optional);" +
                "Source Type;Source Name\n" +
                "15.03.2026 10:00:00;$WALLET_NAME;Trade;EUR;50;;BTC;0.5;;;" +
                "persist purchase;persist-1;Manual;CITest\n" +
                "20.03.2026 11:00:00;$WALLET_NAME;Deposit;;;ETH;0.1;;;" +
                "persist deposit;persist-2;Manual;CITest\n"

        /** Guarded Room save marker (Kotlin app log). */
        const val ROOM_SAVE_MARKER = "Data saved to RoomsDB"
        /** C++ fast start found + loaded the persisted state blob (core log). */
        const val CORE_STATE_LOAD_MARKER = "Saved data found, loading..."
        /** The fast start found NOTHING to load (the failure shape; Kotlin app log). */
        const val CORE_STATE_MISSING_MARKER = "No saved C++ state; fast start has no values to show."
        /** The background re-parse of the last import re-posted (Kotlin app log). */
        const val CORE_REBUILD_DONE_MARKER = "Fast start (C++): background rebuild finished; re-posting from the C++ core."

        /**
         * Locale-fixed "d.M. HH:mm" display of the test file's stamp -
         * the history button hardcodes this format (locale-independent).
         */
        val HISTORY_STAMP_PATTERN =
            Pattern.compile("\\d{1,2}\\.\\d{1,2}\\. \\d{2}:\\d{2}")
    }
}
