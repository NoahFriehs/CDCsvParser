package at.msd.friehs_bicha.cdcsvparser

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.activity.WalletViewActivity

class ParseActivity : AppCompatActivity() {
    private lateinit var progressDialog: Dialog

    @JvmField
    @Volatile
    var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showProgressDialog()
        setContentView(R.layout.activity_parse)

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
            displayTexts(it)
            FileLog.d("ParseActivity", "parsedDataLiveData changed")
            isReady = true
            hideProgressDialog()
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
                    runOnUiThread { textView.text = resources.getString(R.string.no_internet_connection) }
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
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
    }

    fun hideProgressDialog() {
        progressDialog.dismiss()
    }
}