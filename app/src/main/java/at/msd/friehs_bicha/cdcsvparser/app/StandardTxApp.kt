package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionManager
import java.io.Serializable

class StandardTxApp : BaseApp, Serializable {

    constructor(file: ArrayList<String>) {

        transactions = TransactionManager.txFromCsvList(file, null, this)
        FileLog.i("TxApp","Transactions: " + transactions.size)
        //fillWallet(transactions)  //TODO: der fillWallet() Aufruf muss überarbeitet werden -> auf interne TxTypes umstellen!!!
    }


    }