package at.msd.friehs_bicha.cdcsvparser.Core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.CardTxApp
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

class CoreService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            FileLog.w(TAG, "Initialization failed. Intent is null.")
            return super.onStartCommand(null, flags, startId)
        }

        if (AppModelManager.isInitialized()) {
            appModel = AppModelManager.getInstance()
        }
        Thread {
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
        }.start()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleStartServiceWithFirebaseData(intent: Intent) {
        //Not developed more atm bc of strange bug in FB
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
                val dataArray = arrayOf("TEST", "TEST")//Array<String>(data.size) { i -> data[i] }
                if (initWithData(dataArray, data.size, mode)) {
                    FileLog.d(TAG, "Initialization with data successful.")
                    isRunning = true
                } else {
                    FileLog.e(TAG, "Initialization with data failed.")
                    return
                }
            }

            false -> {
                if (appModel != null) {
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

    private fun provideDataToActivity() {

        when (isCoreInitialized) {
            true -> {
                TODO("call getCurrencies from core and return prices to it and then get the data from it")
            }

            false -> {
                Thread {
                    try {
                        while (!isRunning) {
                            Thread.sleep(500)
                            FileLog.d(TAG, "Waiting for initialization to finish.")
                        }
                        FileLog.d(TAG, "isRunning")
                        walletsLiveData.postValue(appModel?.txApp?.wallets)
                        outsideWalletsLiveData.postValue(appModel?.txApp?.outsideWallets)
                        cardWalletsLiveData.postValue(appModel?.cardApp?.wallets)
                        if (appModel?.txApp != null && appModel?.txApp is CardTxApp) {
                            cardWalletsLiveData.postValue(appModel?.txApp?.wallets)
                        }

                        val allWallets = ArrayList<Wallet>()
                        allWallets.addAll(appModel?.txApp?.wallets ?: ArrayList())
                        allWallets.addAll(appModel?.cardApp?.wallets ?: ArrayList())

                        allWalletsLiveData.postValue(allWallets.clone() as ArrayList<Wallet>)

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
                            return@Thread
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
                        transactions.postValue(transactions_ as ArrayList<Transaction>)
                    } catch (e: InterruptedException) {
                        FileLog.e(TAG, " : $e")
                        throw RuntimeException(e)
                    }
                }.start()
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
        val transactions = MutableLiveData<ArrayList<Transaction>>()

        var currentWallet = MutableLiveData<Map<String, String?>>()


        var priceProvider: AssetValue = AssetValue.getInstance()
        private var appModel: AppModel? = null


        init {
            dataLiveData.value = ArrayList()
            firebaseDataLiveData.value = ArrayList()
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
        fun getValueOfAssets(w: Wallet?): Double {
            if (isCoreInitialized) {
                TODO("call getCurrencies from core and return prices to it and then get the data from it")
                return 0.0
            }
            return try {
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


        fun getWalletAdapter(wallet: Wallet): Map<String, String?> {
            return when (isCoreInitialized) {
                true -> {
                    TODO("ask Core for the data and return it")
                    mapOf()
                }

                false -> {
                    appModel!!.getWalletAdapter(wallet)
                }
            }
        }

        fun getAssetMap(specificWallet: Wallet): Map<String, String?> {
            return when (isCoreInitialized) {
                true -> {
                    TODO("ask Core for the data and return it")
                    mapOf()
                }

                false -> {
                    appModel!!.getAssetMap(specificWallet)
                }
            }
        }

        fun getTransactionAdapter(transaction: Transaction): Map<String, String?> {
            return when (isCoreInitialized) {
                true -> {
                    TODO("ask Core for the data and return it")
                    mapOf()
                }

                false -> {
                    appModel!!.getTransactionAdapter(transaction)
                }
            }
        }

        fun getTransaction(transactionId: Int): Transaction {
            return when (isCoreInitialized) {
                true -> {
                    TODO("ask Core for the data and return it")
                }

                false -> {
                    transactions.value!!.find { it.transactionId == transactionId }!!
                }
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