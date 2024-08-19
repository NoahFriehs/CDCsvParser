package at.msd.friehs_bicha.cdcsvparser.db

import androidx.room.Embedded
import androidx.room.Relation
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

class WalletWithTransactions(
    @Embedded val wallet: Wallet,
    @Relation(
        parentColumn = "walletId",
        entityColumn = "walletId",
        entity = Transaction::class
    )
    var transactions: List<Transaction>
)