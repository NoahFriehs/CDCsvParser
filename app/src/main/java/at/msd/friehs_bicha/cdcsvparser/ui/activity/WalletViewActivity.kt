package at.msd.friehs_bicha.cdcsvparser.ui.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletAdapter
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletListFragment

class WalletViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_view)
        var appModel = AppModelManager.getInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, WalletListFragment(appModel.txApp!!.wallets))
            .commit()
    }
}