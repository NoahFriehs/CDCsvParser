package at.msd.friehs_bicha.cdcsvparser.general

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.CroCardTxApp
import at.msd.friehs_bicha.cdcsvparser.app.TxApp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.*
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.*
import com.google.firebase.Timestamp
import java.io.Serializable
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * The parser control for the Parser
 */
class AppModel : BaseAppModel, Serializable {

    var asset: AssetValue = AssetValue()

    /**
     * Creates a new AppModel
     *
     * @param file          the file to parse
     * @param appType       which app to use
     * @param useStrictType
     */
    constructor(file: ArrayList<String>, appType: AppType?, useStrictType: Boolean) : super(appType) {
        var exception = ""
        try {
            when (appType) {
                AppType.CdCsvParser -> txApp = TxApp(file)
                AppType.CroCard -> txApp = CroCardTxApp(file, useStrictType)
                else -> throw RuntimeException("Usage not found")
            }
        } catch (e: Exception) {
            exception = e.message.toString()
        }
        asset = AssetValue()
        isRunning = true
        if (exception != "") {
            throw RuntimeException(exception)
        }
    }

    /**
     * @deprecated do not use this constructor
     */
//    constructor(appType: AppType?, useStrictType: Boolean?, context: Context) : super(appType) {
//        getFromAndroidDB(context, useStrictType)
//        asset = AssetValue()
//        //isRunning = true;
//    }

