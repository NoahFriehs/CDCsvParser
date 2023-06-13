package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import at.msd.friehs_bicha.cdcsvparser.LoginActivity
import at.msd.friehs_bicha.cdcsvparser.MainActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import com.google.firebase.auth.FirebaseAuth

class StartingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starting)

        FileLog.init(applicationContext)

        val user = FirebaseAuth.getInstance().currentUser
        val intent = if (user != null) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(intent)
            finish()
        },1000)
    }
}
