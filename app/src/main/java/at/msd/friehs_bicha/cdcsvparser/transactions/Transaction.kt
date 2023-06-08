package at.msd.friehs_bicha.cdcsvparser.transactions

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType
import com.google.firebase.Timestamp
import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.Date

/**
 * Represents a Transaction object
 */
open class Transaction : Serializable {

    var transactionId: Int

    var description: String

    var walletId = 0

    var fromWalletId = 0

    var date: Date?

    var currencyType: String

    var amount: BigDecimal

    var nativeAmount: BigDecimal

    var amountBonus: BigDecimal?

    var transactionType: TransactionType?

    var transHash: String? = null

    var toCurrency: String? = null

    var toAmount: BigDecimal? = null

    var isOutsideTransaction = false

    var notes: String = ""

    constructor(
        date: String?,
        description: String,
        currencyType: String,
        amount: BigDecimal?,
        nativeAmount: BigDecimal?,
        transactionType: TransactionType?
    ) {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.date = Converter.dateConverter(date)
        this.description = description
        this.currencyType = currencyType
        this.amount = BigDecimal.ZERO
        this.amount = this.amount.add(amount)
        this.nativeAmount = BigDecimal.ZERO
        this.nativeAmount = this.nativeAmount.add(nativeAmount)
        this.transactionType = transactionType
        amountBonus = BigDecimal.ZERO
        transactionId = ++uidCounter
    }

    constructor(
        transactionId: Int,
        date: Date?,
        description: String,
        currencyType: String,
        amount: BigDecimal,
        nativeAmount: BigDecimal,
        amountBonus: BigDecimal?,
        transactionType: TransactionType?,
        transHash: String?,
        toCurrency: String?,
        toAmount: BigDecimal?,
        walletId: Int
    ) {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.transactionId = transactionId
        this.date = date
        this.description = description
        this.currencyType = currencyType
        this.amount = amount
        this.nativeAmount = nativeAmount
        this.amountBonus = amountBonus
        this.transactionType = transactionType
        this.transHash = transHash
        this.toCurrency = toCurrency
        this.toAmount = toAmount
        this.walletId = walletId
    }

    constructor(transaction: DBTransaction) {
        if (!CurrencyType.currencys.contains(transaction.currencyType)) CurrencyType.currencys.add(
            transaction.currencyType
        )
        this.transactionId = transaction.transactionId.toInt()
        this.date = transaction.date
        this.description = transaction.description
        this.currencyType = transaction.currencyType
        this.amount = BigDecimal(transaction.amount)
        this.nativeAmount = BigDecimal(transaction.nativeAmount)
        this.amountBonus = transaction.amountBonus?.let { BigDecimal(it) }
        this.transactionType = transaction.transactionType
        this.transHash = transaction.transHash
        this.toCurrency = transaction.toCurrency
        this.toAmount = transaction.toAmount?.let { BigDecimal(it) }
        this.walletId = transaction.walletId
    }

    constructor(
        transactionId: Long,
        description: String,
        walletId: Int,
        fromWalletId: Int,
        date: Date?,
        currencyType: String,
        amount: Double,
        nativeAmount: Double,
        amountBonus: Double,
        transactionType: TransactionType?,
        transHash: String?,
        toCurrency: String?,
        toAmount: Double?,
        outsideTransaction: Boolean
    ) {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.transactionId = transactionId.toInt()
        this.date = date
        this.description = description
        this.currencyType = currencyType
        this.amount = BigDecimal(amount)
        this.nativeAmount = BigDecimal(nativeAmount)
        this.amountBonus = BigDecimal(amountBonus)
        this.transactionType = transactionType
        this.transHash = transHash
        this.toCurrency = toCurrency
        this.toAmount = toAmount?.let { BigDecimal(it) }
        this.walletId = walletId
        this.fromWalletId = fromWalletId
        this.isOutsideTransaction = outsideTransaction
    }

//    fun setWalletId(uid: Int) {
//        walletId = uid
//    }
//
//    fun setFromWalletId(uid: Int) {
//        fromWalletId = uid
//    }

    override fun toString(): String {
        return """${date.toString()}
Description: $description
Amount: ${nativeAmount.round(MathContext(5))} €
AssetAmount: ${amount.round(MathContext(5))} $currencyType"""
    }