    constructor(dbWallets: ArrayList<HashMap<String, *>>?, dbOutsideWallets: ArrayList<HashMap<String, *>>?, dbTransactions: ArrayList<HashMap<String, *>>?, appType: AppType, amountTxFailed: Long) : super(appType) {
        initFromFirebase(dbWallets!!, dbOutsideWallets, dbTransactions!!, appType, amountTxFailed)
        asset = AssetValue()
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
                    totalPrice = totalPrice.add(wallet?.moneySpent)
                }
                AppType.CroCard -> {
                    val wallets = txApp!!.wallets.clone() as ArrayList<Wallet>
                    wallets.removeAt(0)
                    for (wallet in wallets) {
                        totalPrice = totalPrice.add(wallet.amount)
                    }
                }
                else -> throw RuntimeException("Usage not found")
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
            val valueOfAll = AtomicReference(0.0)   //TODO not necessary
            for (wallet in txApp!!.wallets) {
                if (wallet!!.currencyType == "EUR") continue
                val price = asset.getPrice(wallet.currencyType)!!
                val amount = wallet.amountBonus
                valueOfAll.updateAndGet { v: Double -> v + price * amount.toDouble() }
            }
            valueOfAll.get()
        } catch (e: Exception) {
            0.0
        }

    /**
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    private fun getTotalBonus(wallet: Wallet?): Double {
        return try {
            val valueOfAll = AtomicReference(0.0)   //TODO not necessary
            val price = asset.getPrice(wallet!!.currencyType)!!
            val amount = wallet.amountBonus
            valueOfAll.updateAndGet { v: Double -> v + price * amount.toDouble() }
            valueOfAll.get()
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Returns the total amount the assets are worth in EUR
     *
     * @return the total amount the assets are worth in EUR
     */
    val valueOfAssets: Double
        get() = try {
            var valueOfAll = 0.0
            for (w in txApp!!.wallets) {
                if (w!!.currencyType == "EUR") continue
                val price = asset.getPrice(w.currencyType)!!
                val amount = w.amount
                valueOfAll += price * amount.toDouble()
                if (valueOfAll < 0.0) {
                    val i = 0
                }
            }
            valueOfAll
        } catch (e: Exception) {
            0.0
        }

    /**
     * Returns the amount the asset is worth in EUR
     *
     * @return the amount the asset is worth in EUR
     */
    private fun getValueOfAssets(w: Wallet?): Double {
        return try {
            val valueOfWallet: Double
            val price = asset.getPrice(w!!.currencyType)!!
            val amount = w.amount
            valueOfWallet = price * amount.toDouble()
            valueOfWallet
        } catch (e: Exception) {
            0.0
        }
    }

    fun getAssetMap(wallet: Wallet?): Map<String, String?> {
        //TODO add sleep //if (!isRunning) sleep(1000);
        val total = StringHelper.formatAmountToString(wallet!!.moneySpent.toDouble())
        val map: MutableMap<String, String?> = HashMap()
        when (appType) {
            AppType.CdCsvParser -> {
                val amountOfAsset = getValueOfAssets(wallet)
                val rewardValue = getTotalBonus(wallet)
                if (asset.isRunning) {
                    map[R.id.assets_value.toString()] = StringHelper.formatAmountToString(amountOfAsset)
                    map[R.id.rewards_value.toString()] = StringHelper.formatAmountToString(rewardValue)
                    map[R.id.profit_loss_value.toString()] = StringHelper.formatAmountToString(amountOfAsset - wallet.moneySpent.toDouble())
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

    @get:Throws(InterruptedException::class)
    val parseMap: Map<String, String?>?
        get() {
            while (!isRunning) Thread.sleep(500)    //TODO remove this
            return try {
                val total = totalPrice
                val amountOfAsset = valueOfAssets
                val rewardValue = totalBonus
                val totalMoneySpent = StringHelper.formatAmountToString(total.toDouble())
                val map: MutableMap<String, String?> = HashMap()
                when (appType) {
                    AppType.CdCsvParser -> if (asset.isRunning) {
                        map[R.id.assets_valueP.toString()] = StringHelper.formatAmountToString(amountOfAsset)
                        map[R.id.rewards_value.toString()] = StringHelper.formatAmountToString(rewardValue)
                        map[R.id.profit_loss_value.toString()] = StringHelper.formatAmountToString(amountOfAsset - total.toDouble())
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
                    else -> throw RuntimeException("Usage not found")
                }
                map
            } catch (e: Exception) {
                null
            }
        }


    /**
     * @deprecated not in use, leave it here to show how to use the database
     */
//    fun setInAndroidDB(context: Context): Boolean {
//        val t = Thread {
//            try {
//                val db: AppDatabase? = AppDatabase.getInstance(context)
//                //clear db
//                db!!.clearAllTables()
//                db.walletDao().deleteAll()
//                db.transactionDao().deleteAll()
//                db.transactionDao().insertAll(txApp!!.transactions)
//                db.walletDao().insertAll(txApp!!.wallets)
//                db.walletDao().insertAll(txApp!!.outsideWallets)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//        t.start()
//        return true
//    }


    /**
     * @deprecated not in use, leave it here to show how to use the database
     */
//    private fun getFromAndroidDB(context: Context, useStrictType: Boolean?): Boolean {
//        val t = Thread {
//            try {
//                val db: AppDatabase? = AppDatabase.getInstance(context)
//                val tXs = db!!.transactionDao().all
//                val wTXs = db.walletDao().all
//                when (appType) {
//                    AppType.CdCsvParser -> {
//                        val ws: MutableList<CDCWallet> = ArrayList()
//                        wTXs!!.forEach(Consumer { w: WalletWithTransactions? ->
//                            val wallet = CDCWallet(w!!.wallet)
//                            ws.add(wallet)
//                        })
//                        txApp = TxApp(tXs, ws)
//                        isRunning = true
//                    }
//                    AppType.CroCard -> throw RuntimeException("Usage not Implemented")
//                    else -> throw RuntimeException("Usage not found")
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//        t.start()
//        return true
//    }


    private fun initFromFirebase(dbWallets: ArrayList<HashMap<String, *>>, dbOutsideWallets: ArrayList<HashMap<String, *>>?, dbTransactions: ArrayList<HashMap<String, *>>, appType: AppType, amountTxFailed: Long)
    {
        if (appType == AppType.CroCard) {
            processCroCardFromDB(dbWallets, dbTransactions, amountTxFailed)
        }

        val tXs: MutableList<Transaction> = ArrayList()
        val wTXs: MutableList<CDCWallet> = ArrayList()
        val wTXsOutside: MutableList<CDCWallet> = ArrayList()
        dbTransactions.forEach(Consumer { hashMap: HashMap<String, *> ->

            val transactionId = hashMap["transactionId"] as Long
            val description = hashMap["description"] as String
            val walletId = hashMap["walletId"] as Long
            val fromWalletId = hashMap["fromWalletId"] as Long
            val date = hashMap["date"] as Timestamp?
            val currencyType = hashMap["currencyType"] as String
            val amount = hashMap["amount"] as Double
            val nativeAmount = hashMap["nativeAmount"] as Double
            val amountBonus = hashMap["amountBonus"] as Double?
            val transactionType = hashMap["transactionType"] as String?
            val transHash = hashMap["transHash"] as String?
            val toCurrency = hashMap["toCurrency"] as String?
            val toAmount = hashMap["toAmount"] as Double?
            val isOutsideTransaction = hashMap["outsideTransaction"] as Boolean

            if (transactionType == null)
            {
                val i = 0
            }

            val transaction = Transaction(transactionId, description, walletId.toInt(), fromWalletId.toInt(), date!!.toDate(), currencyType, amount, nativeAmount, amountBonus!!,
                stringToTransactionType(transactionType), transHash, toCurrency, toAmount, isOutsideTransaction)
            tXs.add(transaction)
        })
        dbWallets.forEach(Consumer { hashMap: HashMap<String, *> ->

            val walletId = hashMap["walletId"] as Long
            val currencyType = hashMap["currencyType"] as String?
            val amount = hashMap["amount"] as Double
            val amountBonus = hashMap["amountBonus"] as Double
            val moneySpent = hashMap["moneySpent"] as Double
            val isOutsideWallet = hashMap["outsideWallet"] as Boolean

            val transactionsList = hashMap["transactions"] as MutableList<HashMap<String, *>?>?
            val transactions = ArrayList<Transaction?>()

            transactionsList?.forEach { transactionMap ->
                transactionMap?.let {
                    val dbTransaction = Transaction(
                        it["transactionId"] as Long,
                        it["description"] as String,
                        (it["walletId"] as Long).toInt(),
                        (it["fromWalletId"] as Long).toInt(),
                        (it["date"] as Timestamp?)?.toDate(),
                        it["currencyType"] as String,
                        it["amount"] as Double,
                        it["nativeAmount"] as Double,
                        it["amountBonus"] as Double,
                        TransactionType.valueOf(it["transactionType"] as String),
                        it["transHash"] as String?,
                        it["toCurrency"] as String?,
                        it["toAmount"] as Double?,
                        it["outsideTransaction"] as Boolean

                    )
                    transactions.add(dbTransaction)
                }
            }

            val wallet = CDCWallet(walletId, currencyType, amount, amountBonus, moneySpent, isOutsideWallet, transactions)
            wTXs.add(wallet)
        })
        dbOutsideWallets?.forEach(Consumer { hashMap: HashMap<String, *> ->
            val walletId = hashMap["walletId"] as Long
            val currencyType = hashMap["currencyType"] as String?
            val amount = hashMap["amount"] as Double
            val amountBonus = hashMap["amountBonus"] as Double
            val moneySpent = hashMap["moneySpent"] as Double
            val isOutsideWallet = hashMap["outsideWallet"] as Boolean

            val transactionsList = hashMap["transactions"] as MutableList<HashMap<String, *>?>?
            val transactions = ArrayList<Transaction?>()

            transactionsList?.forEach { transactionMap ->
                transactionMap?.let {
                    val dbTransaction = Transaction(
                        it["transactionId"] as Long,
                        it["description"] as String,
                        (it["walletId"] as Long).toInt(),
                        (it["fromWalletId"] as Long).toInt(),
                        (it["date"] as Timestamp?)?.toDate(),
                        it["currencyType"] as String,
                        it["amount"] as Double,
                        it["nativeAmount"] as Double,
                        it["amountBonus"] as Double,
                        TransactionType.valueOf(it["transactionType"] as String),
                        it["transHash"] as String?,
                        it["toCurrency"] as String?,
                        it["toAmount"] as Double?,
                        it["outsideTransaction"] as Boolean

                    )
                    transactions.add(dbTransaction)
                }
            }

            val wallet = CDCWallet(walletId, currencyType, amount, amountBonus, moneySpent, isOutsideWallet, transactions)
            wTXsOutside.add(wallet)
        })
        this.txApp = TxApp(tXs, wTXs, wTXsOutside, amountTxFailed)
        this.isRunning = true
    }

    private fun processCroCardFromDB(dbWallets: ArrayList<HashMap<String, *>>, dbTransactions: ArrayList<HashMap<String, *>>, amountTxFailed: Long) {
        val tXs: MutableList<CroCardTransaction> = ArrayList()
        val wTXs: MutableList<CroCardWallet> = ArrayList()
        dbTransactions.forEach(Consumer { hashMap: HashMap<String, *> ->

            val transactionId = hashMap["transactionId"] as Long
            val description = hashMap["description"] as String
            val walletId = hashMap["walletId"] as Long
            val fromWalletId = hashMap["fromWalletId"] as Long
            val date = (hashMap["date"] as Timestamp).toDate()
            val currencyType = hashMap["currencyType"] as String
            val amount = hashMap["amount"] as Double
            val nativeAmount = hashMap["nativeAmount"] as Double
            val amountBonus = hashMap["amountBonus"] as Double?
            val transactionType = hashMap["transactionType"] as String
            val transHash = hashMap["transHash"] as String?
            val toCurrency = hashMap["toCurrency"] as String?
            val toAmount = hashMap["toAmount"] as Double?
            val isOutsideTransaction = hashMap["outsideTransaction"] as Boolean
            val transactionTypeString = hashMap["transactionTypeString"] as String

            val transaction = CroCardTransaction(transactionId, description, walletId.toInt(), fromWalletId.toInt(), date, currencyType, amount, nativeAmount, amountBonus!!,
                transactionType, transHash, toCurrency, toAmount, isOutsideTransaction, transactionTypeString)
            tXs.add(transaction)
        })
        dbWallets.forEach(Consumer { hashMap: HashMap<String, *> ->

            val walletId = hashMap["walletId"] as Long
            val currencyType = hashMap["currencyType"] as String?
            val amount = hashMap["amount"] as Double
            val amountBonus = hashMap["amountBonus"] as Double
            val moneySpent = hashMap["moneySpent"] as Double
            val isOutsideWallet = hashMap["outsideWallet"] as Boolean

            val transactionsList = hashMap["transactions"] as MutableList<HashMap<String, *>?>?
            val transactions = ArrayList<CroCardTransaction?>()

            transactionsList?.forEach { transactionMap ->
                transactionMap?.let {
                    val dbTransaction = CroCardTransaction(
                        it["transactionId"] as Long,
                        it["description"] as String,
                        (it["walletId"] as Long).toInt(),
                        (it["fromWalletId"] as Long).toInt(),
                        (it["date"] as Timestamp).toDate(),
                        it["currencyType"] as String,
                        it["amount"] as Double,
                        it["nativeAmount"] as Double,
                        it["amountBonus"] as Double,
                        it["transactionType"] as String,
                        it["transHash"] as String?,
                        it["toCurrency"] as String?,
                        it["toAmount"] as Double?,
                        it["outsideTransaction"] as Boolean,
                        it["transactionTypeString"] as String
                    )
                    transactions.add(dbTransaction)
                }
            }

            val wallet = CroCardWallet(walletId, currencyType, amount, amountBonus, moneySpent, isOutsideWallet, transactions)
            wTXs.add(wallet)
        })
        this.txApp = CroCardTxApp(tXs, wTXs, amountTxFailed)
        this.isRunning = true
    }


    fun toHashMap(): HashMap<String, Any>
    {
        val appHashMap: HashMap<String, Any>
        when (appType)
        {
            AppType.CdCsvParser ->
            {
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
                    "appType" to appType!!
                )
            }
            AppType.CroCard ->
            {
                val dbWallets = ArrayList<CCDBWallet>()
                val dbTransactions = ArrayList<CCDBTransaction>()
                txApp!!.wallets.forEach {
                    dbWallets.add(CCDBWallet(it!!))
                }
                txApp!!.transactions.forEach {
                    dbTransactions.add(CCDBTransaction(it))
                }

                appHashMap = hashMapOf<String, Any>(
                    "wallets" to dbWallets,
                    "transactions" to dbTransactions,
                    "amountTxFailed" to txApp!!.amountTxFailed,
                    "appType" to appType!!
                )
            }
            else -> throw RuntimeException("Usage not found")
        }
        return appHashMap
    }

}