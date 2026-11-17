package at.msd.friehs_bicha.cdcsvparser.benchmark

import android.content.Context
import android.content.res.Configuration
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import at.msd.friehs_bicha.cdcsvparser.R
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.regex.Pattern

/**
 * Baseline Profile journeys (three routes, run on the device budget):
 * 1. cold start to the main screen (also marked as startup profile),
 * 2. the parse-screen route (history re-import of the seeded fixture),
 * 3. the wallet-detail route (per-wallet chart scope in the parse overview).
 *
 * Seeding is done by the host before the run (root adb on the device
 * budget): the fixture CSV lands in the app's filesDir (which the main
 * screen snapshots at composition time, before first launch) and the
 * core-mode prefs are pre-written into the app's DataStore. The test
 * process runs under its own uid (this is a com.android.test module,
 * not a uid-shared androidTest), so it cannot touch the app's data
 * dir - that is why no test code writes into it.
 *
 * UI strings are resolved from the app package's resources (with the
 * app's per-app German locale forced) because the embedded app R IDs
 * point at the app resource table, not this test APK's table.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Resolves an app R string ID against the APP package's resources.
     * The app classes embedded in this test APK carry R IDs baked
     * against the app table, so they only resolve there; the config
     * forces the per-app German locale the generation device runs.
     */
    private fun str(id: Int): String {
        val config = Configuration().apply { setLocale(Locale.GERMAN) }
        val appContext = InstrumentationRegistry.getInstrumentation().context
            .createPackageContext(APP_PACKAGE, 0) // resources only - no code
        return appContext.createConfigurationContext(config).getString(id)
    }

    @Test
    fun coldStartToMain() {
        rule.collect(APP_PACKAGE, maxIterations = 10, includeInStartupProfile = true) {
            startActivityAndWait()
            landOnMain(device)
        }
    }

    @Test
    fun parseScreenRoute() {
        rule.collect(APP_PACKAGE, maxIterations = 10) {
            startActivityAndWait()
            landOnMain(device)
            openParseScreen(device)
        }
    }

    @Test
    fun walletDetailRoute() {
        rule.collect(APP_PACKAGE, maxIterations = 10) {
            startActivityAndWait()
            landOnMain(device)
            openParseScreen(device)
            openWalletScope(device)
        }
    }

    /**
     * Waits through splash (and the signed-out login screen, skipped
     * when present) until the main screen is on screen and at rest.
     */
    private fun landOnMain(device: UiDevice, timeoutMs: Long = 90_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(By.text(str(R.string.welcome_message))) != null) {
                device.waitForIdle()
                return
            }
            val skip = device.findObject(By.text(str(R.string.use_app_without_login)))
            if (skip != null) skip.click()
            sleep(device, 750)
        }
        throw IllegalStateException("main screen did not appear within ${timeoutMs} ms")
    }

    /**
     * History dialog -> seeded upload row -> posted parse overview.
     * The overview only posts once the (optional) price fetch settles,
     * so the final wait carries the documented worst-case budget.
     */
    private fun openParseScreen(device: UiDevice) {
        val historyButton: UiObject2 = device.wait(
            Until.findObject(By.text(HISTORY_STAMP_PATTERN)),
            60_000,
        ) ?: throw IllegalStateException("history upload button not visible on main screen")
        historyButton.click()

        val row: UiObject2 = device.wait(
            Until.findObject(By.textContains(SEED_FILE_NAME)),
            60_000,
        ) ?: throw IllegalStateException("seeded upload row not visible in history dialog")
        row.click()

        device.wait(
            Until.findObject(By.text(str(R.string.overview_charts))),
            PARSE_POST_TIMEOUT_MS,
        ) ?: throw IllegalStateException("parse overview did not post")
        device.waitForIdle()
    }

    /**
     * The wallet-detail surface: the per-wallet chart scope row under
     * the parse chart. The fixture's deposit row books an ETH wallet,
     * so the ETH chip is present once the row has posted. If the row
     * cannot be reached within its budget, the route degrades to the
     * parse overview instead of failing the whole collection.
     */
    private fun openWalletScope(device: UiDevice) {
        val w = device.displayWidth
        val h = device.displayHeight
        var scopeRow: UiObject2? = null
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline) {
            scopeRow = device.findObject(By.text(str(R.string.chart_wallet_total)))
            if (scopeRow != null) break
            device.swipe(w / 2, (h * 0.6).toInt(), w / 2, (h * 0.25).toInt(), 12)
            sleep(device, 500)
        }
        if (scopeRow == null) return

        val walletChip: UiObject2? = device.wait(
            Until.findObject(By.text(WALLET_CHIP_SYMBOL)),
            20_000,
        )
        if (walletChip != null) {
            walletChip.click()
            device.waitForIdle()
        }
    }

    /** Condition-based sleep (UiAutomator has no raw wait). */
    private fun sleep(device: UiDevice, millis: Long) {
        device.wait(Until.findObject(By.text(SLEEP_MARKER_UNUSED)), millis)
    }

    private companion object {
        const val APP_PACKAGE = "at.msd.friehs_bicha.cdcsvparser"
        const val SLEEP_MARKER_UNUSED = "sleep_marker_unused"
        const val PARSE_POST_TIMEOUT_MS = 180_000L
        const val WALLET_CHIP_SYMBOL = "ETH"

        /**
         * Name of the fixture CSV the host seeds into the app's
         * filesDir before the run (two transactions: a trade that
         * books the BTC wallet and a deposit that books the ETH
         * wallet - without the 8-coin second file, so the optional
         * price fetch of the re-import stays short: 2 symbols).
         */
        const val SEED_FILE_NAME = "2026-01-15-12-00-00.csv"

        /** The history button's stamp, locale-fixed "d.M. HH:mm". */
        val HISTORY_STAMP_PATTERN = Pattern.compile("\\d{1,2}\\.\\d{1,2}\\. \\d{2}:\\d{2}")
    }
}