    companion object {

        /**
         * @exception does not work yet, because of the different csv formats
         *
         * @param line
         * @param appType
         * @return
         */
        fun fromCsvLine(line: String, appType: AppType): Transaction? {

            return when (appType) {
                AppType.CdCsvParser -> fromCdCsvParserCsvLine(line)
//                AppType.COINBASE -> fromCoinbaseCsvLine(line)
//                AppType.BLOCKCHAIN_INFO -> fromBlockchainInfoCsvLine(line)
//                AppType.BITPANDA -> fromBitpandaCsvLine(line)
//                AppType.BINANCE -> fromBinanceCsvLine(line)
//                AppType.BITCOIN_DE -> fromBitcoinDeCsvLine(line)
//                AppType.KRAKEN -> fromKrakenCsvLine(line)
//                AppType.BITFINEX -> fromBitfinexCsvLine(line)
                else -> {
                    throw IllegalArgumentException("AppType not supported")
                }
            }
        }

        fun fromDb(transaction: HashMap<String, *>): Transaction
        {
            return Transaction(
                transaction["transactionId"] as Long,
                transaction["description"] as String,
                (transaction["walletId"] as Long).toInt(),
                (transaction["fromWalletId"] as Long).toInt(),
                (transaction["date"] as Timestamp).toDate(),
                transaction["currencyType"] as String,
                transaction["amount"] as Double,
                transaction["nativeAmount"] as Double,
                transaction["amountBonus"] as Double,
                stringToTransactionType(transaction["transactionType"] as String?),
                transaction["transHash"] as String?,
                transaction["toCurrency"] as String?,
                transaction["toAmount"] as Double?,
                transaction["outsideTransaction"] as Boolean
            )
        }

        private fun fromCdCsvParserCsvLine(line: String): Transaction? {

            val sa = line.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (sa.size == 10 || sa.size == 11) {
                val t = Transaction(
                    sa[0],
                    sa[1],
                    sa[2],
                    Converter.amountConverter(sa[3]),
                    Converter.amountConverter(sa[7]),
                    Converter.ttConverter(sa[9])
                )
                if (sa.size == 11) t.transHash = sa[10]
                if (Converter.ttConverter(sa[9]) == TransactionType.viban_purchase) {
                    t.toCurrency = sa[4]
                    t.toAmount = Converter.amountConverter(sa[5])
                }
                return t
            } else {
                println(sa.contentToString())
                println(sa.size)
                FileLog.e(
                    "TxApp",
                    "Error while processing the following transaction: $line"
                )
            }
            return null //throw IllegalArgumentException("Error while processing the following transaction: $line")
        }

        private fun fromBitfinexCsvLine(line: String): Transaction {
            val split = line.split(",")
            val date = Converter.dateConverter(split[0])
            val description = split[1]
            val currencyType = split[2]
            val amount = Converter.amountConverter(split[3])
            val nativeAmount = Converter.amountConverter(split[4])
            val transactionType = TransactionType.valueOf(split[5])
            return Transaction(
                split[0],
                description,
                currencyType,
                amount,
                nativeAmount,
                transactionType
            )
        }

        private fun fromKrakenCsvLine(line: String): Transaction {
            val split = line.split(",")
            val date = Converter.dateConverter(split[0])
            val description = split[1]
            val currencyType = split[2]
            val amount = Converter.amountConverter(split[3])
            val nativeAmount = Converter.amountConverter(split[4])
            val transactionType = TransactionType.valueOf(split[5])
            return Transaction(
                split[0],
                description,
                currencyType,
                amount,
                nativeAmount,
                transactionType
            )
        }

        private fun fromBitcoinDeCsvLine(line: String): Transaction {
            val split = line.split(",")
            val date = Converter.dateConverter(split[0])
            val description = split[1]
            val currencyType = split[2]
            val amount = Converter.amountConverter(split[3])
            val nativeAmount = Converter.amountConverter(split[4])
            val transactionType = TransactionType.valueOf(split[5])
            return Transaction(
                split[0],
                description,
                currencyType,
                amount,
                nativeAmount,
                transactionType
            )
        }

        private fun fromBinanceCsvLine(line: String): Transaction {
            val split = line.split(",")
            val date = Converter.dateConverter(split[0])
            val description = split[1]
            val currencyType = split[2]
            val amount = Converter.amountConverter(split[3])
            val nativeAmount = Converter.amountConverter(split[4])
            val transactionType = TransactionType.valueOf(split[5])
            return Transaction(
                split[0],
                description,
                currencyType,
                amount,
                nativeAmount,
                transactionType
            )
        }

        var uidCounter = 0
    }
}