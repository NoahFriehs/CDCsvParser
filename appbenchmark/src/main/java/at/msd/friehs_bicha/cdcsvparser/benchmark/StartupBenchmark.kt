package at.msd.friehs_bicha.cdcsvparser.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The measured cold-start delta of the shipped Baseline Profile.
 * Both legs run on the SAME release build - a fresh-install comparison:
 * "before" = partial dexopt with the profile explicitly disabled,
 * "after"  = partial dexopt with the installed profile required
 * (errors if it is missing). Timings are the trace startup metrics
 * (time to initial / full display); emulator numbers are relative
 * measurements, not device-predictive.
 *
 * This class must not reference any app class: it is the one that runs
 * as a RELEASE test (profiles only install for release builds) and the
 * release variant of this module compiles without the app on the
 * classpath. The landing texts are therefore matched raw, in both
 * shipped locales.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(
        // Disable + a couple of uninstrumented warmup iterations:
        // the pre-compile set is established without any profile input
        // (the realistic profile-less warm state) before the measured
        // iterations run.
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 2,
        ),
    )

    @Test
    fun startupWithProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    private fun startup(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = APP_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            landOnMain(device)
        }

    private fun landOnMain(device: UiDevice, timeoutMs: Long = 120_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findAny(device, WELCOME_TEXTS) != null) {
                device.waitForIdle()
                return
            }
            // Signed-out devices land on the login screen first; the
            // skip is the documented pre-login startup step.
            findAny(device, SKIP_TEXTS)?.click()
            sleep(device, 750)
        }
        throw IllegalStateException("main screen did not appear within ${timeoutMs} ms")
    }

    private fun findAny(device: UiDevice, texts: Array<out String>): UiObject2? {
        for (text in texts) {
            val obj: UiObject2? = device.findObject(By.text(text))
            if (obj != null) return obj
        }
        return null
    }

    private fun sleep(device: UiDevice, millis: Long) {
        device.wait(Until.findObject(By.text(SLEEP_MARKER_UNUSED)), millis)
    }

    private companion object {
        const val APP_PACKAGE = "at.msd.friehs_bicha.cdcsvparser"
        const val SLEEP_MARKER_UNUSED = "sleep_marker_unused"
        const val ITERATIONS = 10

        // en + de of string/welcome_message and string/use_app_without_login.
        val WELCOME_TEXTS = arrayOf(
            "Welcome to CDCsvParser",
            "Willkommen zu CDCsvParser",
        )
        val SKIP_TEXTS = arrayOf(
            "Use App without Login",
            "App ohne Anmeldung verwenden",
        )
    }
}
