package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.general.AppModel

class ParseActivity : AppCompatActivity() {
    var appModel: AppModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parse)

        // calling the action bar
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        getAppModel()
        val btnFilter = findViewById<Button>(R.id.btn_filter)
        val btnTx = findViewById<Button>(R.id.btn_all_tx)
        val apiInfo = findViewById<TextView>(R.id.coinGeckoApiLabel)
        apiInfo.text = "All prices provided by \n CryptoCompare API"   //overwrites the link, bc we CG api is down atm
        btnFilter.setOnClickListener { view: View? ->
            if (appModel!!.isRunning) {
                val intent = Intent(this@ParseActivity, AssetsFilterActivity::class.java)
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel)
                startActivity(intent)
            }
        }
        btnTx.setOnClickListener { view: View? ->
            if (appModel!!.isRunning) {
                val intent = Intent(this@ParseActivity, TransactionsActivity::class.java)
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel)
                startActivity(intent)
            }
        }
        while (!appModel!!.isRunning) { //TODO: add loading icon
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }

        //trys to get the prices from api and the prints the values depending on the answer of coingeko api
        displayInformation()
    }

    private fun getAppModel() {
        appModel = intent.extras!!["AppModel"] as AppModel?
    }

    /**
     * Displays the prices of all assets
     */
    private fun displayInformation() {
        //get and set prices
        val t = Thread {
            try {
                displayTexts(appModel?.parseMap)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        t.start()
    }

    /**
     * Displays the prices of specificWallet
     *
     * @param texts the Map<String></String>, String> which should be displayed with id of View and text to set pairs
     */
    private fun displayTexts(texts: Map<String, String?>?) {
        texts!!.forEach { (key: String?, value: String?) ->
            val textView = findViewById<TextView>(resources.getIdentifier(key, "id", packageName))
            if (textView == null)
            {
                return  //TODO set here Breakpoint to see if there are any problems with the ids(should only occur if the xml file is changed or programmer error)
            }
            if (value == null) {
                runOnUiThread { textView.visibility = View.INVISIBLE }
            } else {
                runOnUiThread { textView.text = value }
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
}