package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
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

        // Not signed in: show the login screen as the effective root.
        if (savedInstanceState == null && FirebaseAuth.getInstance().currentUser == null) {
            navController.navigate(R.id.loginFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
