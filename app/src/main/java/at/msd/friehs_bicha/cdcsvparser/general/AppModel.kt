package at.msd.friehs_bicha.cdcsvparser.general

import android.content.Intent
import android.graphics.Color
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.SettingsActivity.Companion.useStrictType
import at.msd.friehs_bicha.cdcsvparser.app.*
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars.applicationContext
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.*
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletAdapter
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper.formatAmountToString
import at.msd.friehs_bicha.cdcsvparser.wallet.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.Serializable
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * The parser control for the Parser
 */
class AppModel : BaseAppModel, Serializable {

    var cardApp: CardTxApp? = null


    /**
     * Creates a new AppModel
     *
     * @param file          the file to parse
     * @param appType       which app to use
     * @param useStrictType
     */
    constructor(
        file: ArrayList<String>,
        appType: AppType,
        useStrictType: Boolean
    ) : super(appType) {
        val copy = file.clone() as ArrayList<String>    //TODO: remove only for testing -> DEV_ZONE
        txApp = TxAppFactory.createTxApp(
            appType,
            AppStatus.NotStarted,
            useStrictType,
            hashMapOf(DataTypes.csvAsList to file)
        )
        this.appType = txApp!!.appType

        // DEV_ZONE
        if (!CoreService.isRunning && false) {
            applicationContext.startService(
                Intent(
                    applicationContext,
                    CoreService::class.java
                ).apply {
                    action = CoreService.ACTION_START_SERVICE_WITH_DATA
                    putExtra("data", copy)
                    putExtra("mode", 0)
                })
        }


        // DEV_ZONE


        if (PreferenceHelper.getIsDataLocal(applicationContext)) saveAppModelLocal()
        else PreferenceHelper.setIsAppModelSavedLocal(applicationContext, false)
        isRunning = true
        if (appType == AppType.CroCard) {
            cardApp = txApp as CardTxApp
        }
        if (txApp!!.amountTxFailed > 0) {
            FileLog.e("AppModel", "txApp: amountTxFailed, AppType: $appType")
            throw RuntimeException("$txApp.amountTxFailed transaction(s) failed")
        }
    }

    /**
     * Creates a new AppModel
     *
     * @param dbWallets         the wallets from the database
     * @param dbOutsideWallets  the outside wallets from the database
     * @param dbTransactions    the transactions from the database
     * @param appType           which app to use
     * @param amountTxFailed    the amount of failed transactions
     * @param useStrictType
     */
    constructor(
        dbWallets: ArrayList<HashMap<String, *>>?,
        dbOutsideWallets: ArrayList<HashMap<String, *>>?,
        dbTransactions: ArrayList<HashMap<String, *>>?,
        appType: AppType,
        amountTxFailed: Long,
        useStrictType: Boolean
    ) : super(appType) {
        txApp = TxAppFactory.createTxApp(
            appType,
            AppStatus.importFromFB,
            useStrictType,
            hashMapOf(
                DataTypes.dbWallets to dbWallets,
                DataTypes.dbOutsideWallets to dbOutsideWallets,
                DataTypes.dbTransactions to dbTransactions,
                DataTypes.amountTxFailed to amountTxFailed
            )
        )
        if (PreferenceHelper.getIsDataLocal(applicationContext)) saveAppModelLocal()
        isRunning = true
    }


    constructor() : super(PreferenceHelper.getSelectedType(applicationContext)) {
        if (PreferenceHelper.getIsDataLocal(applicationContext) && PreferenceHelper.getIsAppModelSavedLocal(applicationContext)) loadAppModelLocal()
        else PreferenceHelper.setIsAppModelSavedLocal(applicationContext,false)
        isRunning = true
    }


