package at.msd.friehs_bicha.cdcsvparser.ui.display

import at.msd.friehs_bicha.cdcsvparser.core.IncompleteWalletNote
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Row icon family, resolved by the adapter to a drawable + tint. */
enum class TransactionIcon { CREDIT, DEBIT, PURCHASE, OTHER }

/** One pre-formatted row for the transaction list. Pure data, safe for diffing. */
data class TransactionRow(
    val id: Int,
    val date: String,
    val monthKey: String,
    val description: String,
    val currency: String,
    val nativeAmount: String,
    val nativeSigned: Double,
    val assetAmount: String,
    val icon: TransactionIcon,
)

private val INCOME_TYPES = setOf(
    "crypto_deposit", "crypto_transfer", "crypto_earn_interest_paid",
    "crypto_airdrop_credited", "crypto_bounty_credited", "crypto_gift_received",
    "dust_conversion_credited", "rewards_platform_deposit_credited",
    "admin_wallet_credited", "reimbursement", "supercharger_reward_to_app_credited",
    "referral_card_cashback", "card_cashback_reverted",
)

private val PURCHASE_TYPES = setOf("crypto_purchase", "viban_purchase")

/** Maps a transaction type to its row icon family. */
fun transactionIcon(typeName: String?): TransactionIcon {
    return when (typeName) {
        in INCOME_TYPES -> TransactionIcon.CREDIT
        in PURCHASE_TYPES -> TransactionIcon.PURCHASE
        "crypto_withdrawal", "crypto_wallet_swap_debited", "supercharger_withdrawal", "lockup_lock" ->
            TransactionIcon.DEBIT

        else -> TransactionIcon.OTHER
    }
}

fun transactionRow(transaction: Transaction): TransactionRow {
    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val native = transaction.nativeAmount.toDouble()
    return TransactionRow(
        id = transaction.transactionId,
        date = transaction.date?.let { dateFormat.format(it) } ?: "",
        monthKey = transaction.date?.let { monthFormat.format(it) } ?: "",
        description = transaction.description,
        currency = transaction.currencyType,
        nativeAmount = StringHelper.formatAmountToString(native),
        nativeSigned = native,
        assetAmount = StringHelper.formatAmountToString(
            transaction.amount.toDouble(),
            6,
            transaction.currencyType
        ),
        icon = transactionIcon(transaction.transactionType?.name),
    )
}

/** Wallet result trend, mapped to a color by the view (not the model). */
enum class ProfitTrend { POSITIVE, NEUTRAL, NEGATIVE }

/** One pre-formatted row for the wallet list, plus raw values for sorting. */
data class WalletRow(
    val walletId: Int,
    val name: String,
    // Raw currency type (the display [name] can differ for card wallets,
    // e.g. "EUR -> EUR").
    val currencyType: String,
    // The internal fiat buffer: a plain (non-card) EUR wallet. Card wallets
    // are all CroCardWallets, all denominated in fiat, and none of them is
    // the buffer, so the buffer can only be a plain Wallet with currency
    // type "EUR" - the exact exclusion the standard card/scope aggregates
    // already apply.
    val isFiatBuffer: Boolean,
    // Resolved at row-build time (see isIncompleteHistory): the row gains the
    // "incomplete history" footnote when the ledger is partial, not the
    // number itself.
    val incompleteHistory: Boolean = false,
    val amountText: String,
    val assetValueText: String,
    val percentText: String,
    val trend: ProfitTrend,
    val transactionCount: Int,
    val amountValue: Double,
    val assetValue: Double,
    val percentProfit: Double,
    val isOutside: Boolean,
)

