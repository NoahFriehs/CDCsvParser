package at.msd.friehs_bicha.cdcsvparser.general

import at.msd.friehs_bicha.cdcsvparser.App

import android.content.Intent
import android.graphics.Color
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.*
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.ParseData
import at.msd.friehs_bicha.cdcsvparser.core.WalletAssets
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.*
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper.formatAmountToString
import at.msd.friehs_bicha.cdcsvparser.wallet.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.Serializable
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * The parser control for the Parser
 *
 * Arm convention for the appType when() in the summary getters below:
 * the card family (CroCard/CurveCard) gets the card arm, and everything
 * else - the whole C++-core family (Default, Custom, Kraken, BlockPit; all
 * StandardTxApp-style data) - gets the CDC/standard arm via else. A new
 * core type therefore works out of the box instead of hitting a missing
 * arm that throws (2026-10-16 device incident: a saved BlockPit model
 * threw in every one of these getters).
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
        val app = TxAppFactory.createTxApp(
            appType,
            AppStatus.NotStarted,
            useStrictType,
            hashMapOf(DataTypes.csvAsList to file)
        )
        txApp = app
        this.appType = app.appType

        if (PreferenceHelper.getIsDataLocal(App.instance)) saveAppModelLocal()
        else PreferenceHelper.setIsAppModelSavedLocal(App.instance, false)
        isRunning = true
        if (appType == AppType.CroCard) {
            cardApp = app as CardTxApp
        }
        // Partial parse failures must not crash the app: continue with the
        // parsed subset (the C++ core path behaves the same way and
        // surfaces the count via log/UI).
        if (app.amountTxFailed > 0) {
            FileLog.w(
                "AppModel",
                "txApp: ${app.amountTxFailed} line(s) failed to parse, AppType: $appType - continuing with the parsed subset"
            )
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
        if (PreferenceHelper.getIsDataLocal(App.instance)) saveAppModelLocal()
        isRunning = true
    }


    constructor() : super(PreferenceHelper.getSelectedType(App.instance)) {
        if (PreferenceHelper.getIsDataLocal(App.instance) && PreferenceHelper.getIsAppModelSavedLocal(App.instance)) {
            // The Room load runs on a background scope; until it finished
            // (isFullyLoaded) the model is half-empty and the fast start must
            // not post it (plan 003).
            loadAppModelLocal()
        }
        else PreferenceHelper.setIsAppModelSavedLocal(App.instance,false)
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
                AppType.CroCard, AppType.CurveCard -> {
                    val app = txApp ?: cardApp
                    val wallets = app!!.wallets.clone() as ArrayList<Wallet>
                    for (wallet in wallets) {
                        totalPrice = totalPrice.add(wallet.amount)
                    }
                }

                else -> for (wallet in txApp?.wallets ?: emptyList()) {
                    if (wallet.currencyType == "EUR") continue
                    totalPrice = totalPrice.add(wallet.moneySpent)
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
    /**
     * Typed per-wallet display state (replacement of the old
     * `getAssetMap` `R.id`-keyed map). Non-null [wallet] by design - the
     * old `wallet!!` assert disappeared with the nullable parameter.
     * Card wallets carry only "money spent" (the other three rows are
     * hidden via null); everything else is standard layout with the
     * no-prices token while `AssetValue` is not running.
     */
    fun walletAssets(wallet: Wallet): WalletAssets {
        val total = formatAmountToString(wallet.moneySpent.toDouble())
        if (wallet is CroCardWallet) return WalletAssets.card(total)

        return if (appType == AppType.CroCard || appType == AppType.CurveCard) {
            WalletAssets.card(total)
        } else {
            val amountOfAsset = getValueOfAssets(wallet)
            val rewardValue = getTotalBonus(wallet)
            WalletAssets.standard(
                moneySpentText = total,
                assetsText = formatAmountToString(amountOfAsset),
                rewardsText = formatAmountToString(rewardValue),
                profitLossText = formatAmountToString(
                    amountOfAsset - wallet.moneySpent.toDouble()
                ),
                pricesRunning = AssetValue.getInstance().isRunning,
            )
        }
    }


    /**
     * Typed state with the data to display on the parse screen (the X2
     * replacement of the old `parseMap` `R.id`-keyed map). `null` = the
     * cards are not ready (the old contract's whole-map-null: the cpp-core
     * caller retries for a bounded time).
     */
    @get:Throws(InterruptedException::class)
    val parseData: ParseData?
        get() {
            return try {
                val total = totalPrice
                val totalMoneySpent = formatAmountToString(total.toDouble())
                if (appType != AppType.CdCsvParser && txApp != null && cardApp != null) {
                    appType = txApp!!.appType
                }
                when (appType) {
                    AppType.CroCard, AppType.CurveCard ->
                        ParseData.card(totalMoneySpent)

                    else -> if (AssetValue.getInstance().isRunning) {
                        val amountOfAsset = valueOfAssets
                        val rewardValue = totalBonus
                        ParseData.standard(
                            moneySpentText = totalMoneySpent,
                            assetsText = formatAmountToString(amountOfAsset),
                            rewardsText = formatAmountToString(rewardValue),
                            profitLossText = formatAmountToString(
                                amountOfAsset - total.toDouble()
                            ),
                        )
                    } else {
                        ParseData.standard(
                            moneySpentText = totalMoneySpent,
                            assetsText = WalletAssets.NO_INTERNET,
                            rewardsText = WalletAssets.NO_INTERNET,
                            profitLossText = WalletAssets.NO_INTERNET,
                        )
                    }
                }
            } catch (e: Exception) {
                e.message?.let { FileLog.w("ParseMap", it) }
                null
            }
        }


    private fun saveAppModelLocal()
    {
        appModelScope.launch {
            val walletDao = App.instance.db.walletDao()
            val txDao = App.instance.db.transactionDao()
            val cardWalletDao = App.instance.db.cardWalletDao()
            val cardTransactionDao = App.instance.db.cardTransactionDao()

            if (txApp is StandardTxApp) {
                walletDao.deleteAll()
                txDao.deleteAll()
                walletDao.insertAll(txApp!!.wallets)
                walletDao.insertAll(txApp!!.outsideWallets)
                txDao.insertAll(txApp!!.transactions)
            }
            PreferenceHelper.setIsAppModelSavedLocal(App.instance, true)

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
        isFullyLoaded = false
        appModelScope.launch {
            try {
            val useStrictType = PreferenceHelper.getUseStrictType(App.instance)
            val ws = App.instance.db.walletDao().getAllWallets()
            val txs = App.instance.db.transactionDao().getAllTransactions()
            val cws = App.instance.db.cardWalletDao().getAllWallets()
            val cts = App.instance.db.cardTransactionDao().getAllTransactions()

            if (ws.isEmpty() && txs.isEmpty() && cws.isEmpty() && cts.isEmpty()) {
                FileLog.e("AppModel", "loadAppModelLocal: no data found in db")
                return@launch
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
                return@launch
            }

            if (txApp == null) {
                txApp = cardApp
            }

            isRunning = true
            } finally {
                // Every exit path (including "no data found in db") marks the
                // model final so the fast-start wait can resolve.
                isFullyLoaded = true
            }
        }
    }


    fun toHashMap(overrideAppType: AppType = appType): HashMap<String, Any> {
        val appHashMap: HashMap<String, Any>
        when (overrideAppType) {
            AppType.CroCard, AppType.CurveCard -> {
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





    

    fun hasCard(): Boolean {
        return cardApp != null || appType == AppType.CroCard || appType == AppType.CurveCard
    }

    fun hasTxModule(): Boolean {
        return txApp != null && txApp!!.appType == AppType.CdCsvParser
    }

    companion object {
        // Bounded IO scope for the fire-and-forget Room writes below
        // (replaces the old GlobalScope usage).
        private val appModelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Returns the amount the asset is worth in EUR
         *
         * @return the amount the asset is worth in EUR
         */
        fun getValueOfAssets(w: Wallet?): Double {
            if (w is CroCardWallet) {
                return w.amount.toDouble()
            }
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

        
    }

}