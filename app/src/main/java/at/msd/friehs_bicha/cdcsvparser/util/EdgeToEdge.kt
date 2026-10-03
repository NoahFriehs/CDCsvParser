package at.msd.friehs_bicha.cdcsvparser.util

import android.app.Activity
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
    private val actionBarHeight = HashMap<Activity, Int>()

    fun enable(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top + decorActionBarHeight(activity),
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }
    }

    /** Height of the decor (window) action bar, 0 when the activity has none. */
    private fun decorActionBarHeight(activity: Activity): Int {
        if (activity !is AppCompatActivity) return 0
        if (activity.supportActionBar == null) return 0
        return actionBarHeight.getOrPut(activity) {
            // ?attr/actionBarSize from the appcompat namespace
            val attr = TypedValue()
            activity.supportActionBar!!.themedContext.theme.resolveAttribute(
                androidx.appcompat.R.attr.actionBarSize, attr, true
            )
            attr.resourceId?.let {
                activity.resources.getDimensionPixelSize(it)
            } ?: 0
        }
    }
}
