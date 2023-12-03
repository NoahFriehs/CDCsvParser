package at.msd.friehs_bicha.cdcsvparser.Core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.CardTxApp
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionData
import at.msd.friehs_bicha.cdcsvparser.util.FirebaseUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletXmlSerializer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CoreService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            FileLog.w(TAG, "Initialization failed. Intent is null.")
            return super.onStartCommand(null, flags, startId)
        }

        if (AppModelManager.isInitialized()) {
            appModel = AppModelManager.getInstance()
        }

        GlobalScope.launch {
            when (intent.action) {
                ACTION_START_SERVICE -> {
                    handleStartService()
                }

                ACTION_START_SERVICE_WITH_DATA -> {
                    handleStartServiceWithData(intent)
                }

                ACTION_START_SERVICE_WITH_FIREBASE_DATA -> {
                    handleStartServiceWithFirebaseData(intent)
                }

                ACTION_STOP_SERVICE -> {
                    FileLog.d(TAG, "Stopping service.")
                    stopSelf()
                }

                ACTION_RESTART_SERVICE -> {
                    FileLog.d(TAG, "Restarting service.")
                    //TODO: restartService()
                }

                else -> {
                    FileLog.w(TAG, "Initialization failed. Unknown action: ${intent.action}")
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleStartServiceWithFirebaseData(intent: Intent) {
        if (isCoreInitialized) {
            TODO()
        }
        if (intent.getBooleanExtra("isRunning", false)) {
            FileLog.d(TAG, "isRunning")
            isRunning = true
        }

        provideDataToActivity()

    }

    private fun handleStartServiceWithData(intent: Intent) {
        val data = intent.getStringArrayListExtra("data")
        val mode = intent.getIntExtra("mode", 0)
        if (data == null) {
            FileLog.e(TAG, "Initialization with data failed. Data is null.")
            return
        }
        when (isCoreInitialized) {
            true -> {
                val dataArray = Array<String>(data.size) { i -> data[i] }
                if (initWithData(dataArray, data.size, mode)) {
                    FileLog.d(TAG, "Initialization with data successful.")
                    isRunning = true
                } else {
                    FileLog.e(TAG, "Initialization with data failed.")
                    return
                }
            }

            false -> {
                if (appModel != null && data.size == 0) {
                    FileLog.i(TAG, "AppModel already initialized.")
                    isRunning = true
                    isInitialized = true
                } else {
                    appModel = AppModel(data, AppType.Default, false)
                    AppModelManager.setInstance(appModel!!)
                }
                isInitialized = true
                isRunning = true
            }
        }

        provideDataToActivity()

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun provideDataToActivity() {

        when (isCoreInitialized) {
            true -> {
                //TODO("call getCurrencies from core and return prices to it and then get the data from it")
                val currencies = getCurrencies()
                val prices = Array<Double>(currencies.size) { _ -> 0.0 }
                currencies.forEach {
                    prices[currencies.indexOf(it)] = priceProvider.getPrice(it)
                }
                setPrice(prices)

                // get Data from Core and set it to the LiveData
                val map: MutableMap<String, String?> = java.util.HashMap()
                val totalMoneySpent = getTotalMoneySpent()
                val totalMoneySpentString =
                    StringHelper.formatAmountToString(totalMoneySpent.toDouble())
                if (AssetValue.getInstance().isRunning) {
                    val amountOfAsset = getValueOfAssets()
                    val rewardValue = getTotalBonus()
                    map[R.id.assets_valueP.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset)
                    map[R.id.rewards_value.toString()] =
                        StringHelper.formatAmountToString(rewardValue)
                    map[R.id.profit_loss_value.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset - totalMoneySpent.toDouble())
                    map[R.id.money_spent_value.toString()] = totalMoneySpentString
                } else {
                    FileLog.e(TAG, "AssetValue is not running")
                    map[R.id.assets_valueP.toString()] = "no internet connection"
                    map[R.id.rewards_value.toString()] = "no internet connection"
                    map[R.id.profit_loss_value.toString()] = "no internet connection"
                    map[R.id.money_spent_value.toString()] = totalMoneySpentString
                }
                parsedDataLiveData.postValue(map)

                //get transactions from Core and set it to the LiveData
                val transactions = getTransactionsAsString()
                val transactions_ = ArrayList<Transaction>()
                transactions.forEach {
                    val txData =
                        TransactionData(0, "", 0, 0, "", 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0)
                    val dataString = it.replace("\n\t", "")
                    try {
                        txData.fromXml(dataString)
                        transactions_.add(Transaction(txData))
                    } catch (e: Exception) {
                        FileLog.e(TAG, "Exception: $e")
                    }
                    //txdata.fromXml(it)
                }
                transactionsLiveData.postValue(transactions_)

                //get Wallets from Core and set it to the LiveData
                val wallets = getWalletsAsString()
                val wallets_ = ArrayList<Wallet>()
                wallets.forEach {
                    val data = WalletXmlSerializer().deserializeFromXml(it)
                    wallets_.add(Wallet(data))
                }
                transactions_.forEach { tx ->
                    wallets_.find { it.walletId == tx.walletId }?.transactions?.add(tx)
                }
                walletsLiveData.postValue(wallets_)
                outsideWalletsLiveData.postValue(wallets_.filter { it.isOutsideWallet } as ArrayList<Wallet>)
                //cardWalletsLiveData.postValue(wallets_.filter { it.isCard } as ArrayList<Wallet>)
                allWalletsLiveData.postValue(wallets_)

                val walletNames_ = Array<String?>(wallets_.size) { _ -> null }
                wallets_.forEach {
                    wallets_.indexOf(it).let { index ->
                        walletNames_[index] = it.getTypeString()
                    }
                }
                walletNames.postValue(walletNames_)
                wallets_.forEach {
                    assetMaps.value!!.add(AssetData(it.walletId, getAssetMap(it.walletId)))
                }
            }

            false -> {
                GlobalScope.launch {
                    try {
                        while (!isRunning) {
                            delay(500)
                            FileLog.d(TAG, "Waiting for initialization to finish.")
                        }
                        FileLog.d(TAG, "isRunning")
                        walletsLiveData.postValue(appModel?.txApp?.wallets)
                        outsideWalletsLiveData.postValue(appModel?.txApp?.outsideWallets)
                        cardWalletsLiveData.postValue(appModel?.cardApp?.wallets)
                        if (appModel?.txApp != null && appModel?.txApp is CardTxApp) {
                            cardWalletsLiveData.postValue(appModel?.txApp?.wallets)
                        }

                        val allWallets = mutableListOf<Wallet>()
                        allWallets.addAll(appModel?.txApp?.wallets ?: emptyList())
                        allWallets.addAll(appModel?.cardApp?.wallets ?: emptyList())

                        allWalletsLiveData.postValue(allWallets.toCollection(ArrayList()))

                        val walletNames_ = Array<String?>(allWallets.size) { _ -> null }

                        allWallets.forEach {
                            allWallets.indexOf(it).let { index ->
                                walletNames_[index] = it.getTypeString()
                            }
                        }

                        walletNames.postValue(walletNames_)

                        val data = appModel?.parseMap
                        if (data == null) {
                            FileLog.e(TAG, "data is null")
                            return@launch
                        }
                        parsedDataLiveData.postValue(data!!)

                        val transactions_ = ArrayList<Transaction?>()
                        allWallets.forEach {
                            transactions_.addAll(it.transactions.toCollection(ArrayList()))
                        }
                        while (transactions_.contains(null)) {
                            FileLog.e(TAG, "transactions_ contains null")
                            transactions_.remove(null)
                        }
                        transactionsLiveData.postValue(transactions_ as ArrayList<Transaction>)
                        allWallets.forEach {
                            assetMaps.value!!.add(AssetData(it.walletId, getAssetMap(it.walletId)))
                        }
                    } catch (e: InterruptedException) {
                        FileLog.e(TAG, " : $e")
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }

    private fun getAssetMap(walletId: Int): Map<String, String?> {
        return when (isCoreInitialized) {
            true -> {

                val map = mutableMapOf<String, String?>()
                val moneySpent = getMoneySpentByWID(walletId)
                val total = StringHelper.formatAmountToString(moneySpent)
                val amountOfAsset = getValueOfAssetsByWID(walletId)
                val rewardValue = getTotalBonusByWID(walletId)
                if (AssetValue.getInstance().isRunning) {
                    map[R.id.assets_value.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset)
                    map[R.id.rewards_value.toString()] =
                        StringHelper.formatAmountToString(rewardValue)
                    map[R.id.profit_loss_value.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset - moneySpent)
                    map[R.id.money_spent_value.toString()] = total
                } else {
                    map[R.id.assets_value.toString()] = "no internet connection"
                    map[R.id.rewards_value.toString()] = "no internet connection"
                    map[R.id.profit_loss_value.toString()] = "no internet connection"
                    map[R.id.money_spent_value.toString()] = total
                }
                map
            }

            false -> {
                val specificWallet = appModel!!.txApp!!.wallets.find { it.walletId == walletId }
                appModel!!.getAssetMap(specificWallet)
            }
        }
    }

    private fun handleStartService() {
        when (isCoreInitialized) {
            true -> {
                if (init()) {
                    FileLog.d(TAG, "Initialization successful.")
                    isRunning = true
                    //TODO: call getCurrencies from core and return prices to it
                } else {
                    FileLog.w(TAG, "Initialization failed.")
                }
            }

            false -> {
                if (AppModelManager.isInitialized()) {
                    // tf we doing here
                    isInitialized = true
                    isRunning = true
                    FileLog.d(TAG, "AppModel already initialized.")
                } else {
                    if (PreferenceHelper.getIsAppModelSavedLocal(applicationContext)) {
                        AppModelManager.setInstance(AppModel())
                        isInitialized = true
                    } else {
                        FileLog.e(TAG, "AppModel not initialized. No data available.")
                    }
                }
            }
        }
    }

    private external fun init(): Boolean
    private external fun initWithData(data: Array<String>, dataSize: Int, mode: Int): Boolean

    private external fun getCurrencies(): Array<String>

    private external fun setPrice(prices: Array<Double>)

    private external fun getTotalMoneySpent(): Double
    private external fun getValueOfAssets(): Double
    private external fun getTotalBonus(): Double

    private external fun getTransactionsAsString(): Array<String>
    private external fun getWalletsAsString(): Array<String>

    private external fun getValueOfAssetsByWID(walletID: Int): Double
    private external fun getTotalBonusByWID(walletID: Int): Double
    private external fun getMoneySpentByWID(walletId: Int): Double


    companion object {

        private const val TAG = "CoreService"

        var isCoreInitialized = false
        var isInitialized = false

        var isRunning = false

        var dataLiveData = MutableLiveData<List<String>>()

        var firebaseDataLiveData = MutableLiveData<MutableList<FirebaseAppmodel>>()

        var parsedDataLiveData = MutableLiveData<Map<String, String?>>()

        var walletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var outsideWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var cardWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var allWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        val walletNames = MutableLiveData<Array<String?>>()
        val transactionsLiveData = MutableLiveData<ArrayList<Transaction>>()

        var currentWallet = MutableLiveData<Map<String, String?>>()
        val assetMaps = MutableLiveData<ArrayList<AssetData>>()


        var priceProvider: AssetValue = AssetValue.getInstance()
        var user = FirebaseAuth.getInstance().currentUser
        private var appModel: AppModel? = null


        init {
            dataLiveData.value = ArrayList()
            firebaseDataLiveData.value = ArrayList()
            assetMaps.value = ArrayList()
            try {
                System.loadLibrary("cdcsvparser")
                isCoreInitialized = true
                isInitialized = true
            } catch (e: UnsatisfiedLinkError) {
                FileLog.e(TAG, "Failed to load native library: ${e.message}")
            }
        }

        fun startService() {
            val intent = Intent(InstanceVars.applicationContext, CoreService::class.java)
            InstanceVars.applicationContext.startService(intent)
        }

        fun startServiceWithData(data: ArrayList<String>, mode: Int) {
            val intent = Intent(InstanceVars.applicationContext, CoreService::class.java)
            intent.action = ACTION_START_SERVICE_WITH_DATA
            intent.putExtra("data", data)
            intent.putExtra("mode", mode)
            InstanceVars.applicationContext.startService(intent)
        }

        fun startServiceWithFirebaseData(
            dbWallets: java.util.ArrayList<HashMap<String, *>>?,
            dbOutsideWallets: java.util.ArrayList<HashMap<String, *>>?,
            dbTransactions: java.util.ArrayList<HashMap<String, *>>?,
            appType: AppType,
            amountTxFailed: Long,
            useStrictType: Boolean
        ) {
            if (isCoreInitialized) {
                TODO()
            }

            AppModel(
                dbWallets,
                dbOutsideWallets,
                dbTransactions,
                appType,
                amountTxFailed,
                useStrictType
            ).let {
                AppModelManager.setInstance(it)
            }

            val intent = Intent(InstanceVars.applicationContext, CoreService::class.java)
            intent.action = ACTION_START_SERVICE_WITH_FIREBASE_DATA
//            intent.putExtra("data", data)
            intent.putExtra("isRunning", true)
            InstanceVars.applicationContext.startService(intent)
        }

        fun stopService() {
            val intent = Intent(InstanceVars.applicationContext, CoreService::class.java)
            InstanceVars.applicationContext.stopService(intent)
        }

        fun restartService() {
            stopService()
            startService()
        }

        /**
         * Returns the amount the asset is worth in EUR
         *
         * @return the amount the asset is worth in EUR
         */
        fun getValueOfAssetsFromWID(walletId: Int): Double {
            return if (isCoreInitialized) {
                try {
                    val w = walletsLiveData.value!!.find { it.walletId == walletId }
                    val valueOfWallet: Double
                    val price = w!!.currencyType.let { AssetValue.getInstance().getPrice(it) }
                    val amount = w.amount
                    valueOfWallet = price * amount.toDouble()
                    valueOfWallet
                } catch (e: Exception) {
                    FileLog.e("$TAG.getValueOfAssets", "Exception: $e")
                    0.0
                }
            } else
                try {
                    val w = appModel!!.txApp!!.wallets.find { it.walletId == walletId }
                    val valueOfWallet: Double
                    val price = w!!.currencyType.let { AssetValue.getInstance().getPrice(it) }
                    val amount = w.amount
                    valueOfWallet = price * amount.toDouble()
                    valueOfWallet
                } catch (e: Exception) {
                    FileLog.e("$TAG.getValueOfAssets", "Exception: $e")
                    0.0
                }
        }


        fun getWalletAdapter(walletId: Int): Map<String, String?> {
            return when (isCoreInitialized) {
                true -> {
                    val w = allWalletsLiveData.value!!.find { it.walletId == walletId }
                    AppModel.getWalletAdapter(w!!)
                }

                false -> {
                    val w = appModel!!.txApp!!.wallets.find { it.walletId == walletId }
                    AppModel.getWalletAdapter(w!!)
                }
            }
        }

        fun getAssetMap(walletId: Int): Map<String, String?> {
            return assetMaps.value!!.find { it.walletId == walletId }!!.data
        }


        fun getTransactionAdapter(transaction: Transaction): Map<String, String?> {
            return when (isCoreInitialized) {
                true -> {
                    val defaultLocale = Locale.getDefault()
                    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", defaultLocale)
                    val map: MutableMap<String, String?> = java.util.HashMap()
                    map[R.id.tv_assetAmountValue.toString()] =
                        StringHelper.formatAmountToString(
                            transaction.amount.toDouble(),
                            6,
                            transaction.currencyType
                        )
                    map[R.id.tv_transactionId.toString()] = transaction.transactionId.toString()
                    map[R.id.tv_date.toString()] =
                        transaction.date?.let { dateFormat.format(it).toString() }
                    map[R.id.tv_descriptionValue.toString()] = transaction.description
                    map[R.id.tv_amountValue.toString()] =
                        StringHelper.formatAmountToString(transaction.nativeAmount.toDouble())
                    map
                }

                false -> {
                    appModel!!.getTransactionAdapter(transaction)
                }
            }
        }

        fun getTransaction(transactionId: Int): Transaction {
            return when (isCoreInitialized) {
                true -> {
                    transactionsLiveData.value!!.find { it.transactionId == transactionId }!!
                }

                false -> {
                    transactionsLiveData.value!!.find { it.transactionId == transactionId }!!
                }
            }
        }


        fun saveDataToFirebase() {
            if (isCoreInitialized) {
                FileLog.e(TAG, "saveDataToFirebase: Not implemented yet")
                return
                TODO("save data to firebase")  //TODO: save data to firebase
            }
            appModel?.let {
                user = FirebaseAuth.getInstance().currentUser   //refresh user
                if (user != null) Thread {
                    FirebaseUtil(InstanceVars.applicationContext).saveDataToFirebase(
                        it
                    )
                }.start()
            }

        }


        const val ACTION_START_SERVICE = "at.msd.friehs_bicha.cdcsvparser.Core.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "at.msd.friehs_bicha.cdcsvparser.Core.action.STOP_SERVICE"
        const val ACTION_RESTART_SERVICE =
            "at.msd.friehs_bicha.cdcsvparser.Core.action.RESTART_SERVICE"
        const val ACTION_START_SERVICE_WITH_DATA =
            "at.msd.friehs_bicha.cdcsvparser.Core.action.START_SERVICE_WITH_DATA"
        const val ACTION_START_SERVICE_WITH_FIREBASE_DATA =
            "at.msd.friehs_bicha.cdcsvparser.Core.action.START_SERVICE_WITH_FIREBASE_DATA"

    }
}