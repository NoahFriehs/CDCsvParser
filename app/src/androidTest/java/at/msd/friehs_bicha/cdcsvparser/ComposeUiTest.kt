package at.msd.friehs_bicha.cdcsvparser

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.ui.activity.StartingActivity
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

private const val HISTORY_PASS_COMPLETE_MARKER = "History pass complete"

/**
 * UI smoke tests (3.5) for the Compose screen tree. Deliberately
 * network-light: a tiny deterministic BlockPit CSV drives the parse flow and
 * assertions only rely on what the core computes from the CSV alone (row
 * count, posted overview structure), never on live crypto prices, so no
 * CoinGecko 429 rate limit can flake a test. The parse posts its map once
 * the (optional) price fetch finishes - the only network wait - and the
 * budget below covers the worst documented cooldown (45 s).
 *
 * Interaction is pure UiAutomator (no Espresso): on the API 37 dev-preview
 * image Espresso's InputManagerEventInjectionStrategy crashes
 * (android.hardware.input.InputManager.getInstance was removed), which
 * wedges the Compose test rule's idle hook and every Espresso.onIdle.
 * UiAutomator reads the same accessibility tree the user (and screen
 * readers) see and also reaches dialog windows.
 */
class ComposeUiTest {

    private lateinit var scenario: ActivityScenario<StartingActivity>

    private val target
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun str(id: Int) = target.getString(id)

