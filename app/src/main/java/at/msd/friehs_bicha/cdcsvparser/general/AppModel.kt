package at.msd.friehs_bicha.cdcsvparser.general

import android.graphics.Color
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.*
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.*
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper.formatAmountToString
import at.msd.friehs_bicha.cdcsvparser.wallet.*
import java.io.Serializable
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * The parser control for the Parser
 */
class AppModel : BaseAppModel, Serializable {


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
        txApp = TxAppFactory.createTxApp(
            appType,
            AppStatus.NotStarted,
            useStrictType,
            hashMapOf(DataTypes.csvAsList to file)
        )
        isRunning = true
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
                    val wallets = txApp!!.wallets.clone() as ArrayList<Wallet>
                    for (wallet in wallets) {
                        totalPrice = totalPrice.add(wallet.amount)
                    }
                }

                else -> {
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
                val price = wallet.currencyType?.let { AssetValue.getInstance()!!.getPrice(it) }!!
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
            val price = wallet!!.currencyType?.let { AssetValue.getInstance()!!.getPrice(it) }!!
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
                val price = w.currencyType?.let { AssetValue.getInstance()!!.getPrice(it) }!!
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


    /**
     * Returns the amount the asset is worth in EUR
     *
     * @return the amount the asset is worth in EUR
     */
    fun getValueOfAssets(w: Wallet?): Double {
        return try {
            val valueOfWallet: Double
            val price = w!!.currencyType?.let { AssetValue.getInstance()!!.getPrice(it) }!!
            val amount = w.amount
            valueOfWallet = price * amount.toDouble()
            valueOfWallet
        } catch (e: Exception) {
            FileLog.e("AppModel.getValueOfAssets", "Exception: $e")
            0.0
        }
    }


    /**
     * Returns a map with the data to display from a wallet
     *
     * @return a map with the data to display from a wallet
     */
    fun getAssetMap(wallet: Wallet?): Map<String, String?> {
        val total = formatAmountToString(wallet!!.moneySpent.toDouble())
        val map: MutableMap<String, String?> = HashMap()
        when (appType) {
            AppType.CdCsvParser -> {
                val amountOfAsset = getValueOfAssets(wallet)
                val rewardValue = getTotalBonus(wallet)
                if (AssetValue.getInstance()!!.isRunning) {
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
                val amountOfAsset = valueOfAssets
                val rewardValue = totalBonus
                val totalMoneySpent = formatAmountToString(total.toDouble())
                val map: MutableMap<String, String?> = HashMap()
                when (appType) {
                    AppType.CdCsvParser -> if (AssetValue.getInstance()!!.isRunning) {
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

                    else -> {
                        FileLog.e("AppModel", "ParseMap: AppType not found, AppType: $appType")
                        throw RuntimeException("Usage not found")
                    }
                }
                map
            } catch (e: Exception) {
                e.message?.let { FileLog.w("ParseMap", it) }
                null
            }
        }


    fun toHashMap(): HashMap<String, Any> {
        val appHashMap: HashMap<String, Any>
        when (appType) {
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
                    "appType" to appType!!
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
                    "appType" to appType!!
                )
            }

            else -> {
                FileLog.e("AppModel", "CroCard: Usage not found, AppType: $appType")
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


    fun getWalletAdapter(wallet: Wallet): MutableMap<String, String?> {
        val assetValue = getValueOfAssets(wallet)
        var percentProfit = assetValue / wallet.moneySpent.toDouble() * 100
        if (percentProfit.isNaN()) {
            percentProfit = 0.0
        }
        val assetValueString = formatAmountToString(assetValue, 5)
        val amountString =
            formatAmountToString(wallet.amount.toDouble(), 5, wallet.currencyType!!)
        val color: Int = if (percentProfit > 100) {
            Color.GREEN
        } else if (percentProfit == 100.0 || percentProfit == 0.0) {
            Color.GRAY
        } else {
            Color.RED
        }

        val walletName: String = if (wallet is CroCardWallet) wallet.transactionType.toString()
        else wallet.currencyType!!

        val map: MutableMap<String, String?> = HashMap()
        map[R.id.walletId.toString()] = wallet.walletId.toString()
        map[R.id.currencyType.toString()] = walletName
        map[R.id.amount.toString()] = amountString
        map[R.id.amountValue.toString()] = assetValueString
        map[R.id.percentProfit.toString()] =
            formatAmountToString(percentProfit - 100, 2, "%", true)
        map[R.id.amountTransactions.toString()] = wallet.transactions?.count().toString()
        map["COLOR"] = color.toString()
        return map
    }

    fun getTransactionAdapter(transaction: Transaction): MutableMap<String, String?> {

        val defaultLocale = Locale.getDefault()
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", defaultLocale)

        val map: MutableMap<String, String?> = HashMap()
        map[R.id.tv_transactionId.toString()] = transaction.transactionId.toString()
        map[R.id.tv_date.toString()] = transaction.date?.let { dateFormat.format(it).toString() }
        map[R.id.tv_descriptionValue.toString()] = transaction.description
        map[R.id.tv_amountValue.toString()] =
            formatAmountToString(transaction.nativeAmount.toDouble())
        map[R.id.tv_assetAmountValue.toString()] =
            formatAmountToString(transaction.amount.toDouble(), 6, transaction.currencyType)
        return map
    }

}