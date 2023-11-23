package at.msd.friehs_bicha.cdcsvparser.app

data class FirebaseAppmodel(
    val dbWallets: ArrayList<HashMap<String, *>>?,
    val dbOutsideWallets: ArrayList<HashMap<String, *>>?,
    val dbTransactions: ArrayList<HashMap<String, *>>?,
    val appType: AppType,
    val amountTxFailed: Long,
    val useStrictType: Boolean
)
