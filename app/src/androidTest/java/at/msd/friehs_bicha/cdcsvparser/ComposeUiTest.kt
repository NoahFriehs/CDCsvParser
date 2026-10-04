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
        // Crypto was detected in the file -> the provider attribution shows.
        waitFor(
            By.textContains("CryptoCompare API"),
            "price attribution not posted",
            30_000,
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

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Locale-fixed "d.M. HH:mm" display of the test file's stamp. */
        val HISTORY_STAMP_PATTERN =
            Pattern.compile("\\d{1,2}\\.\\d{1,2}\\. \\d{2}:\\d{2}")
    }
}
