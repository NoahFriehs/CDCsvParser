package at.msd.friehs_bicha.cdcsvparser.transactions

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

/**
 * All different TransactionTypes
 */
enum class TransactionType {
    crypto_purchase,  //Purchase of crypto
    supercharger_deposit,  //Deposit to supercharger
    rewards_platform_deposit_credited,  //Reward from missions
    supercharger_reward_to_app_credited,  //Reward from supercharger
    viban_purchase,  //Purchase of crypto
    crypto_earn_program_created,  //Deposit to earn
    crypto_earn_interest_paid,  //Earn interest
    supercharger_withdrawal,  //Withdrawal from supercharger
    lockup_lock,  //lock for stake
    crypto_withdrawal,  //withdrawal
    crypto_deposit, referral_card_cashback,  //Card Cashback
    reimbursement,  //Money back for Spotify
    card_cashback_reverted,  //Cashbackreverted
    crypto_earn_program_withdrawn,  //Withdraw from earn
    admin_wallet_credited,  //Free money from fork
    crypto_wallet_swap_credited,  //fork stuff
    crypto_wallet_swap_debited,  //fork stuff
    dust_conversion_credited,  //Dust conversion end
    dust_conversion_debited,  //Dust conversion start
    crypto_viban_exchange,  //sell
    STRING //for Card und unknown things
}

/**
 * Convert String to TransactionType
 */
fun stringToTransactionType(str: String?): TransactionType {
    return try {
        if (str != null) {
            TransactionType.valueOf(str)
        } else {
            TransactionType.STRING
        }
    } catch (e: IllegalArgumentException) {
        FileLog.e("TransactionType", "Unknown TransactionType: $str")
        TransactionType.STRING
    }
}