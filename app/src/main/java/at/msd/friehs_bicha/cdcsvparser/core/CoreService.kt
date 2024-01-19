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

    /**
     * Saves the data to Firebase
     */
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

    /**
     * Loads the data from Firebase
     */
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

    /**
     * Loads the data from Firebase
     */
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
                    appModel = AppModel(data, AppType.fromOrdinal(mode), false)
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

    /**
     * Checks which modes are available and sets the variables accordingly (cpp)
     */
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

    /**
     * Provides the data to the activity
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun provideDataToActivity() {

        when (isCoreInitialized && useCpp) {
            true -> {
                provideDataToActivityFromCppCore()
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
                        saveToRoomsDB()
                    } catch (e: InterruptedException) {
                        FileLog.e(TAG, " : $e")
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun provideDataToActivityFromCppCore() {
        calculateWalletBalances()

        val currencies = getCurrencies()
        val prices = Array<Double>(currencies.size) { _ -> 0.0 }
        currencies.forEach {
            prices[currencies.indexOf(it)] = priceProvider.getPrice(it)
        }
        setPrice(prices)

        // get Data from Core and set it to the LiveData
        val map: MutableMap<String, String?> = java.util.HashMap()
        var totalMoneySpent = getTotalMoneySpent()
        val totalMoneySpentString =
            StringHelper.formatAmountToString(totalMoneySpent)

        if (hasCryptoTx) {

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
                AssetValue.getInstance().check()
                map[R.id.assets_valueP.toString()] = "no internet connection"
                map[R.id.rewards_value.toString()] = "no internet connection"
                map[R.id.profit_loss_value.toString()] = "no internet connection"
                map[R.id.money_spent_value.toString()] = totalMoneySpentString
                //start thread to check if internet is back
                GlobalScope.launch {
                    while (!AssetValue.getInstance().isRunning) {
                        FileLog.d(TAG, "Waiting for internet connection.")
                        delay(5000)
                        AssetValue.getInstance().check()
                    }
                    provideDataToActivityFromCppCore()
                }
            }
        } else {
            totalMoneySpent = getTotalMoneySpentCard()
            map[R.id.money_spent_value.toString()] =
                StringHelper.formatAmountToString(totalMoneySpent)
            map[R.id.assets_valueP.toString()] = null
            map[R.id.rewards_value.toString()] = null
            map[R.id.profit_loss_value.toString()] = null
            map[R.id.assets_value_label.toString()] = null
            map[R.id.rewards_label.toString()] = null
            map[R.id.profit_loss_label.toString()] = null
            map[R.id.coinGeckoApiLabel.toString()] = null
        }
        parsedDataLiveData.postValue(map)

        //get transactions from core and set it to the LiveData
        val transactions = getTransactionsAsString()
        val cardTransactions = getCardTransactionsAsStrings()
        val transactions_ = ArrayList<Transaction>()
        val cardTransactions_ = ArrayList<CroCardTransaction>()
        transactions.forEach {
            val txData =
                TransactionData()
            val dataString = it.replace("\n\t", "")
            try {
                txData.fromXml(dataString)
                transactions_.add(Transaction(txData))
            } catch (e: Exception) {
                FileLog.e(TAG, "Exception: $e")
            }
        }
        transactionsLiveData.postValue(transactions_)
        cardTransactions.forEach {
            val txData =
                TransactionData()
            val dataString = it.replace("\n\t", "")
            try {
                txData.fromXml(dataString)
                cardTransactions_.add(CroCardTransaction(txData))
            } catch (e: Exception) {
                FileLog.e(TAG, "Exception: $e")
            }
        }
        cardTransactionsLiveData.postValue(cardTransactions_)


        //get Wallets from core and set it to the LiveData
        val wallets = getWalletsAsString()
        val cardWallets = getCardWalletsAsStrings()
        val wallets_ = ArrayList<Wallet>()
        val cardWallets_ = ArrayList<Wallet>()
        wallets.forEach {
            val data = WalletXmlSerializer().deserializeFromXml(it)
            wallets_.add(Wallet(data))
        }
        transactions_.forEach { tx ->
            wallets_.find { it.walletId == tx.walletId }?.transactions?.add(tx)
        }
        cardWallets.forEach {
            val data = WalletXmlSerializer().deserializeFromXml(it)
            cardWallets_.add(CroCardWallet(data))
        }
        cardTransactions_.forEach { tx ->
            cardWallets_.find { it.walletId == tx.walletId }?.transactions?.add(tx)
        }

        walletsLiveData.postValue(wallets_)
        outsideWalletsLiveData.postValue(wallets_.filter { it.isOutsideWallet } as ArrayList<Wallet>)
        cardWalletsLiveData.postValue(cardWallets_)
        allWalletsLiveData.postValue(wallets_)
        allWalletsLiveData.postValue(cardWallets_)

        val walletNames_ = Array<String?>(wallets_.size + cardWallets_.size) { _ -> null }
        val indexAll = wallets_.size
        wallets_.forEach {
            wallets_.indexOf(it).let { index ->
                walletNames_[index] = it.getTypeString()
            }
        }
        cardWallets_.forEach {
            cardWallets_.indexOf(it).let { index ->
                walletNames_[index + indexAll] = it.getTypeString()
            }
        }
        walletNames.postValue(walletNames_)
        wallets_.forEach {
            assetMaps.value!!.add(AssetData(it.walletId, getAssetMap(it.walletId)))
        }
        cardWallets_.forEach {
            assetMaps.value!!.add(AssetData(it.walletId, getCardAssetMap(it.walletId)))
        }

        saveToRoomsDB()
    }

    private fun getCardAssetMap(walletId: Int): Map<String, String?> {
        return when (isCoreInitialized && useCpp) {
            true -> {

                val map = mutableMapOf<String, String?>()
                val moneySpent = getMoneySpentByWID(walletId)
                val total = StringHelper.formatAmountToString(moneySpent)
                map[R.id.money_spent_value.toString()] = total
                map[R.id.assets_value.toString()] = null
                map[R.id.rewards_value.toString()] = null
                map[R.id.profit_loss_value.toString()] = null
                map[R.id.assets_value_label.toString()] = null
                map[R.id.rewards_label.toString()] = null
                map[R.id.profit_loss_label.toString()] = null
                map
            }

            false -> {
                val specificWallet = appModel!!.txApp!!.wallets.find { it.walletId == walletId }
                appModel!!.getAssetMap(specificWallet)
            }
        }
    }

    private fun saveToRoomsDB() {
        val walletDao = InstanceVars.db.walletDao()
        val txDao = InstanceVars.db.transactionDao()
        val cardWalletDao = InstanceVars.db.cardWalletDao()
        val cardTransactionDao = InstanceVars.db.cardTransactionDao()

        walletsLiveData.value?.let {
            walletDao.insertAll(it)
        }
        transactionsLiveData.value?.let {
            txDao.insertAll(it)
        }
        cardWalletsLiveData.value?.let {
            cardWalletDao.insertAll(it as ArrayList<CroCardWallet>)
        }
        cardTransactionsLiveData.value?.let {
            cardTransactionDao.insertAll(it)
        }
    }

    /**
     * Returns the amounts of the asset in the wallet
     *
     * @return the amount of the asset in the wallet
     */
    private fun getAssetMap(walletId: Int): Map<String, String?> {
        return when (isCoreInitialized && useCpp) {
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

    /**
     * Initializes the Core
     */
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

    /**
     * Loads the data from Firebase
     */
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

    /**
     * Loads the data from the HashMap
     */
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
            if (dbWallets.isNullOrEmpty() && dbOutsideWallets.isNullOrEmpty() && dbTransactions.isNullOrEmpty()) {
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
                when (isCoreInitialized && useCpp) {
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
                when (isCoreInitialized && useCpp) {
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

    /**
     * getTotalValueOfAssetsCard
     */
    private external fun getTotalMoneySpentCard(): Double

    private external fun getTransactionsAsString(): Array<String>
    private external fun getWalletsAsString(): Array<String>

    private external fun getCardTransactionsAsStrings(): Array<String>
    private external fun getCardWalletsAsStrings(): Array<String>

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
            get() = ""//"$path/save/"
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

        /**
         * Starts the CoreService
         */
        fun startService() {
            val intent = Intent(applicationContext, CoreService::class.java)
            applicationContext.startService(intent)
        }

        /**
         * Starts the CoreService with the data
         */
        fun startServiceWithData(data: ArrayList<String>, mode: Int) {
            val intent = Intent(applicationContext, CoreService::class.java)
            intent.action = ACTION_START_SERVICE_WITH_DATA
            intent.putExtra("data", data)
            intent.putExtra("mode", mode)
            applicationContext.startService(intent)
        }


        /**
         * Returns the amount the asset is worth in EUR
         *
         * @return the amount the asset is worth in EUR
         */
        fun getValueOfAssetsFromWID(walletId: Int): Double {
            return if (isCoreInitialized && useCpp) {
                if (walletsLiveData.value == null || walletsLiveData.value!!.isEmpty() || walletsLiveData.value!!.find { it.walletId == walletId } == null) {
                    FileLog.w(
                        "$TAG.getValueOfAssetsFromWID",
                        "walletsLiveData is null or empty"
                    )   //normal when only card tx
                    return 1.0
                }
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


        /**
         * Returns the amounts of the asset in the wallet
         *
         * @return the amounts of the asset in the wallet
         */
        fun getWalletAdapter(walletId: Int): Map<String, String?> {
            return when (isCoreInitialized && useCpp) {
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

        /**
         * Returns the AssetMap of the wallet
         */
        fun getAssetMap(walletId: Int): Map<String, String?> {
            return assetMaps.value!!.find { it.walletId == walletId }!!.data
        }


        /**
         * Returns the stats of transaction
         *
         * @return the stats of transaction
         */
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

        /**
         * Returns the transaction with the transactionId
         *
         * @return the transaction with the transactionId
         */
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


        /**
         * Tell service to save the data to Firebase
         */
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
                return
            }
            appModel?.let {
                user = FirebaseAuth.getInstance().currentUser   //refresh user
                if (user != null) Thread {
                    FirebaseUtil(applicationContext).saveDataToFirebase(
                        it
                    )
                }.start()
            }

        }

        /**
         * Saves the data to Firebase
         */
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


        /**
         * Handles the result of the Firebase task
         */
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