    /**
     * Returns the total amount spent
     *
     * @return the total amount spent
     */
    private val totalPrice: BigDecimal
        get() {
            var totalPrice = BigDecimal(0)
            when (appType) {
                AppType.CdCsvParser -> for (wallet in txApp!!.wallets) {
                    if (wallet.currencyType == "EUR") continue
                    totalPrice = totalPrice.add(wallet.moneySpent)
                }

                AppType.CroCard -> {
                    val app = txApp ?: cardApp
                    val wallets = app!!.wallets.clone() as ArrayList<Wallet>
                    for (wallet in wallets) {
                        totalPrice = totalPrice.add(wallet.amount)
                    }
                }

                else  ->{
                    FileLog.e("AppModel", "CroCard: Usage not found, AppType: $appType")
                    throw RuntimeException("Usage not found")
                }
            }
            return totalPrice
        }

    /**
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    private val totalBonus: Double
        get() = try {
            var valueOfAll = 0.0
            for (wallet in txApp!!.wallets) {
                if (wallet.currencyType == "EUR") continue
                val price = wallet.currencyType.let { AssetValue.getInstance().getPrice(it) }
                val amount = wallet.amountBonus
                valueOfAll += price * amount.toDouble()
            }
            valueOfAll
        } catch (e: Exception) {
            FileLog.e("AppModel.totalBonus", "Exception: $e")
            0.0
        }

    /**
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    private fun getTotalBonus(wallet: Wallet?): Double {
        return try {
            var valueOfAll = 0.0
            val price = wallet!!.currencyType.let { AssetValue.getInstance().getPrice(it) }
            val amount = wallet.amountBonus
            valueOfAll += price * amount.toDouble()
            valueOfAll
        } catch (e: Exception) {
            FileLog.e("AppModel.getTotalBonus", "Exception: $e")
            0.0
        }
    }

    /**
     * Returns the total amount the assets are worth in EUR
     *
     * @return the total amount the assets are worth in EUR
     */
    private val valueOfAssets: Double
        get() = try {
            var valueOfAll = 0.0
            for (w in txApp!!.wallets) {
                if (w.currencyType == "EUR") continue
                val price = w.currencyType.let { AssetValue.getInstance().getPrice(it) }
                val amount = w.amount
                valueOfAll += price * amount.toDouble()
                if (valueOfAll < 0.0) {
                    FileLog.i("AppModel.valueOfAssets", "valueOfAll < 0: $valueOfAll")
                }
            }
            valueOfAll
        } catch (e: Exception) {
            FileLog.e("AppModel.valueOfAssets", "Exception: $e")
            0.0
        }

    fun loadPriceCaches(): Boolean {
        if (txApp == null) return true
        val walletSyms = txApp!!.wallets.map { it.currencyType }
        return AssetValue.getInstance().loadCache(walletSyms)
    }

    fun reloadPriceCache(): Boolean {
        if (txApp == null) return true
        return AssetValue.getInstance().reloadCache()
    }


    /**
     * Returns a map with the data to display from a wallet
     *
     * @return a map with the data to display from a wallet
     */
    fun getAssetMap(wallet: Wallet?): Map<String, String?> {
        val total = formatAmountToString(wallet!!.moneySpent.toDouble())
        val map: MutableMap<String, String?> = HashMap()

        if (wallet is CroCardWallet) {
            map[R.id.money_spent_value.toString()] = total
            map[R.id.assets_value.toString()] = null
            map[R.id.rewards_value.toString()] = null
            map[R.id.profit_loss_value.toString()] = null
            map[R.id.assets_value_label.toString()] = null
            map[R.id.rewards_label.toString()] = null
            map[R.id.profit_loss_label.toString()] = null
            return map
        }

        when (appType) {
            AppType.CdCsvParser -> {
                val amountOfAsset = getValueOfAssets(wallet)
                val rewardValue = getTotalBonus(wallet)
                if (AssetValue.getInstance().isRunning) {
                    map[R.id.assets_value.toString()] =
                        formatAmountToString(amountOfAsset)
                    map[R.id.rewards_value.toString()] =
                        formatAmountToString(rewardValue)
                    map[R.id.profit_loss_value.toString()] =
                        formatAmountToString(amountOfAsset - wallet.moneySpent.toDouble())
                    map[R.id.money_spent_value.toString()] = total
                } else {
                    map[R.id.assets_value.toString()] = "no internet connection"
                    map[R.id.rewards_value.toString()] = "no internet connection"
                    map[R.id.profit_loss_value.toString()] = "no internet connection"
                    map[R.id.money_spent_value.toString()] = total
                }
            }

            AppType.CroCard -> {
                map[R.id.money_spent_value.toString()] = total
                map[R.id.assets_value.toString()] = null
                map[R.id.rewards_value.toString()] = null
                map[R.id.profit_loss_value.toString()] = null
                map[R.id.assets_value_label.toString()] = null
                map[R.id.rewards_label.toString()] = null
                map[R.id.profit_loss_label.toString()] = null
            }

            else -> throw RuntimeException("Usage not found")
        }
        return map
    }


