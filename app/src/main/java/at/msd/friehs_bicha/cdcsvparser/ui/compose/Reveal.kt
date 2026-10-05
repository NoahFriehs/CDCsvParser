package at.msd.friehs_bicha.cdcsvparser.ui.compose

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Motion specs for the polish pass (MOD 2.6). Deliberately short and
 * low-amplitude so the text-based UIAutomator tests (stable content, no
 * coordinate dependence on transitions) never observe unsettled frames.
 */
object Motion {
    /** Destination-screen fade-in driven from the fragment NavHost. */
    const val DESTINATION_FADE_MS = 220
    /** Async section reveals (parse progress bar, chart panel, main extras). */
    const val SECTION_ENTER_MS = 240
    const val SECTION_EXIT_MS = 160
    /** Lazy list rows: the first-settle appear. */
    const val ITEM_DURATION_MS = 220
    const val ITEM_RISE_DP = 6
    const val ITEM_STAGGER_MS = 25
    const val ITEM_MAX_STAGGER_MS = 400

    /** Rows composed after this (i.e. while scrolling) appear instantly. */
    const val INITIAL_SETTLE_WINDOW_MS = 600

    fun sectionEnter(): EnterTransition =
        fadeIn(tween(SECTION_ENTER_MS, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(SECTION_ENTER_MS, easing = FastOutSlowInEasing)) { it / 20 }

    fun sectionExit(): ExitTransition = fadeOut(tween(SECTION_EXIT_MS))
}

/** The system-wide animator scale at 0 ("remove animations") means no motion. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) <= 0f
    }
}

/** Captured once per list composition: the moment the list first appeared. */
@Composable
fun rememberListSettleStart(): Long = remember { SystemClock.elapsedRealtime() }

/**
 * First-settle appear for a lazy-list row: fade in + rise with a per-index
 * stagger. Rows composed after the settle window (i.e. while scrolling)
 * snap in immediately, so long lists scroll calmly.
 */
@Composable
fun StaggeredListAppear(
    index: Int,
    settleStartedAt: Long,
    content: @Composable () -> Unit,
) {
    if (rememberReduceMotion()) {
        content()
        return
    }
    val alpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    val rise = remember(density) {
        Animatable(with(density) { Motion.ITEM_RISE_DP.dp.toPx() })
    }
    LaunchedEffect(index, settleStartedAt) {
        val spec = tween<Float>(Motion.ITEM_DURATION_MS, easing = FastOutSlowInEasing)
        val ageMs = SystemClock.elapsedRealtime() - settleStartedAt
        if (ageMs > Motion.INITIAL_SETTLE_WINDOW_MS) {
            alpha.snapTo(1f)
            rise.snapTo(0f)
        } else {
            val delayMs = min(index * Motion.ITEM_STAGGER_MS, Motion.ITEM_MAX_STAGGER_MS)
            if (delayMs > 0) delay(delayMs.toLong())
            launch { rise.animateTo(0f, spec) }
            alpha.animateTo(1f, spec)
        }
    }
    Box(
        modifier = Modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, rise.value.roundToInt()) },
    ) {
        content()
    }
}
