package at.msd.friehs_bicha.cdcsvparser.core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.app.AppStatus
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.CardTxApp
import at.msd.friehs_bicha.cdcsvparser.app.DataTypes
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.app.TxAppFactory
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars.applicationContext
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.CCDBTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.DBTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionData
import at.msd.friehs_bicha.cdcsvparser.util.FirebaseUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CCDBWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.DBWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletData
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletXmlSerializer
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.function.Consumer

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

        makeSaveDirIfNeeded()

        useCpp = PreferenceHelper.getUseCpp(applicationContext)

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

                ACTION_SAVE_DATA_TO_FIREBASE -> {
                    handleSaveDataToFirebase()
                }

                ACTION_STOP_SERVICE -> {
                    FileLog.d(TAG, "Stopping service.")
                    stopSelf()
                }

                ACTION_RESTART_SERVICE -> {
                    FileLog.d(TAG, "Restarting service.")
                    //TODO: restartService()
                    TODO()
                }

                else -> {
                    FileLog.w(TAG, "Initialization failed. Unknown action: ${intent.action}")
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleSaveDataToFirebase() {
        user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.e(TAG, "User is null")
            return
        }

        //wait until walletNames is set
        while (walletNames.value == null) {
            FileLog.v(TAG, "Waiting for walletNames to be set.")
            suspend { delay(500) }
        }


        saveToFireBase()
    }

    private fun handleStartServiceWithFirebaseData(intent: Intent) {
        GlobalScope.launch {
            loadFromFirebase()
            while (!isRunning) {
                FileLog.d(TAG, "Waiting for FB initialization to finish.")
                delay(500)
            }
            provideDataToActivity()
        }
    }

    private fun handleStartServiceWithData(intent: Intent) {
        val data = intent.getStringArrayListExtra("data")
        val mode = intent.getIntExtra("mode", 0)
        if (data == null) {
            FileLog.e(TAG, "Initialization with data failed. Data is null.")
            return
        }
        when (isCoreInitialized && useCpp) {
            true -> {
                val dataArray = Array<String>(data.size) { i -> data[i] }
                if (initWithData(dataArray, data.size, mode, logFilePath)) {
                    FileLog.d(TAG, "Initialization with data successful.")
                    isRunning = true
                } else {
                    FileLog.e(TAG, "Initialization with data failed.")
                    return
                }
                checkAndSetModes()
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
                appModel?.let {
                    hasCardTx = it.hasCard()
                    hasCryptoTx = it.hasTxModule()
                }
            }
        }

        provideDataToActivity()

    }

    private fun checkAndSetModes() {
        when (getModes()) {
            1 -> {
                hasCryptoTx = true
                hasCardTx = false
            }

            2 -> {
                hasCryptoTx = false
                hasCardTx = true
            }

            3 -> {
                hasCryptoTx = true
                hasCardTx = true
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun provideDataToActivity() {

        when (isCoreInitialized && useCpp) {
            true -> {

                calculateWalletBalances()

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
                    StringHelper.formatAmountToString(totalMoneySpent)
                if (AssetValue.getInstance().isRunning) {
                    val amountOfAsset = getValueOfAssets()
                    val rewardValue = getTotalBonus()
                    map[R.id.assets_valueP.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset)
                    map[R.id.rewards_value.toString()] =
                        StringHelper.formatAmountToString(rewardValue)
                    map[R.id.profit_loss_value.toString()] =
                        StringHelper.formatAmountToString(amountOfAsset - totalMoneySpent)
                    map[R.id.money_spent_value.toString()] = totalMoneySpentString
                } else {
                    FileLog.e(TAG, "AssetValue is not running")
                    map[R.id.assets_valueP.toString()] = "no internet connection"
                    map[R.id.rewards_value.toString()] = "no internet connection"
                    map[R.id.profit_loss_value.toString()] = "no internet connection"
                    map[R.id.money_spent_value.toString()] = totalMoneySpentString
                }
                parsedDataLiveData.postValue(map)

                //get transactions from core and set it to the LiveData
                val transactions = getTransactionsAsString()
                //TODO getCardTxAsStrings
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

                //get Wallets from core and set it to the LiveData
                val wallets = getWalletsAsString()
                //TODO getCardWalletsAsStrings
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
                cardWalletsLiveData.postValue(wallets_.filterIsInstance<CroCardWallet>() as ArrayList<Wallet>)
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


                //save(savePath)
                //load(savePath)
            }

            false -> {
                GlobalScope.launch {
                    try {
                        while (!isRunning) {
                            delay(500)
                            FileLog.d(TAG, "Waiting for initialization to finish.")
                        }
                        FileLog.d(TAG, "isRunning")
                        if (appModel == null) {
                            FileLog.w(TAG, "AppModel is null")
                            appModel = AppModelManager.getInstance()
                            if (appModel == null) {
                                FileLog.e(TAG, "AppModel is null")
                                return@launch
                            }
                        }
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
                        val cardTransactions_ = ArrayList<CroCardTransaction?>()
                        appModel?.txApp?.wallets?.forEach {
                            transactions_.addAll(it.transactions.toCollection(ArrayList()))
                        }
                        appModel?.cardApp?.wallets?.forEach {
                            cardTransactions_.addAll(it.transactions.toCollection(ArrayList()) as ArrayList<CroCardTransaction?>)
                        }
                        appModel?.txApp?.outsideWallets?.forEach {
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
        when (isCoreInitialized && useCpp) {
            true -> {
                if (init(logFilePath, savePath)) {
                    FileLog.d(TAG, "Initialization successful.")
                    isRunning = true
                    provideDataToActivity()
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

    private fun loadFromFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val db = Firebase.firestore

        FirebaseUtil.getUserDataFromFirestore(
            uid,
            db
        ) { userMap ->
            loadFromMap(userMap)
            FileLog.i("FirebaseUtil", "User data loaded successfully")
        }
    }

    private fun loadFromMap(userMap: HashMap<String, Any>) {
        userMap.let {
            val appSettingsMap = it["appSettings"] as HashMap<String, Any>?
            val appSettings = AppSettings().fromHashMap(appSettingsMap!!)
            if (!appSettings.compareVersionsWithDefault()) {
                return
            }
            hasCryptoTx = appSettings.hasCryptoTx == "true"
            hasCardTx = appSettings.hasCardTx == "true"
            val dbWallets = it["wallets"] as ArrayList<HashMap<String, *>>?
            val dbOutsideWallets = it["outsideWallets"] as ArrayList<HashMap<String, *>>?
            val dbTransactions = it["transactions"] as ArrayList<HashMap<String, *>>?
            val dbCardWallets = it["cardWallets"] as ArrayList<HashMap<String, *>>?
            val dbCardTransactions =
                it["cardTransactions"] as ArrayList<HashMap<String, *>>?

            PreferenceHelper.setUseStrictType(
                InstanceVars.applicationContext,
                appSettings.useStrictType
            )

            if (dbCardWallets.isNullOrEmpty() && dbCardTransactions.isNullOrEmpty()) {
                hasCardTx = false
            }
            if (dbWallets.isNullOrEmpty() && dbOutsideWallets.isNullOrEmpty() && dbTransactions.isNullOrEmpty()){
                hasCryptoTx = false
            }

            if (hasCryptoTx) {
                when (isCoreInitialized && useCpp) {
                    true -> {
                        val txXMl = mutableListOf<String>()
                        val walletXml = mutableListOf<String>()

                        dbTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            txXMl.add(
                                TransactionData.fromTransaction(Transaction.fromDb(hashMap)).toXml()
                            )
                        })
                        dbWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            walletXml.add(
                                WalletXmlSerializer().serializeToXml(
                                    WalletData.fromWallet(
                                        CDCWallet.fromDb(hashMap)
                                    )
                                )
                            )
                        })
                        dbOutsideWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            walletXml.add(
                                WalletXmlSerializer().serializeToXml(
                                    WalletData.fromWallet(
                                        CDCWallet.fromDb(hashMap)
                                    )
                                )
                            )
                        })

                        val dataArray = Array<String>(txXMl.size) { i -> txXMl[i] }
                        val walletArray = Array<String>(walletXml.size) { i -> walletXml[i] }

                        init(logFilePath, "")

                        setTransactionData(dataArray)
                        setWalletData(walletArray)

                    }

                    false -> {
                        val appModel = AppModel(
                            dbWallets,
                            dbOutsideWallets,
                            dbTransactions,
                            AppType.CdCsvParser,
                            0,
                            appSettings.useStrictType
                        )
                        AppModelManager.setInstance(appModel)
                    }
                }
            }
            if (hasCardTx && hasCryptoTx) {
                when (isCoreInitialized) {
                    true -> {
                        dbCardWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardWalletData(
                                arrayOf(
                                    WalletXmlSerializer().serializeToXml(
                                        WalletData.fromWallet(
                                            CroCardWallet.fromDb(
                                                hashMap
                                            )
                                        )
                                    )
                                )
                            )
                        })
                        dbCardTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardTransactionData(
                                arrayOf(
                                    TransactionData.fromTransaction(
                                        CroCardTransaction.fromDb(
                                            hashMap
                                        )
                                    ).toXml()
                                )
                            )
                        })
                    }

                    false -> {
                        AppModelManager.getInstance()?.cardApp = TxAppFactory.createTxApp(
                            AppType.CroCard,
                            AppStatus.importFromFB,
                            appSettings.useStrictType,
                            hashMapOf(
                                DataTypes.dbWallets to dbCardWallets,
                                DataTypes.dbTransactions to dbCardTransactions,
                                DataTypes.amountTxFailed to 0
                            )
                        ) as CardTxApp
                    }
                }
            }
            if (hasCardTx) {
                when (isCoreInitialized) {
                    true -> {

                        init(logFilePath, savePath)

                        dbCardWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardWalletData(
                                arrayOf(
                                    WalletXmlSerializer().serializeToXml(
                                        WalletData.fromWallet(
                                            CroCardWallet.fromDb(
                                                hashMap
                                            )
                                        )
                                    )
                                )
                            )
                        })
                        dbCardTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardTransactionData(
                                arrayOf(
                                    TransactionData.fromTransaction(
                                        CroCardTransaction.fromDb(
                                            hashMap
                                        )
                                    ).toXml()
                                )
                            )
                        })
                    }

                    false -> {
                        val appModel = AppModel(
                            dbCardWallets,
                            null,
                            dbCardTransactions,
                            AppType.CroCard,
                            0,
                            appSettings.useStrictType
                        )
                        AppModelManager.setInstance(appModel)
                    }
                }
            }
            isInitialized = true
            isRunning = true
        }
    }

    private fun saveToMap(): HashMap<String, Any> {
        val userMap = HashMap<String, Any>()

        val appSettingsMap = HashMap<String, Any>()
        appSettingsMap["hasCryptoTx"] = hasCryptoTx.toString()
        appSettingsMap["hasCardTx"] = hasCardTx.toString()
        appSettingsMap["useStrictType"] =
            PreferenceHelper.getUseStrictType(applicationContext).toString()
        appSettingsMap["version"] = AppSettings().dbVersion
        userMap["appSettings"] = appSettingsMap

        val dbWallets = ArrayList<DBWallet>()
        val dbOutsideWallets = ArrayList<DBWallet>()
        val dbTransactions = ArrayList<DBTransaction>()
        val dbCardWallets = ArrayList<CCDBWallet>()
        val dbCardTransactions = ArrayList<CCDBTransaction>()

        walletsLiveData.value?.forEach {
            if (it is CDCWallet) {
                dbWallets.add(DBWallet(it))
            } else if (it is CroCardWallet) {
                dbCardWallets.add(CCDBWallet(it))
            }
        }
        outsideWalletsLiveData.value?.forEach {
            if (it is CDCWallet) {
                dbOutsideWallets.add(DBWallet(it))
            }
        }
        transactionsLiveData.value?.forEach {
            if (it is Transaction) {
                dbTransactions.add(DBTransaction(it))
            } else if (it is CroCardTransaction) {
                dbCardTransactions.add(CCDBTransaction(it))
            }
        }
        cardTransactionsLiveData.value?.forEach {
            if (it is CroCardTransaction) {
                dbCardTransactions.add(CCDBTransaction(it))
            }
        }
        cardWalletsLiveData.value?.forEach {
            if (it is CroCardWallet) {
                dbCardWallets.add(CCDBWallet(it))
            }
        }

        userMap["wallets"] = dbWallets
        userMap["outsideWallets"] = dbOutsideWallets
        userMap["transactions"] = dbTransactions
        userMap["cardWallets"] = dbCardWallets
        userMap["cardTransactions"] = dbCardTransactions

        return userMap
    }

    private external fun init(logFilePath: String, savePath: String): Boolean
    private external fun initWithData(
        data: Array<String>,
        dataSize: Int,
        mode: Int,
        logFilePath: String
    ): Boolean

    private external fun save(savePath: String) //TODO: does not work in and
    private external fun load(savePath: String)

    private external fun getModes(): Int

    private external fun setWalletData(walletData: Array<String>)
    private external fun setTransactionData(transactionData: Array<String>)
    private external fun setCardWalletData(cardWalletData: Array<String>)
    private external fun setCardTransactionData(cardTransactionData: Array<String>)

    private external fun calculateWalletBalances()

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
        var useCpp = true

        var isRunning = false

        var hasCardTx = false
        var hasCryptoTx = false

        var dataLiveData = MutableLiveData<List<String>>()

        var firebaseDataLiveData = MutableLiveData<MutableList<FirebaseAppmodel>>()

        var parsedDataLiveData = MutableLiveData<Map<String, String?>>()

        var walletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var outsideWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var cardWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var allWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        val walletNames = MutableLiveData<Array<String?>>()
        val transactionsLiveData = MutableLiveData<ArrayList<Transaction>>()
        val cardTransactionsLiveData = MutableLiveData<ArrayList<CroCardTransaction>>()

        var currentWallet = MutableLiveData<Map<String, String?>>()
        val assetMaps = MutableLiveData<ArrayList<AssetData>>()


        var priceProvider: AssetValue = AssetValue.getInstance()
        var user = FirebaseAuth.getInstance().currentUser
        private var appModel: AppModel? = null


        val path: String
            get() = applicationContext.filesDir.absolutePath
        val savePath: String
            get() = "$path/save/"
        val logFilePath: String
            get() = "$path/log/core.log"

        init {
            dataLiveData.value = ArrayList()
            firebaseDataLiveData.value = ArrayList()
            assetMaps.value = ArrayList()
            //mkdir for save
            try {
                System.loadLibrary("cdcsvparser")
                isCoreInitialized = true
                isInitialized = true
                FileLog.d(TAG, "Core library loaded.")
            } catch (e: UnsatisfiedLinkError) {
                FileLog.e(TAG, "Failed to load native library: ${e.message}")
            }
        }

        private fun makeSaveDirIfNeeded() {
            val saveDir = File("/save")
            if (!saveDir.exists()) {
                saveDir.mkdir()
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
                FileLog.i("FirebaseUtil", "Saving data to Firebase")
                applicationContext.startService(
                    Intent(
                        applicationContext,
                        CoreService::class.java
                    ).apply {
                        action = ACTION_SAVE_DATA_TO_FIREBASE
                    })
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

        private fun saveToFireBase() {
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            val appSettings = AppSettings(
                uid,
                PreferenceHelper.getSelectedType(applicationContext),
                PreferenceHelper.getUseStrictType(applicationContext)
            )
            if (hasCardTx) appSettings.hasCardTx = "true"
            if (hasCryptoTx) appSettings.hasCryptoTx = "true"

            val appHashMap: java.util.HashMap<String, Any>
            val dbWallets = java.util.ArrayList<DBWallet>()
            val dbOutsideWallets = java.util.ArrayList<DBWallet>()
            val dbCardWallets = java.util.ArrayList<CCDBWallet>()
            val dbTransactions = java.util.ArrayList<DBTransaction>()
            val cardTransactions = java.util.ArrayList<CCDBTransaction>()
            walletsLiveData.value?.forEach {
                dbWallets.add(DBWallet(it))
            }
            outsideWalletsLiveData.value?.forEach {
                dbOutsideWallets.add(DBWallet(it))
            }
            cardWalletsLiveData.value?.forEach {
                dbCardWallets.add(CCDBWallet(it))
            }
            transactionsLiveData.value?.forEach {
                dbTransactions.add(DBTransaction(it))
            }
            cardTransactionsLiveData.value?.forEach {
                cardTransactions.add(CCDBTransaction(it))
            }

            appHashMap = hashMapOf<String, Any>(
                "wallets" to dbWallets,
                "outsideWallets" to dbOutsideWallets,
                "cardWallets" to dbCardWallets,
                "transactions" to dbTransactions,
                "cardTransactions" to cardTransactions,
                "appSettings" to appSettings.toHashMap()
            )

            val db = Firebase.firestore

            db.collection("user").document(uid).set(appHashMap)
                .addOnCompleteListener { task ->
                    handleFirebaseTaskResult(task, "Data saved successfully", "Error saving data")
                }
        }


        private fun handleFirebaseTaskResult(
            task: Task<Void>,
            successMessage: String,
            errorMessage: String
        ) {
            if (task.isSuccessful) {
                Toast.makeText(applicationContext, successMessage, Toast.LENGTH_SHORT).show()
                FileLog.i("FirebaseUtil", successMessage)
            } else {
                Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                FileLog.e("FirebaseUtil", "Error: ${task.exception}")
            }
        }


        const val ACTION_START_SERVICE = "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "at.msd.friehs_bicha.cdcsvparser.core.action.STOP_SERVICE"
        const val ACTION_RESTART_SERVICE =
            "at.msd.friehs_bicha.cdcsvparser.core.action.RESTART_SERVICE"
        const val ACTION_START_SERVICE_WITH_DATA =
            "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE_WITH_DATA"
        const val ACTION_START_SERVICE_WITH_FIREBASE_DATA =
            "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE_WITH_FIREBASE_DATA"
        const val ACTION_SAVE_DATA_TO_FIREBASE =
            "at.msd.friehs_bicha.cdcsvparser.core.action.SAVE_DATA_TO_FIREBASE"

    }
}