    /**
     * Returns a map with the data to display for the app
     *
     * @return a map with the data to display for the app
     */
    @get:Throws(InterruptedException::class)
    val parseMap: Map<String, String?>?
        get() {
            return try {
                val total = totalPrice
                val totalMoneySpent = formatAmountToString(total.toDouble())
                val map: MutableMap<String, String?> = HashMap()
                if (appType != AppType.CdCsvParser && txApp != null && cardApp != null)
                {
                    appType = txApp!!.appType
                }
                when (appType) {
                    AppType.CdCsvParser -> if (AssetValue.getInstance().isRunning) {
                        val amountOfAsset = valueOfAssets
                        val rewardValue = totalBonus
                        map[R.id.assets_valueP.toString()] =
                            formatAmountToString(amountOfAsset)
                        map[R.id.rewards_value.toString()] =
                            formatAmountToString(rewardValue)
                        map[R.id.profit_loss_value.toString()] =
                            formatAmountToString(amountOfAsset - total.toDouble())
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                    } else {
                        map[R.id.assets_valueP.toString()] = "no internet connection"
                        map[R.id.rewards_value.toString()] = "no internet connection"
                        map[R.id.profit_loss_value.toString()] = "no internet connection"
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                    }

                    AppType.CroCard -> {
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                        map[R.id.assets_valueP.toString()] = null
                        map[R.id.rewards_value.toString()] = null
                        map[R.id.profit_loss_value.toString()] = null
                        map[R.id.assets_value_label.toString()] = null
                        map[R.id.rewards_label.toString()] = null
                        map[R.id.profit_loss_label.toString()] = null
                        map[R.id.coinGeckoApiLabel.toString()] = null
                    }

                    else ->{
                        FileLog.e("AppModel", "ParseMap: AppType not found, AppType: $appType" )
                        throw RuntimeException("Usage not found")
                    }
                }
                map
            } catch (e: Exception) {
                e.message?.let { FileLog.w("ParseMap", it) }
                null
            }
        }


    @OptIn(DelicateCoroutinesApi::class)
    private fun saveAppModelLocal()
    {
        GlobalScope.launch {
            val walletDao = InstanceVars.db.walletDao()
            val txDao = InstanceVars.db.transactionDao()
            val cardWalletDao = InstanceVars.db.cardWalletDao()
            val cardTransactionDao = InstanceVars.db.cardTransactionDao()

            if (txApp is StandardTxApp) {
                walletDao.deleteAll()
                txDao.deleteAll()
                walletDao.insertAll(txApp!!.wallets)
                walletDao.insertAll(txApp!!.outsideWallets)
                txDao.insertAll(txApp!!.transactions)
            }
            PreferenceHelper.setIsAppModelSavedLocal(applicationContext, true)

            if (cardApp == null) return@launch
            cardWalletDao.deleteAll()
            cardTransactionDao.deleteAll()

            val listOfWalletIDs = ArrayList<Int>()

            for (wallet in cardApp!!.wallets) {
                listOfWalletIDs.add(wallet.walletId)
            }
            for (transaction in cardApp!!.transactions) {
                if (!listOfWalletIDs.contains(transaction.walletId)) {
                    FileLog.e(
                        "AppModel",
                        "saveAppModelLocal: $transaction.walletId not found in wallets"
                    )
                    continue
                }
            }

            cardWalletDao.insertAll(cardApp!!.wallets as ArrayList<CroCardWallet>)
            cardTransactionDao.insertAll(cardApp!!.transactions as ArrayList<CroCardTransaction>)
        }
    }


