package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * Activity for the about us page
 */
class AboutUsActivity : AppCompatActivity() {

    companion object {
        private const val EMAIL_ADDRESS = "cdcsvparser@gmail.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setupEmailButton()
    }

    /**
     * Set up the email button click listener
     */
    private fun setupEmailButton() {
        val emailButton = findViewById<TextView>(R.id.tv_mailString)

        emailButton.setOnClickListener {
            val emailIntent = createEmailIntent()

            // Ensure there's an app to handle this intent
            emailIntent.resolveActivity(packageManager)?.let {
                startActivity(Intent.createChooser(emailIntent, null))
            }
        }
    }

    /**
     * Create the email intent
     */
    private fun createEmailIntent(): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
        }
    }

    /**
     * Handle options item selected
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}