fun walletRow(wallet: Wallet): WalletRow {
    val assetValue = walletValueInEur(wallet)
    var percentProfit = assetValue / wallet.moneySpent.toDouble() * 100
    if (percentProfit.isNaN()) percentProfit = 0.0
    val trend = when {
        percentProfit > 100 -> ProfitTrend.POSITIVE
        percentProfit == 100.0 || percentProfit == 0.0 -> ProfitTrend.NEUTRAL
        else -> ProfitTrend.NEGATIVE
    }
    return WalletRow(
        walletId = wallet.walletId,
        name = wallet.getTypeString(),
        currencyType = wallet.currencyType,
        isFiatBuffer = wallet !is CroCardWallet && wallet.currencyType == "EUR",
        incompleteHistory = isIncompleteHistory(wallet),
        amountText = StringHelper.formatAmountToString(wallet.amount.toDouble(), 5, wallet.currencyType),
        assetValueText = StringHelper.formatAmountToString(assetValue, 5),
        percentText = StringHelper.formatAmountToString(percentProfit - 100, 2, "%", true),
        trend = trend,
        transactionCount = wallet.transactions.count(),
        amountValue = wallet.amount.toDouble(),
        assetValue = assetValue,
        percentProfit = percentProfit,
        isOutside = wallet.isOutsideWallet,
    )
}

/**
 * Incomplete-history detection: a wallet whose FINAL computed
 * balance in its own unit is negative on a held token wallet is the
 * partial-export signature - the early rows (e.g. the first purchase) are
 * missing from the export, so the debits outrun the credits. The check is
 * on the ledger as posted; no price is involved.
 * Never flagged: the internal fiat "EUR" buffer and card wallets (a
 * negative there is an overdraft, not a missing ledger - the same scope
 * rule that keeps the buffer out of the card aggregates), and outside
 * (bookkeeping) wallets: they hold the counter entry of external movements,
 * where a negative balance is normal (a complete export with plain deposits
 * lands them negative - deposits debit the outside wallet, withdrawals
 * credit it).
 */
fun isIncompleteHistory(wallet: Wallet): Boolean =
    wallet !is CroCardWallet &&
        !wallet.isOutsideWallet &&
        wallet.currencyType != "EUR" &&
        wallet.amount.signum() < 0

/** The flagged wallets (empty = the hint stays off on every surface). */
fun incompleteHistory(wallets: List<Wallet>): List<Wallet> =
    wallets.filter { isIncompleteHistory(it) }

/** The note-row entries in wallet-name order: name + the negative balance. */
fun incompleteHistoryNotes(wallets: List<Wallet>): List<IncompleteWalletNote> =
    incompleteHistory(wallets)
        .sortedBy { it.getTypeString() }
        .map {
            IncompleteWalletNote(
                name = it.getTypeString(),
                balanceText = StringHelper.removeLastZeros(
                    DecimalFormat("#0.00000").format(it.amount.toDouble())
                ),
            )
        }

/** EUR value of a wallet; card wallets are already denominated in fiat. */
fun walletValueInEur(wallet: Wallet): Double {
    if (wallet is CroCardWallet) return wallet.amount.toDouble()
    return try {
        AssetValue.getInstance().getPrice(wallet.currencyType) * wallet.amount.toDouble()
    } catch (e: Exception) {
        0.0
    }
}

/**
 * Rows eligible for the allocation donut: outside (bookkeeping) wallets,
 * zero-value rows, and the internal fiat buffer are excluded - the buffer
 * is the same exclusion the card/scope aggregates already apply.
 * Excluded rows stay visible in the wallet list; this only feeds the donut.
 */
fun donutSlices(rows: List<WalletRow>): List<WalletRow> =
    rows.filter { !it.isOutside && it.assetValue > 0 && !it.isFiatBuffer }

enum class WalletSortKey { AMOUNT, ASSET_VALUE, PERCENT, TRANSACTIONS }

fun sortWalletRows(
    list: List<WalletRow>,
    key: WalletSortKey,
    ascending: Boolean
): List<WalletRow> {
    if (list.size <= 1) return list
    // Must match the UI labels: "amount €" sorts the EUR value, "amount
    // Asset" the raw amount in the wallet's own unit.
    val byValue: (WalletRow) -> Double = when (key) {
        WalletSortKey.AMOUNT -> { it -> it.assetValue }
        WalletSortKey.ASSET_VALUE -> { it -> it.amountValue }
        WalletSortKey.PERCENT -> { it -> it.percentProfit }
        WalletSortKey.TRANSACTIONS -> { it -> it.transactionCount.toDouble() }
    }
    return if (ascending) list.sortedBy(byValue) else list.sortedByDescending(byValue)
}