    private fun loadAppModelLocal()
    {
        Thread{
            val ws = InstanceVars.db.walletDao().getAllWallets()
            val txs = InstanceVars.db.transactionDao().getAllTransactions()
            val cws = InstanceVars.db.cardWalletDao().getAllWallets()
            val cts = InstanceVars.db.cardTransactionDao().getAllTransactions()

            if (ws.isEmpty() && txs.isEmpty() && cws.isEmpty() && cts.isEmpty()) {
                FileLog.e("AppModel", "loadAppModelLocal: no data found in db")
                return@Thread
            }

            if (cws.isNotEmpty() && cts.isNotEmpty()) {
                val cardWallets = ArrayList<CroCardWallet>()
                cws.forEach {
                    it.wallet.transactions.addAll(it.transactions)
                    cardWallets.add(it.wallet)}

                cardApp = TxAppFactory.createTxApp(
                    AppType.CroCard,
                    AppStatus.Finished,
                    useStrictType,
                    hashMapOf(
                        DataTypes.dbWallets to cardWallets,
                        DataTypes.dbTransactions to cts,
                        DataTypes.amountTxFailed to 0L
                    )
                ) as CardTxApp
            }

            if (ws.isNotEmpty() && txs.isNotEmpty()) {

                val wallets = ArrayList<Wallet>()
                val outsideWallets = ArrayList<Wallet>()

                ws.forEach {
                    it.wallet.transactions = ArrayList()
                    (it.wallet.transactions as ArrayList<Transaction?>).addAll(it.transactions) //more difficult way: txs.filter { tx -> tx.walletId == it.wallet.walletId }
                    if (it.wallet.isOutsideWallet) {
                        outsideWallets.add(it.wallet)
                    } else {
                        wallets.add(it.wallet)
                    }

                }

                txApp = TxAppFactory.createTxApp(
                    AppType.CdCsvParser,
                    AppStatus.Finished,
                    useStrictType,
                    hashMapOf(
                        DataTypes.dbWallets to wallets,
                        DataTypes.dbOutsideWallets to outsideWallets,
                        DataTypes.dbTransactions to txs,
                        DataTypes.amountTxFailed to 0L
                    )
                )
            }

            if (txApp == null && cardApp == null) {
                FileLog.e("AppModel", "loadAppModelLocal: no data found in db")
                return@Thread
            }

            if (txApp == null) {
                txApp = cardApp
            }

            isRunning = true
        }.start()
    }


    fun toHashMap(overrideAppType: AppType = appType): HashMap<String, Any> {
        val appHashMap: HashMap<String, Any>
        when (overrideAppType) {
            AppType.CdCsvParser -> {
                val dbWallets = ArrayList<DBWallet>()
                val dboutsideWallets = ArrayList<DBWallet>()
                val dbTransactions = ArrayList<DBTransaction>()
                txApp!!.wallets.forEach {
                    dbWallets.add(DBWallet(it))
                }
                txApp!!.outsideWallets.forEach {
                    dboutsideWallets.add(DBWallet(it))
                }
                txApp!!.transactions.forEach {
                    dbTransactions.add(DBTransaction(it))
                }

                appHashMap = hashMapOf<String, Any>(
                    "wallets" to dbWallets,
                    "outsideWallets" to dboutsideWallets,
                    "transactions" to dbTransactions,
                    "amountTxFailed" to txApp!!.amountTxFailed,
                    "appType" to appType
                )
            }

            AppType.CroCard -> {
                val dbWallets = ArrayList<CCDBWallet>()
                val dbTransactions = ArrayList<CCDBTransaction>()
                txApp!!.wallets.forEach {
                    dbWallets.add(CCDBWallet(it))
                }
                txApp!!.transactions.forEach {
                    dbTransactions.add(CCDBTransaction(it))
                }

                appHashMap = hashMapOf<String, Any>(
                    "wallets" to dbWallets,
                    "transactions" to dbTransactions,
                    "amountTxFailed" to txApp!!.amountTxFailed,
                    "appType" to appType
                )
            }

            else -> {
                FileLog.e("AppModel", "toHashMap: Usage not found, AppType: $appType")
                throw RuntimeException("Usage not found")
            }
        }
                return appHashMap
    }


