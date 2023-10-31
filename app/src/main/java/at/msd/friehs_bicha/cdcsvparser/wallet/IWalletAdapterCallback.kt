package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletAdapter

interface IWalletAdapterCallback {
    fun onCallback(value: MutableMap<String, String?>, holder: WalletAdapter.WalletViewHolder)
}