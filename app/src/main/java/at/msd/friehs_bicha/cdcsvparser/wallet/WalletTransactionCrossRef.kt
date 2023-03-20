package at.msd.friehs_bicha.cdcsvparser.wallet

import androidx.room.Entity
import androidx.room.ForeignKey
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

@Entity(tableName = "wallet_transaction", primaryKeys = ["walletId", "transactionId"], foreignKeys = [ForeignKey(entity = Wallet::class, parentColumns = arrayOf("walletId"), childColumns = arrayOf("walletId")), ForeignKey(entity = Transaction::class, parentColumns = arrayOf("transactionId"), childColumns = arrayOf("transactionId"))])
class WalletTransactionCrossRef {
    var walletId = 0
    var transactionId = 0
}