package at.msd.friehs_bicha.cdcsvparser.transactions

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionData(
    var transactionId: Int,
    var description: String,
    var walletId: Int,
    var fromWalletId: Int,
    var currencyType: String,
    amount: Double,
    nativeAmount: Double,
    amountBonus: Double,
    transactionTypeOrdinal: Int,
    dateSeconds: Int,
    dateMinutes: Int,
    dateHours: Int,
    dateDay: Int,
    dateMonth: Int,
    dateYear: Int
) {

    var date: Date?

    var amount: Double

    var nativeAmount: Double

    var amountBonus: Double

    var transactionType: TransactionType

    var transactionTypeString: String = ""

    var transHash: String = ""

    var toCurrency: String = ""

    var toAmount: Double = 0.0

    var isOutsideTransaction = false

    var notes: String = ""

    init {
        this.amount = amount
        this.nativeAmount = nativeAmount
        this.amountBonus = amountBonus
        this.transactionType = TransactionType.values()[transactionTypeOrdinal]
        this.date = Date(dateYear, dateMonth, dateDay, dateHours, dateMinutes, dateSeconds)
    }

    fun toXml(): String {
        val serializer: XmlSerializer = Xml.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)

        serializer.startDocument("UTF-8", true)
        serializer.startTag("", "TransactionData")

        serializer.startTag("", "transactionId")
        serializer.text(transactionId.toString())
        serializer.endTag("", "transactionId")

        serializer.startTag("", "description")
        serializer.text(description)
        serializer.endTag("", "description")

        serializer.startTag("", "walletId")
        serializer.text(walletId.toString())
        serializer.endTag("", "walletId")

        serializer.startTag("", "fromWalletId")
        serializer.text(fromWalletId.toString())
        serializer.endTag("", "fromWalletId")

        serializer.startTag("", "currencyType")
        serializer.text(currencyType)
        serializer.endTag("", "currencyType")

        serializer.startTag("", "toCurrencyType")
        serializer.text(toCurrency)
        serializer.endTag("", "toCurrencyType")

        serializer.startTag("", "amount")
        serializer.text(amount.toString())
        serializer.endTag("", "amount")

        serializer.startTag("", "toAmount")
        serializer.text(toAmount.toString())
        serializer.endTag("", "toAmount")

        serializer.startTag("", "nativeAmount")
        serializer.text(nativeAmount.toString())
        serializer.endTag("", "nativeAmount")

        serializer.startTag("", "amountBonus")
        serializer.text(amountBonus.toString())
        serializer.endTag("", "amountBonus")

        serializer.startTag("", "tTO")
        serializer.text(transactionType.ordinal.toString())
        serializer.endTag("", "tTO")

        serializer.startTag("", "tTS")
        serializer.text(transactionTypeString)
        serializer.endTag("", "tTS")

        serializer.startTag("", "transactionHash")
        serializer.text(transHash)
        serializer.endTag("", "transactionHash")

        serializer.startTag("", "outTx")
        serializer.text(isOutsideTransaction.toString())
        serializer.endTag("", "outTx")

        serializer.startTag("", "notes")
        serializer.text(notes)
        serializer.endTag("", "notes")

        // Format date and time as "YYYY-MM-DD HH:mm:ss"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(date)

        serializer.startTag("", "transactionDate")
        serializer.text(formattedDate)
        serializer.endTag("", "transactionDate")

        serializer.endTag("", "TransactionData")
        serializer.endDocument()

        return writer.toString()
    }

    fun fromXml(xml: String) {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())

        var eventType = parser.eventType
        var tagName = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> tagName = parser.name
                XmlPullParser.TEXT -> {
                    when (tagName) {
                        "transactionId" -> transactionId = parser.text.toInt()
                        "description" -> description = parser.text
                        "walletId" -> walletId = parser.text.toInt()
                        "fromWalletId" -> fromWalletId = parser.text.toInt()
                        "transactionDate" -> {
                            // Parse date from the formatted string
                            val dateFormat =
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            date = dateFormat.parse(parser.text)
                        }

                        "currencyType" -> currencyType = parser.text
                        "toCurrencyType" -> toCurrency = parser.text
                        "amount" -> amount = parser.text.toDouble()
                        "toAmount" -> toAmount = parser.text.toDouble()
                        "nativeAmount" -> nativeAmount = parser.text.toDouble()
                        "amountBonus" -> amountBonus = parser.text.toDouble()
                        "tTO" -> transactionType =
                            fromOrdinal(parser.text.toInt())

                        "tTS" -> transactionTypeString =
                            parser.text


                        "transactionHash" -> transHash = parser.text
                        "outTx" -> isOutsideTransaction = parser.text.toBoolean()
                        "notes" -> notes = parser.text
                    }
                }
            }
            eventType = parser.next()
        }
    }

    companion object {
        fun fromTransaction(transaction: Transaction): TransactionData {
            return TransactionData(
                transaction.transactionId,
                transaction.description,
                transaction.walletId,
                transaction.fromWalletId,
                transaction.currencyType,
                transaction.amount.toDouble(),
                transaction.nativeAmount.toDouble(),
                transaction.amountBonus?.toDouble() ?: 0.0,
                transaction.transactionType?.ordinal ?: TransactionType.STRING.ordinal,
                transaction.date?.seconds ?: 0,
                transaction.date?.minutes ?: 0,
                transaction.date?.hours ?: 0,
                transaction.date?.day ?: 0,
                transaction.date?.month ?: 0,
                transaction.date?.year ?: 0
            )
        }
    }
}