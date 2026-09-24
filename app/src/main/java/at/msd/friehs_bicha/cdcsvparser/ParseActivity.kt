package at.msd.friehs_bicha.cdcsvparser

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.activity.WalletViewActivity
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker

class ParseActivity : AppCompatActivity() {
    private lateinit var progressDialog: Dialog

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        if (!isFinishing) {
            FileLog.e(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms - giving up.")
            Toast.makeText(this, R.string.parsing_timeout, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @JvmField
    @Volatile
    var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showProgressDialog()
        setContentView(R.layout.activity_parse)

        // Watchdog: if neither parsed data nor an error arrives (e.g. a core
        // failure that never emits an event), bail out instead of leaving the
        // user on a progress screen forever.
        timeoutHandler.postDelayed(parseTimeout, PARSE_TIMEOUT_MS)

        // calling the action bar
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        val btnFilter = findViewById<Button>(R.id.btn_filter)
        val btnTx = findViewById<Button>(R.id.btn_all_tx)
        btnFilter.setOnClickListener {
                val intent = Intent(this@ParseActivity, WalletViewActivity::class.java)
                startActivity(intent)
        }
        btnTx.setOnClickListener {
            val intent = Intent(this@ParseActivity, TransactionsActivity::class.java)
            startActivity(intent)
        }

        //trys to get the prices from api and the prints the values depending on the answer of coingeko api
        displayInformation()
    }

    /**
     * Displays the prices of all assets
     */
    private fun displayInformation() {
        CoreService.parsedDataLiveData.observe(this) {
            Benchmarker.stop()
            displayTexts(it)
            FileLog.d("ParseActivity", "parsedDataLiveData changed")
            isReady = true
            hideProgressDialog()
            if (CoreService.lastFailedLines > 0) {
                Toast.makeText(
                    this,
                    getString(R.string.unparsable_lines_skipped, CoreService.lastFailedLines),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        CoreService.errorCounter.observe(this) {
            FileLog.w("ParseActivity", "errorCounterLiveData changed")
            Toast.makeText(this, R.string.error_while_parsing, Toast.LENGTH_LONG).show()
            isReady = false
            hideProgressDialog()
            finish()
        }
    }

    /**
     * Displays the prices of specificWallet
     *
     * @param texts the Map<String></String>, String> which should be displayed with id of View and text to set pairs
     */
    private fun displayTexts(texts: Map<String, String?>?) {
        if (texts == null) {
            FileLog.e("ParseActivity", "texts is null")
            return
        }
        texts.forEach { (key: String?, value: String?) ->
            val textView = findViewById<TextView>(resources.getIdentifier(key, "id", packageName))
            if (textView == null) {
                FileLog.e("ParseActivity", "textView is null for key: $key")
                return
            }
            when (value) {
                "no internet connection" -> {
                    runOnUiThread {
                        textView.text = resources.getString(R.string.no_internet_connection)
                    }
                }

                null -> {
                    runOnUiThread { textView.visibility = View.INVISIBLE }
                }

                else -> {
                    runOnUiThread { textView.text = value }
                }
            }
        }
    }

    /**
     * Set the back button in action bar
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun showProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog.setContentView(R.layout.progress_icon)
        // Cancelable so the user always has an escape hatch; dismissing it
        // leaves the screen.
        progressDialog.setCancelable(true)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setOnDismissListener { finish() }
        progressDialog.show()
    }

    fun hideProgressDialog() {
        timeoutHandler.removeCallbacks(parseTimeout)
        progressDialog.dismiss()
    }

    companion object {
        private const val TAG = "ParseActivity"
        private const val PARSE_TIMEOUT_MS = 90_000L
    }
}