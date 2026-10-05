package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import at.msd.friehs_bicha.cdcsvparser.ui.compose.Motion
import at.msd.friehs_bicha.cdcsvparser.util.EdgeToEdge
import com.google.firebase.auth.FirebaseAuth

/**
 * Single-activity shell: hosts every screen as a navigation destination
 * (see res/navigation/nav_graph.xml). Edge-to-edge insetting happens once,
 * here, for the whole content frame.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_host)
        val host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = host.navController
        setupActionBarWithNavController(navController)
        EdgeToEdge.enable(this, findViewById(android.R.id.content))
        installDestinationMotion()

        // Not signed in: show the login screen as the effective root.
        if (savedInstanceState == null && FirebaseAuth.getInstance().currentUser == null) {
            navController.navigate(R.id.loginFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * Motion (MOD 2.6): fade each destination in on the fragment view that
     * hosts it. Direction-neutral on purpose - no translation - so the
     * text-based UIAutomator tests never observe a moving frame. The first
     * screen of an activity instance is skipped: the activity's own launch
     * animation (or the process restore) covers it.
     */
    private fun installDestinationMotion() {
        val reduceMotion = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) <= 0f
        var firstScreen = true
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (fm.primaryNavigationFragment != fragment) return
                    val initialLaunch = firstScreen
                    firstScreen = false
                    if (reduceMotion || initialLaunch) return
                    view.doOnPreDraw {
                        view.animate().cancel()
                        view.alpha = 0f
                        view.animate()
                            .alpha(1f)
                            .setDuration(Motion.DESTINATION_FADE_MS.toLong())
                            // The Compose FastOutSlowIn curve (cubic-bezier 0.4, 0, 0.2, 1).
                            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
                            .start()
                    }
                }
            },
            true,
        )
    }
}
