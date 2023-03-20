package at.msd.friehs_bicha.cdcsvparser.wallet

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

//@Entity(tableName = "WalletWithTransactions")
class WalletWithTransactions(@field:Embedded var wallet: Wallet, transactions: ArrayList<Transaction>) {
    @Relation(parentColumn = "walletId", entityColumn = "walletId", associateBy = Junction(WalletTransactionCrossRef::class))
    var transactions: List<Transaction>

    init {
        this.transactions = transactions
    }
}