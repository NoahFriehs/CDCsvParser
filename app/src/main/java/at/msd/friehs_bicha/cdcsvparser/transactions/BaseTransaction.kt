package at.msd.friehs_bicha.cdcsvparser.transactions

import com.google.type.DateTime
import java.math.BigDecimal

class BaseTransaction {

    var m_txID: Int
    var m_txIDexternal: String
    var m_txRefID: String
    var m_rxRefIDexternal: String
    var m_time: DateTime
    var m_mode: TransactionMode //enum
    var m_submode: TransactionSubmode   //enum
    var m_txTypeExtern: String
    var m_txTypeIntern: String
    var m_assetClass: String
    var m_assetType: String
    var m_toAssetType: String
    var m_amount: BigDecimal
    var m_amountNative: BigDecimal
    var m_amountBonus: BigDecimal?
    var m_fee: BigDecimal
    var m_balanceAfter: BigDecimal
    var m_walletID: Int
    var m_fromWalletID: Int
    var m_txHash: String?
    var m_isWithdrawal: Boolean
    var m_description: String
    var m_notes: String

    constructor(
        m_txID: Int,
        m_txIDexternal: String,
        m_txRefID: String,
        m_rxRefIDexternal: String,
        m_time: DateTime,
        m_mode: TransactionMode,
        m_submode: TransactionSubmode,
        m_txTypeExtern: String,
        m_txTypeIntern: String,
        m_assetClass: String,
        m_assetType: String,
        m_toAssetType: String,
        m_amount: BigDecimal,
        m_amountNative: BigDecimal,
        m_amountBonus: BigDecimal?,
        m_fee: BigDecimal,
        m_balanceAfter: BigDecimal,
        m_walletID: Int,
        m_fromWalletID: Int,
        m_txHash: String?,
        m_isWithdrawal: Boolean,
        m_description: String,
        m_notes: String
    ) {
        this.m_txID = m_txID
        this.m_txIDexternal = m_txIDexternal
        this.m_txRefID = m_txRefID
        this.m_rxRefIDexternal = m_rxRefIDexternal
        this.m_time = m_time
        this.m_mode = m_mode
        this.m_submode = m_submode
        this.m_txTypeExtern = m_txTypeExtern
        this.m_txTypeIntern = m_txTypeIntern
        this.m_assetClass = m_assetClass
        this.m_assetType = m_assetType
        this.m_toAssetType = m_toAssetType
        this.m_amount = m_amount
        this.m_amountNative = m_amountNative
        this.m_amountBonus = m_amountBonus
        this.m_fee = m_fee
        this.m_balanceAfter = m_balanceAfter
        this.m_walletID = m_walletID
        this.m_fromWalletID = m_fromWalletID
        this.m_txHash = m_txHash
        this.m_isWithdrawal = m_isWithdrawal
        this.m_description = m_description
        this.m_notes = m_notes
    }
}