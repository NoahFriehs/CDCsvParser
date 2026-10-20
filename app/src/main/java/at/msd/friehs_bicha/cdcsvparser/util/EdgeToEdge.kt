package at.msd.friehs_bicha.cdcsvparser.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge support. targetSdk 35+ forces the window to draw behind the
 * system bars on API 35+ devices; once decorFitsSystemWindows is off the
 * insetting is the app's job, so this keeps the activity content clear of
 * the status/navigation bars - and, for activities that use the decor
 * action bar, below the action bar itself (otherwise the first rows of
 * lists and labels render under it).
 *
 * Call once per activity, after setContentView, with the activity's content
 * root as [root].
 */
object EdgeToEdge {
    fun enable(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // With a non-overlay decor action bar in edge-to-edge mode,
            // appcompat's ActionBarOverlayLayout already folds the action
            // bar height into the TOP system inset it dispatches to this
            // root (its onMeasure, API 21+ branch: status + bar height).
            // bars.top therefore clears status bar AND action bar by
            // itself - adding ?attr/actionBarSize on top of it doubled
            // the bar height (the dead band under the bar on every
            // screen). Activities without an action bar get a plain
            // system-bar inset, which bars.top still covers.
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            // The View-level padding above is the ONE bar clearance: zero
            // the system insets handed to the Compose roots so each
            // fragment's M3 Scaffold does not re-apply the status/nav bar
            // heights on top (it would double them, since the Scaffold
            // only excludes insets consumed elsewhere).
            insets.replaceSystemWindowInsets(0, 0, 0, 0)
        }
    }
}
