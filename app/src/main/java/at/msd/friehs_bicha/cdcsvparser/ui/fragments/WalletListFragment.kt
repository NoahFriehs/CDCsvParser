package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * A fragment representing a list of Items.
 */
class WalletListFragment(val wallets: List<Wallet>) : Fragment() {

    private lateinit var walletAdapter: WalletAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet_list, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletAdapter = WalletAdapter(wallets)
        val rvWallets = view.findViewById<RecyclerView>(R.id.rvWallets)
        rvWallets.layoutManager = LinearLayoutManager(requireContext())
        rvWallets.adapter = walletAdapter
    }
}