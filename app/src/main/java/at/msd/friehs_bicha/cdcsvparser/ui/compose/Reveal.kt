package at.msd.friehs_bicha.cdcsvparser.ui.compose

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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

    // ---- Expressive springs (2026 look) ---------------------------------
    // The expressive motion scheme is internal-only on the stable 1.4
    // surface, so the two specs are hand-rolled: SPATIAL drives
    // position/size and is underdamped (damping 0.8 - it overshoots and
    // settles, the expressive "bounce"); EFFECT drives opacity/color and is
    // critically damped (1.0 - no overshoot). Every call site short-circuits
    // on [rememberReduceMotion].

    const val SPATIAL_DAMPING = 0.8f

    // Named arguments on purpose: the spring() parameter order in the
    // stable line is (dampingRatio, stiffness) - not the (stiffness,
    // damping) of the pre-1.12 API the values were copied from.
    val spatialSpring: SpringSpec<Float> =
        spring<Float>(dampingRatio = SPATIAL_DAMPING, stiffness = Spring.StiffnessLow)

    val effectSpring: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    /** The segmented chip pop starts from this scale and springs to 1.0. */
    const val SELECTION_POP_START = 0.96f
}

/**
 * Overview -> detail spatial reveal: the detail header springs in from a
 * short top offset (the underdamped [Motion.spatialSpring] - a slight
 * overshoot before it settles) while its opacity rides the critically
 * damped [Motion.effectSpring]. With reduced motion the content renders
 * statically. [content] is stacked VERTICALLY (like a [Column]) - the
 * reveal is the box that carries the alpha/offset, so the content must not
 * bring its own conflicting size modifiers (a fillMaxSize child fills the
 * revealed area, as before the reveal existed).
 */
@Composable
fun SpatialReveal(distance: Dp = 20.dp, content: @Composable () -> Unit) {
    if (rememberReduceMotion()) {
        Column { content() }
        return
    }
    val density = LocalDensity.current
    val offset = remember(density) { Animatable(with(density) { distance.toPx() }) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { offset.animateTo(0f, Motion.spatialSpring) }
        alpha.animateTo(1f, Motion.effectSpring)
    }
    Box(
        modifier = Modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, offset.value.roundToInt()) },
    ) {
        Column { content() }
    }
}

/**
 * Segmented-control selection pop: when a segment becomes active it springs
 * from [Motion.SELECTION_POP_START] to full scale (the [Motion.spatialSpring]
 * overshoot makes the active segment "pop"), a deselection snaps back.
 * [rememberReduceMotion] returns the plain [Modifier].
 */
@Composable
fun Modifier.selectionPopModifier(selected: Boolean): Modifier {
    if (rememberReduceMotion()) return this
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(Motion.SELECTION_POP_START)
            scale.animateTo(1f, Motion.spatialSpring)
        } else {
            scale.snapTo(1f)
        }
    }
    return Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin.Center
    }
}

/**
 * Touch-target floor (48×48 dp): M3 buttons render at 40 dp and filter
 * chips at 32 dp - below the accessibility floor. This enlarges the HIT
 * AREA: the component measures at >=48 dp and centers its own content
 * (pure, non-composable). Hand-rolled: the platform
 * `minimumInteractiveComponentSize` modifier (which keeps the visual at its
 * spec size while enlarging the hit area) is not exposed by the resolved
 * foundation 1.12.1 line - verified against the artifact. Applied to every
 * interactive element below the floor (buttons, segmented chips, filter
 * chips, icon buttons, row-level clickable texts); list rows that already
 * measure >=48 dp are unaffected.
 */
fun Modifier.a11yTarget(): Modifier = defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)

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