    /**
     * Writes the test CSV and the app type *before* the app starts, because
     * the main screen snapshots the files dir at composition time - a file
     * created later does not make the history button appear.
     */
    @Before
    fun setUp() {
        val csv = File(target.filesDir, "2026-01-15-12-00-00.csv")
        csv.writeText(
            "Date (UTC);Integration Name;Label;Outgoing Asset;Outgoing Amount;" +
                "Incoming Asset;Incoming Amount;Fee Asset (optional);" +
                "Fee Amount (optional);Comment (optional);Trx. ID (optional);" +
                "Source Type;Source Name\n" +
                "15.01.2026 10:00:00;UI Test Wallet;Trade;EUR;50;;BTC;0.5;;;" +
                "UI test purchase;ui-tx-1;Manual;CITest\n" +
                "20.01.2026 11:00:00;UI Test Wallet;Deposit;;;ETH;0.1;;;" +
                "UI test deposit;ui-tx-2;Manual;CITest\n",
        )
        // Plan 008: a second upload with 8 coins (deliberately NO BTC/ETH,
        // so a still-running pass of another test cannot pollute this
        // test's per-symbol fetch counts).
        File(target.filesDir, "2026-02-10-09-00-00.csv").writeText(
            "Date (UTC);Integration Name;Label;Outgoing Asset;Outgoing Amount;" +
                "Incoming Asset;Incoming Amount;Fee Asset (optional);" +
                "Fee Amount (optional);Comment (optional);Trx. ID (optional);" +
                "Source Type;Source Name\n" +
                "15.01.2026 10:00:00;UI Pass Wallet;Deposit;;;SOL;2.5;;;" +
                "pass-1;ui-pass-1;Manual;CITest\n" +
                "15.01.2026 10:05:00;UI Pass Wallet;Deposit;;;XRP;10;;;" +
                "pass-2;ui-pass-2;Manual;CITest\n" +
                "15.01.2026 10:10:00;UI Pass Wallet;Deposit;;;ADA;20;;;" +
                "pass-3;ui-pass-3;Manual;CITest\n" +
                "15.01.2026 10:15:00;UI Pass Wallet;Deposit;;;DOGE;50;;;" +
                "pass-4;ui-pass-4;Manual;CITest\n" +
                "15.01.2026 10:20:00;UI Pass Wallet;Deposit;;;LTC;0.3;;;" +
                "pass-5;ui-pass-5;Manual;CITest\n" +
                "15.01.2026 10:25:00;UI Pass Wallet;Deposit;;;AVAX;0.8;;;" +
                "pass-6;ui-pass-6;Manual;CITest\n" +
                "15.01.2026 10:30:00;UI Pass Wallet;Deposit;;;LINK;1.2;;;" +
                "pass-7;ui-pass-7;Manual;CITest\n" +
                "15.01.2026 10:35:00;UI Pass Wallet;Deposit;;;MATIC;40;;;" +
                "pass-8;ui-pass-8;Manual;CITest\n",
        )
        // Belt and braces: the core also detects BlockPit from the file
        // header, but set the intended type anyway.
        PreferenceHelper.setSelectedType(target, AppType.BlockPit)

        scenario = ActivityScenario.launch(StartingActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun waitFor(by: BySelector, message: String, timeout: Long = TIMEOUT_MS): UiObject2 {
        val obj = device.wait(Until.findObject(by), timeout)
        assertNotNull("$message - screen had: ${visibleTexts()}", obj)
        return obj
    }

    /** Visible texts for failure diagnostics (whole on-screen a11y tree). */
    private fun visibleTexts(max: Int = 25): String {
        val out = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        val texts = Regex("text=\"([^\"]+)\"")
            .findAll(out.toString(Charsets.UTF_8))
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        return if (texts.isEmpty()) "(none)" else texts.take(max).joinToString(" | ")
    }

    /** Condition-based sleep (UiAutomator 2.3.x has no raw wait). */
    private fun sleep(millis: Long) {
        device.wait(Until.findObject(By.text("sleep_marker_unused")), millis)
    }

    private fun tap(obj: UiObject2) {
        obj.click()
        device.waitForIdle()
    }

    private fun waitText(text: String, timeout: Long = TIMEOUT_MS): UiObject2 =
        waitFor(By.text(text), "expected text '$text'", timeout)

    /**
     * Waits for [by], scrolling the screen up in between polls: the parse
     * overview is a scrollable column and its lower parts (the chart,
     * the attribution) sit below the fold since plan 006 grew the chart
     * panel - a plain wait can never see them.
     */
    private fun waitForScrolling(by: BySelector, message: String, timeout: Long = 60_000): UiObject2 {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val obj = device.findObject(by)
            if (obj != null) return obj
            scrollUpOnce()
            sleep(400)
        }
        throw AssertionError("$message - screen had: ${visibleTexts()}")
    }

    /** One long upward swipe in the card/chip area (well above the chart). */
    private fun scrollUpOnce() {
        // UiAutomator 2.3: no UiDevice.screenResolution anymore.
        val w = device.getDisplayWidth()
        val h = device.getDisplayHeight()
        device.swipe(w / 2, (h * 0.55).toInt(), w / 2, (h * 0.22).toInt(), 12)
    }

    /** Waits (polling) until [text] is no longer on screen. */
    private fun waitTextGone(text: String, deadlineMs: Long) {
        val deadline = System.currentTimeMillis() + deadlineMs
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(By.text(text)) == null) return
            sleep(1_000)
        }
        throw AssertionError(
            "Text '$text' still on screen after ${deadlineMs} ms - screen had: ${visibleTexts()}",
        )
    }

    /** Waits until any expected screen text is visible (splash/login in between). */
    private fun waitAny(vararg texts: String) {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (texts.any { device.findObject(By.text(it)) != null }) return
            sleep(1_000)
        }
        throw AssertionError("None of $texts became visible within 60 s")
    }

    /** Lands on the main screen from either the login screen or a warm start. */
    private fun ensureOnMain() {
        waitAny(str(R.string.use_app_without_login), str(R.string.welcome_message))
        val loginSkip = device.findObject(By.text(str(R.string.use_app_without_login)))
        if (loginSkip != null) {
            tap(loginSkip)
        }
        waitText(str(R.string.welcome_message))
    }

    /**
     * Opens the history dialog, taps the test upload row and waits until the
     * parse screen posted its overview ("Parsing your file…" is gone).
     */
    private fun parseTestUpload() {
        // The button carries the file stamp re-formatted with a locale-fixed
        // "d.M. HH:mm" (variable month width - match, don't hardcode).
        tap(waitFor(By.text(HISTORY_STAMP_PATTERN), "history button not visible"))

        // The row's meta line carries the raw file name.
        tap(
            waitFor(
                By.textContains("2026-01-15-12-00-00.csv"),
                "history row not found",
            ),
        )

        // Wait for the posted overview instead of the transient "Parsing
        // your file…" indicator: with a warm price cache the parse finishes
        // in well under a second and the indicator is never observed.
        // (The spend-chart section only appears once the map was posted.)
        waitFor(
            By.text(str(R.string.overview_charts)),
            "parse overview not posted",
            180_000,
        )
    }

    @Test
    fun mainScreenShowsWelcomeAndUpload() {
        ensureOnMain()
        waitText(str(R.string.welcome_message))
        waitText(str(R.string.upload_file))
    }

    @Test
    fun historySelectionCompletesParseAndPostsOverview() {
        ensureOnMain()
        parseTestUpload()
        // Posted overview structure (independent of live prices):
        waitText(str(R.string.money_spent_label))
        // Crypto was detected in the file -> the provider attribution
        // shows — at the very bottom of the column, so scroll for it.
        waitForScrolling(
            By.textContains("CryptoCompare API"),
            "price attribution not posted",
        )
        waitText(str(R.string.overview_charts))
        // The G35 chart panel: the default series selection is visible.
        waitText(str(R.string.series_spent))
    }

    @Test
    fun parsedTransactionListShowsExactlyTheTwoTestRows() {
        ensureOnMain()
        parseTestUpload()
        tap(waitText(str(R.string.all_transactions)))
        // Exactly the two rows of the test file (purchase + deposit) - a
        // pure CSV assertion, no price involved. The wallet name is CSV
        // content, hence a literal, not an app string resource.
        val rows = device.findObjects(By.text("UI Test Wallet"))
        assertEquals(2, rows.size)
    }

    @Test
    fun settingsScreenShowsControls() {
        ensureOnMain()
        // Gear icon: contentDescription from the settings string resource.
        tap(waitFor(By.desc(str(R.string.settings)), "settings icon not visible"))
        waitText(str(R.string.app_type_label))
        waitText(str(R.string.core_mode_label))
    }

    @Test
    fun loginScreenStructure() {
        // First run only: after the login-skip the flag is set and this path
        // is never shown again. Skip the assertions when already past it.
        waitAny(str(R.string.use_app_without_login), str(R.string.welcome_message))
        if (device.findObject(By.text(str(R.string.login))) == null) {
            return
        }
        waitText(str(R.string.login))
        waitText(str(R.string.signup))
    }

    @Test
    fun revisitingTheParseScreenAttachesToTheSameHistoryPass() {
        val logFile = File(target.filesDir, "log/CDCsvParser.log")

        ensureOnMain()
        // Parse the 8-coin upload (its later stamp is the history button's).
        tap(waitFor(By.text(HISTORY_STAMP_PATTERN), "history button not visible"))
        tap(
            waitFor(
                By.textContains("2026-02-10-09-00-00.csv"),
                "second history row not found",
            ),
        )
        waitFor(By.text(str(R.string.overview_charts)), "parse overview not posted", 180_000)

        // Let the history pass start and run a bit, then LEAVE the screen
        // (the pass keeps running in its process scope) and return: the
        // returning visit must ATTACH to the live pass, not start a second
        // one (plan 008: the old view-scoped launches both kept running —
        // the provider pass has no suspension points — and both wrote the
        // same counter, which is what the markers below would expose).
        sleep(2_000)
        // Log offset AFTER this pass is guaranteed to have started — any
        // other test's pass line lands before it (its symbols never match
        // this file's, so a still-live one was superseded, not completed,
        // by this pass start).
        val logStart = logFile.length()
        tap(waitText(str(R.string.all_transactions)))
        waitText("UI Pass Wallet")   // transactions screen is up
        sleep(3_000)
        device.pressBack()
        waitText(str(R.string.overview_charts))

        // The app log is the ground truth for the pass bookkeeping; the
        // writer thread is slightly lagging, so poll (429-cooldown budget).
        val deadline = System.currentTimeMillis() + 180_000
        var lines: List<String> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            lines = readLogTail(logFile, logStart)
            if (lines.any { it.contains(HISTORY_PASS_COMPLETE_MARKER) }) break
            sleep(2_000)
        }
        assertTrue(
            "history pass never completed; log tail: ${lines.takeLast(15)}",
            lines.any { it.contains(HISTORY_PASS_COMPLETE_MARKER) },
        )
        assertEquals(
            "expected exactly ONE pass for the whole exercise (the return " +
                "visit must attach, not start a second pass)",
            1,
            lines.count { it.contains(HISTORY_PASS_COMPLETE_MARKER) },
        )
        // No symbol was fetched twice: a second (duplicate) pass would add
        // a second "History for X:" line per symbol it re-fetched.
        lines.filter { it.contains("History for ") }
            .groupBy { it.substringAfter("History for ").substringBefore(':') }
            .forEach { (symbol, perSymbol) ->
                assertTrue(
                    "symbol $symbol fetched ${perSymbol.size} times: $perSymbol",
                    perSymbol.size <= 1,
                )
            }
    }

    /**
     * Plan 007 A3: hammer the three chip rows of the posted chart panel in
     * a fast, alternating order (the reported phone-crash interaction) and
     * assert the app is still alive with the chart panel intact. Will not
     * reproduce the data/OS-specific phone crash itself, but catches any
     * regression of the same SHAPE — an exception thrown during a
     * series/time-frame/range state swap (stale highlight carried across a
     * `data =` swap etc.) — in CI.
     */
    @Test
    fun rapidChartChipSwitchingKeepsTheAppAlive() {
        ensureOnMain()
        parseTestUpload()
        val series = listOf(
            str(R.string.series_spent),
            str(R.string.series_portfolio_value),
            str(R.string.profit_loss),
            str(R.string.series_bonus),
        )
        val frames = listOf(
            str(R.string.timeframe_week),
            str(R.string.timeframe_month),
            str(R.string.timeframe_quarter),
            str(R.string.timeframe_year),
        )
        val ranges = listOf(
            str(R.string.range_three_months),
            str(R.string.range_six_months),
            str(R.string.range_one_year),
            str(R.string.range_all),
        )
        var clicks = 0
        // 36 iterations: three rows in round-robin, cycling all four chips
        // of the active row -> every chip hit at least 3 times, and the
        // row changes every tap (the data swaps on most of them).
        // A stale UiObject2 (chip recreated by a recomposition) just
        // misses that one click - the next iteration replaces it.
        for (i in 0 until 36) {
            val row = when (i % 3) {
                0 -> series
                1 -> frames
                else -> ranges
            }
            val chip = device.findObject(By.text(row[i % row.size]))
            if (chip != null) {
                chip.click()
                clicks++
            }
        }
        assertTrue("too few taps landed ($clicks) - chips not found", clicks >= 12)
        // The app survived: the activity is resumable and the chart panel
        // (title + all three chip rows) is still there.
        waitText(str(R.string.overview_charts))
        val allChips = (series + frames + ranges).count { device.findObject(By.text(it)) != null }
        assertEquals("chip rows no longer complete after rapid switching", (series + frames + ranges).size, allChips)
    }

    /** Lines of [logFile] from byte [fromByte] on (ASCII markers only). */
    private fun readLogTail(logFile: File, fromByte: Long): List<String> {
        if (!logFile.exists() || logFile.length() <= fromByte) return emptyList()
        val bytes = logFile.readBytes()
        val from = fromByte.toInt().coerceIn(0, bytes.size)
        return bytes.copyOfRange(from, bytes.size).decodeToString()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Locale-fixed "d.M. HH:mm" display of the test file's stamp. */
        val HISTORY_STAMP_PATTERN =
            Pattern.compile("\\d{1,2}\\.\\d{1,2}\\. \\d{2}:\\d{2}")
    }
}
