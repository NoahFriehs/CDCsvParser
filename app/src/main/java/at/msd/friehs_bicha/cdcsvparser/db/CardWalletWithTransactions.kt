package at.msd.friehs_bicha.cdcsvparser.db

import androidx.room.Embedded
import androidx.room.Relation
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet

class CardWalletWithTransactions (
    @Embedded val wallet: CroCardWallet,
    @Relation(
        parentColumn = "walletId",
        entityColumn = "walletId",
        entity = CroCardTransaction::class
    )
    var transactions: List<CroCardTransaction>
)
