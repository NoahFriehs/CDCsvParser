package at.msd.friehs_bicha.cdcsvparser.general

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.App.AppType
import at.msd.friehs_bicha.cdcsvparser.App.CroCardTxApp
import at.msd.friehs_bicha.cdcsvparser.App.TxApp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletWithTransactions
import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

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

    constructor(appType: AppType?, useStrictType: Boolean?, context: Context) : super(appType) {
        getFromAndroidDB(context, useStrictType)
        asset = AssetValue()
        //isRunning = true;
    }

    /**
     * Returns the total amount spent
     *
     * @return the total amount spent
     */
    val totalPrice: BigDecimal
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
    val totalBonus: Double
        get() = try {
            val valueOfAll = AtomicReference(0.0)
            for (wallet in txApp!!.wallets) {
                if (wallet!!.currencyType == "EUR") continue
                val price = asset.getPrice(wallet.currencyType)!!
                val amount = wallet.amountBonus
                valueOfAll.updateAndGet { v: Double -> v + price * amount!!.toDouble() }
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
    fun getTotalBonus(wallet: Wallet?): Double {
        return try {
            val valueOfAll = AtomicReference(0.0)
            val price = asset.getPrice(wallet!!.currencyType)!!
            val amount = wallet.amountBonus
            valueOfAll.updateAndGet { v: Double -> v + price * amount!!.toDouble() }
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
                valueOfAll += price * amount!!.toDouble()
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
    fun getValueOfAssets(w: Wallet?): Double {
        return try {
            val valueOfWallet: Double
            val price = asset.getPrice(w!!.currencyType)!!
            val amount = w.amount
            valueOfWallet = price * amount!!.toDouble()
            valueOfWallet
        } catch (e: Exception) {
            0.0
        }
    }

    fun getAssetMap(wallet: Wallet?): Map<String, String?> {
        //TODO add sleep //if (!isRunning) sleep(1000);
        val total = wallet!!.moneySpent.round(MathContext(0))
        val map: MutableMap<String, String?> = HashMap()
        when (appType) {
            AppType.CdCsvParser -> {
                val amountOfAsset = getValueOfAssets(wallet)
                val rewardValue = getTotalBonus(wallet)
                if (asset.isRunning) {
                    map[R.id.assets_value.toString()] = (Math.round(amountOfAsset * 100.0) / 100.0).toString() + " €"
                    map[R.id.rewards_value.toString()] = (Math.round(rewardValue * 100.0) / 100.0).toString() + " €"
                    map[R.id.profit_loss_value.toString()] = (Math.round((amountOfAsset - total.toDouble()) * 100.0) / 100.0).toString() + " €"
                    map[R.id.money_spent_value.toString()] = "$total €"
                } else {
                    map[R.id.assets_value.toString()] = "no internet connection"
                    map[R.id.rewards_value.toString()] = "no internet connection"
                    map[R.id.profit_loss_value.toString()] = "no internet connection"
                    map[R.id.money_spent_value.toString()] = "$total €"
                }
            }
            AppType.CroCard -> {
                map[R.id.money_spent_value.toString()] = "$total €"
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
            while (!isRunning) Thread.sleep(500)
            return try {
                val total = totalPrice
                val amountOfAsset = valueOfAssets
                val rewardValue = totalBonus
                val totalMoneySpent = StringHelper.formatAmountToString(total.toDouble())
                val map: MutableMap<String, String?> = HashMap()
                when (appType) {
                    AppType.CdCsvParser -> if (asset.isRunning) {
                        map[R.id.assets_value.toString()] = StringHelper.formatAmountToString(amountOfAsset)
                        map[R.id.rewards_value.toString()] = StringHelper.formatAmountToString(rewardValue)
                        map[R.id.profit_loss_value.toString()] = StringHelper.formatAmountToString(amountOfAsset - total.toDouble())
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                    } else {
                        map[R.id.assets_value.toString()] = "no internet connection"
                        map[R.id.rewards_value.toString()] = "no internet connection"
                        map[R.id.profit_loss_value.toString()] = "no internet connection"
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                    }
                    AppType.CroCard -> {
                        map[R.id.money_spent_value.toString()] = totalMoneySpent
                        map[R.id.assets_value.toString()] = null
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

    fun setInAndroidDB(context: Context): Boolean {
        val t = Thread {
            try {
                val db: AppDatabase? = AppDatabase.Companion.getInstance(context)
                //clear db
                db!!.clearAllTables()
                db.walletDao().deleteAll()
                db.transactionDao().deleteAll()
                db.transactionDao().insertAll(txApp!!.transactions)
                db.walletDao().insertAll(txApp!!.wallets)
                db.walletDao().insertAll(txApp!!.outsideWallets)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        t.start()
        return true
    }

    fun getFromAndroidDB(context: Context, useStrictType: Boolean?): Boolean {
        val t = Thread {
            try {
                val db: AppDatabase? = AppDatabase.Companion.getInstance(context)
                val tXs = db!!.transactionDao().all
                val wTXs = db.walletDao().all
                when (appType) {
                    AppType.CdCsvParser -> {
                        val ws: MutableList<CDCWallet> = ArrayList()
                        wTXs!!.forEach(Consumer { w: WalletWithTransactions? ->
                            val wallet = CDCWallet(w!!.wallet)
                            ws.add(wallet)
                        })
                        txApp = TxApp(tXs, ws)
                        isRunning = true
                    }
                    AppType.CroCard -> throw RuntimeException("Usage not Implemented")
                    else -> throw RuntimeException("Usage not found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        t.start()
        return true
    }

    companion object {
        var asset: AssetValue = AssetValue()
    }
}