    fun getWalletByID(id: Int): Wallet? {
        txApp?.wallets?.forEach {
            if (it.walletId == id) {
                return it
            }
        }
        return null
    }




    fun getWalletAdapterWithCallback(
        wallet: Wallet,
        callback: IWalletAdapterCallback,
        holder: WalletAdapter.WalletViewHolder
    ) {
//        Thread {
//            val map = getWalletAdapter(wallet)
//            callback.onCallback(map, holder)
//        }.start()
    }

    fun getTransactionAdapter(transaction: Transaction): MutableMap<String, String?> {

        //TDOO: make this better for CardTransactions

        val defaultLocale = Locale.getDefault()
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", defaultLocale)
        val map: MutableMap<String, String?> = HashMap()

        if (transaction is CroCardTransaction) {
            map[R.id.tv_assetAmountValue.toString()] =
                formatAmountToString(
                    transaction.amount.toDouble(),
                    6,
                    transaction.transactionTypeString
                )
        } else {
            map[R.id.tv_assetAmountValue.toString()] =
                formatAmountToString(transaction.amount.toDouble(), 6, transaction.currencyType)
        }

        map[R.id.tv_transactionId.toString()] = transaction.transactionId.toString()
        map[R.id.tv_date.toString()] = transaction.date?.let { dateFormat.format(it).toString() }
        map[R.id.tv_descriptionValue.toString()] = transaction.description
        map[R.id.tv_amountValue.toString()] =
            formatAmountToString(transaction.nativeAmount.toDouble())

        return map
    }

    fun hasCard(): Boolean {
        return cardApp != null || appType == AppType.CroCard || appType == AppType.CurveCard
    }

    fun hasTxModule(): Boolean {
        return txApp != null && txApp!!.appType == AppType.CdCsvParser
    }

    companion object {
        /**
         * Returns the amount the asset is worth in EUR
         *
         * @return the amount the asset is worth in EUR
         */
        fun getValueOfAssets(w: Wallet?): Double {
            return try {
                val valueOfWallet: Double
                val price = w!!.currencyType.let { AssetValue.getInstance().getPrice(it) }
                val amount = w.amount
                valueOfWallet = price * amount.toDouble()
                valueOfWallet
            } catch (e: Exception) {
                FileLog.e("AppModel.getValueOfAssets", "Exception: $e")
                0.0
            }
        }

        fun getWalletAdapter(wallet: Wallet): Map<String, String?> {
            val assetValue = getValueOfAssets(wallet)
            var percentProfit = assetValue / wallet.moneySpent.toDouble() * 100
            if (percentProfit.isNaN()) {
                percentProfit = 0.0
            }
            val assetValueString = formatAmountToString(assetValue, 5)
            val amountString =
                formatAmountToString(wallet.amount.toDouble(), 5, wallet.currencyType)
            val color: Int = if (percentProfit > 100) {
                Color.GREEN
            } else if (percentProfit == 100.0 || percentProfit == 0.0) {
                Color.GRAY
            } else {
                Color.RED
            }

            val walletName: String = if (wallet is CroCardWallet) wallet.transactionType.toString()
            else wallet.currencyType

            val map: MutableMap<String, String?> = mutableMapOf()
            map[R.id.walletId.toString()] = wallet.walletId.toString()
            map[R.id.currencyType.toString()] = walletName
            map[R.id.amount.toString()] = amountString
            map[R.id.amountValue.toString()] = assetValueString
            map[R.id.percentProfit.toString()] =
                formatAmountToString(percentProfit - 100, 2, "%", true)
            map[R.id.amountTransactions.toString()] = wallet.transactions.count().toString()
            map["COLOR"] = color.toString()
            return map
        }
    }

}