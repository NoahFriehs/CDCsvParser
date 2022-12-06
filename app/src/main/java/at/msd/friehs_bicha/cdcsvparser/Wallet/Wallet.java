package at.msd.friehs_bicha.cdcsvparser.Wallet;

import at.msd.friehs_bicha.cdcsvparser.Transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.Transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.General.TxApp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Represents a Wallet object
 *
 */
public class Wallet {

    String currencyType;
    BigDecimal amount;
    BigDecimal amountBonus;
    BigDecimal moneySpent;

    ArrayList<Transaction> transactions;

    public Wallet(String  currencyType, BigDecimal amount, BigDecimal nativeAmount) {
        this.currencyType = currencyType;
        this.amount = new BigDecimal(0);
        this.amount = this.amount.add(amount);
        this.moneySpent = new BigDecimal(0);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = new BigDecimal(0);
        this.transactions = new ArrayList<>();
    }

    /**
     * Get Wallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the idex of the wallet
     */
    public static int getWallet(String ct) {
        int i = 0;
        for (Wallet w : TxApp.wallets) {
            if (w.getCurrencyType().equals(ct)) return i;
            i++;
        }
        return -1;
    }


    /**
     * Prints out all wallets
     *
     */
    public static void writeAmount() {
        BigDecimal amountSpent = BigDecimal.ZERO;
        for (Wallet w : TxApp.wallets) {
            System.out.println("-".repeat(20));
            System.out.println(w.getCurrencyType());
            System.out.println("Amount: " + w.amount);
            System.out.println("Money spent: " + w.moneySpent);
            System.out.println("Bonus: " + w.amountBonus);
            if (!Objects.equals(w.amount, BigDecimal.ZERO))
                if (!Objects.equals(w.amount, w.amountBonus))
                    System.out.println("AVG. Price: " + w.moneySpent.divide(w.amount.subtract(w.amountBonus), RoundingMode.DOWN));
            System.out.println("Transactions: " + w.transactions.size());
            amountSpent = amountSpent.add(w.moneySpent);
        }
        for (Wallet w : TxApp.outsideWallets) {
            if (Objects.equals(w.amount, BigDecimal.ZERO)) continue;
            System.out.println("-".repeat(20));
            System.out.println("Outside-" + w.getCurrencyType());
            System.out.println("Amount: " + w.amount);
            System.out.println("Transactions: " + w.transactions.size());
        }
        System.out.println("-".repeat(20));
        System.out.println("Amount total spent: " + amountSpent);
    }

    public String getCurrencyType() {
        return currencyType;
    }

    /**
     * Add a transaction to the Wallet
     *
     * @param amount the amount of the coin
     * @param nativeAmount the amount in native currency
     * @param amountBonus the amount the user got for free
     */
    public void addToWallet(BigDecimal amount, BigDecimal nativeAmount, BigDecimal amountBonus) {
        this.amount = this.amount.add(amount);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = this.amountBonus.add(amountBonus);
    }


    /**
     * Remove a transaction from the Wallet
     *
     * @param amount the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount) {
        this.amount = this.amount.subtract(amount);
        this.moneySpent = this.moneySpent.subtract(nativeAmount);
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }


    /**
     * Adds a transactions to the respective Wallet
     *
     * @param transaction the transaction to be added
     */
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        TransactionType t = transaction.getTransactionType();
        Wallet w = TxApp.wallets.get(Wallet.getWallet(transaction.getCurrencyType()));
        if (!w.transactions.contains(transaction)) {
            w.transactions.add(transaction);
        }
        switch (t) {
            case crypto_purchase -> {
                w.addToWallet(transaction.getAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
            }
            case supercharger_deposit -> {
                //do nothing
            }
            case rewards_platform_deposit_credited -> {
                //do nothing
            }
            case supercharger_reward_to_app_credited -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case viban_purchase -> {
                Wallet wv = TxApp.wallets.get(Wallet.getWallet(transaction.getToCurrency()));
                wv.addToWallet(transaction.getToAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
            }
            case crypto_earn_program_created -> {
            }
            case crypto_earn_interest_paid -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case supercharger_withdrawal -> {
            }
            case lockup_lock -> {
            }
            case crypto_withdrawal -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO);
                Wallet wt = TxApp.outsideWallets.get(Wallet.getWallet(transaction.getCurrencyType()));
                if (!wt.transactions.contains(transaction)) {
                    wt.transactions.add(transaction);
                }
                wt.removeFromWallet(transaction.getAmount(), BigDecimal.ZERO);
            }
            case referral_card_cashback -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case reimbursement -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case card_cashback_reverted -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case crypto_earn_program_withdrawn -> {
            }
            case admin_wallet_credited -> {
                //Free money from fork
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case crypto_wallet_swap_credited -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case crypto_wallet_swap_debited -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
            }
            case crypto_deposit -> {
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO);
                Wallet wt = TxApp.wallets.get(Wallet.getWallet(transaction.getCurrencyType()));
                if (!wt.transactions.contains(transaction)) {
                    wt.transactions.add(transaction);
                }
                wt.removeFromWallet(transaction.getAmount(), BigDecimal.ZERO);
            }
            case dust_conversion_debited -> {
                System.out.println(transaction);
            }
            case dust_conversion_credited -> {
                System.out.println(transaction);
            }
            default -> System.out.println("This is an unsupported TransactionType: " + t);
        }
    }

    public void setCurrencyType(String currencyType) {
        this.currencyType = currencyType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmountBonus() {
        return amountBonus;
    }

    public void setAmountBonus(BigDecimal amountBonus) {
        this.amountBonus = amountBonus;
    }

    public BigDecimal getMoneySpent() {
        return moneySpent;
    }

    public void setMoneySpent(BigDecimal moneySpent) {
        this.moneySpent = moneySpent;
    }

    public void setTransactions(ArrayList<Transaction> transactions) {
        this.transactions = transactions;
    }
}
