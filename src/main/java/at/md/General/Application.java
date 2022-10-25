package at.md.General;

import at.md.Transactions.CardTX;
import at.md.Transactions.Transaction;
import at.md.Transactions.TransactionType;
import at.md.Util.Converter;
import at.md.Util.CurrencyType;
import at.md.Wallet.CardWallet;
import at.md.Wallet.Wallet;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

import static at.md.Util.Converter.stringToDateConverter;
import static at.md.Util.Converter.ttConverter;

public class Application {

    public static final Scanner scanner = new Scanner(System.in);

    static String datapath = null;

    public static void main(String[] args) {

        while (true) {
            switch (userInterface()) {
                case 0 -> {
                    return;
                }

                case 1 -> {
                    TxApp.main(new String[]{datapath});
                    userInterfaceTxApp();
                }

                case 2 -> {
                    CardTxApp.main(new String[]{datapath});
                    userInterfaceCardTxApp();
                }
            }

        }
    }

    private static String getDatapath() {

        System.out.println("Enter path of .csv file");
        String input;
        try {
            input = scanner.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid input");
            return null;
        }

        if (new File(input).exists())
            if (new File(input).isFile())
                return input;
        System.out.println("Invalid input");
        return null;

    }


    /**
     * Let the user enter 1 number.
     *
     * @return the entered number.
     */
    static BigDecimal readNumber(String usage) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        String pattern = "#,##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setParseBigDecimal(true);
        while (true) {
            System.out.print(usage); //usage
            BigDecimal value;
            try {
                value = (BigDecimal) decimalFormat.parse(scanner.nextLine());
            } catch (Exception e) {
                System.out.println("Invalid input");
                continue;
            }
            return value;
        }
    }


    static int userInterface() {

        while (datapath == null) {
            datapath = getDatapath();
        }
        System.out.println("Enter 0 to exit");
        System.out.println("Enter 1 to access wallets");
        System.out.println("Enter 2 to access cards");
        System.out.println("Enter 3 to enter a new path of .csv file");
        while (true) {
            int input = readNumber("").intValue();
            if (input ==  3){
                datapath = null;
                while (datapath == null) datapath = getDatapath();
            }

            if (input < 0 || input > 3)
                System.out.println("Invalid input");
            else return input;

        }
    }

    static void userInterfaceCardTxApp() {
        userInstructionsCardTxApp();
        while (true) {
            System.out.print(">");
            int value = readNumber("").intValue();
            if (value < 0 || (value > 4 && value != 9)) {
                System.out.println("Invalid input");
                continue;
            }

            switch (value) {
                case 0 -> {
                    return;
                }

                case 1 -> {
                    System.out.println("Enter the type you want to display: ");
                    String s = scanner.nextLine();
                    try {
                        for (CardWallet w : CardTxApp.cardWallets) {
                            if (w.getTransactionType().contains(s)) {
                                for (CardTX tx : w.getTxs())
                                    System.out.println(tx.toString());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Invalid Input");

                    }
                }

                case 2 -> {
                    BigDecimal bd = readNumber("Enter the transaction amount: ");
                    for (CardTX t : CardTxApp.transactions) {
                        if (Objects.equals(t.getAmount(), bd)) {
                            System.out.println(t);
                        }
                    }
                }

                case 3 -> {
                    System.out.print("Enter the year (yyyy): ");
                    int year = readNumber("").intValue();
                    ArrayList<CardTX> rightTX = new ArrayList<>();
                    ArrayList<CardTX> rightMonthTX = new ArrayList<>();
                    ArrayList<CardTX> rightDayTX = new ArrayList<>();
                    int txPerYear = 0;
                    int txPerMonth = 0;
                    int txPerDay = 0;
                    for (CardTX t : CardTxApp.transactions) {
                        if (Integer.parseInt(Converter.stringToDateConverter(t.getDate()).substring(0, 4)) == year) {
                            txPerYear++;
                            rightTX.add(t);
                        }
                    }
                    System.out.print("Press 0 to view " + txPerYear + " transaction(s) or enter month (MM): ");

                    int month = readNumber("").intValue();

                    if (month == 0) {
                        for (CardTX t : rightTX) {
                            System.out.println(t.toString());
                        }
                        break;
                    } else {

                        for (CardTX t : rightTX) {
                            if (Integer.parseInt(stringToDateConverter(t.getDate()).substring(5, 7)) == month) {
                                txPerMonth++;
                                rightMonthTX.add(t);
                            }
                        }
                    }

                    System.out.print("Press 0 to view " + txPerMonth + " transaction(s) or enter day");
                    int day = readNumber("").intValue();

                    if (day == 0) {
                        for (CardTX t : rightMonthTX) {
                            System.out.println(t.toString());
                        }
                    } else {

                        for (CardTX t : rightMonthTX) {
                            if (Integer.parseInt(stringToDateConverter(t.getDate()).substring(8, 10)) == day) {
                                txPerDay++;
                                rightDayTX.add(t);
                            }
                        }
                        System.out.println("" + txPerDay + " transaction(s)");
                        for (CardTX t : rightDayTX) {
                            System.out.println(t.toString());
                        }
                    }
                }

                case 4 -> {
                    CardWallet.writeAmount();
                }

                case 9 -> {
                    userInstructionsCardTxApp();
                }
            }
        }
    }

    static void userInstructionsCardTxApp() {

        System.out.println("Press 0 to exit");
        System.out.println("Press 1 to get all transactions from 1 type");
        System.out.println("Press 2 to get transaction by amount");
        System.out.println("Press 3 to get transaction by date");
        System.out.println("Press 4 to get all different transactions with amount");
        System.out.println("Press 9 for help");

    }

    static void userInterfaceTxApp() {
        userInstructionsTxApp();
        boolean continued = true;
        while (continued) {
            int value = readNumber("").intValue();
            if (value < 0 || (value > 8 && value != 9)) {
                System.out.println("Invalid input");
                continue;
            }
            switch (value) {
                case 0 -> continued = false;
                case 1 -> {
                    System.out.println("Enter the Wallet you want to use: ");
                    String s = scanner.nextLine();
                    try {
                        if (!CurrencyType.currencys.contains(s)) continue;
                        for (Wallet w : TxApp.wallets) {
                            if (w.getCurrencyType().equals(s)) {
                                for (Transaction tx : w.getTransactions())
                                    System.out.println(tx.toString());
                            }
                        }
                        for (Wallet w : TxApp.outsideWallets) {
                            if (w.getCurrencyType().equals(s)) {
                                for (Transaction tx : w.getTransactions())
                                    System.out.println(tx.toString());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Invalid Input");

                    }
                }
                case 2 -> {
                    System.out.println("Enter the transaction type: ");
                    String s = scanner.nextLine();
                    try {
                        TransactionType tt = ttConverter(s);
                        for (Transaction t : TxApp.transactions) {
                            if (t.getTransactionType().equals(tt)) {
                                System.out.println(t);
                            }
                        }

                    } catch (Exception e) {
                        System.out.println("Invalid Input");
                    }
                }
                case 3 -> {
                    BigDecimal bd = readNumber("Enter the transaction amount: ");
                    for (Transaction t : TxApp.transactions) {
                        if (Objects.equals(t.getAmount(), bd)) {
                            System.out.println(t);
                        }
                    }
                }
                case 4 -> {
                    BigDecimal bd = readNumber("Enter the native amount: ");
                    for (Transaction t : TxApp.transactions) {
                        if (Objects.equals(t.getNativeAmount(), bd)) {
                            System.out.println(t);
                        }
                    }
                }
                case 5 -> {
                    System.out.println("Enter the transaction hash: ");
                    String s = scanner.nextLine();
                    for (Transaction t : TxApp.transactions) {
                        if (Objects.equals(t.getTransHash(), s)) {
                            System.out.println(t);
                        }
                    }
                }
                case 6 -> {
                    System.out.print("Enter the year (yyyy): ");
                    int year = readNumber("").intValue();
                    ArrayList<Transaction> rightTX = new ArrayList<>();
                    ArrayList<Transaction> rightMonthTX = new ArrayList<>();
                    ArrayList<Transaction> rightDayTX = new ArrayList<>();
                    int txPerYear = 0;
                    int txPerMonth = 0;
                    int txPerDay = 0;
                    for (Transaction t : TxApp.transactions) {
                        if (Integer.parseInt(Converter.stringToDateConverter(t.getDate()).substring(0, 4)) == year) {
                            txPerYear++;
                            rightTX.add(t);
                        }
                    }
                    System.out.print("Press 0 to view " + txPerYear + " transaction(s) or enter month (MM): ");

                    int month = readNumber("").intValue();

                    if (month == 0) {
                        for (Transaction t : rightTX) {
                            System.out.println(t.toString());
                        }
                        break;
                    } else {

                        for (Transaction t : rightTX) {
                            if (Integer.parseInt(stringToDateConverter(t.getDate()).substring(5, 7)) == month) {
                                txPerMonth++;
                                rightMonthTX.add(t);
                            }
                        }
                    }

                    System.out.print("Press 0 to view " + txPerMonth + " transaction(s) or enter day");
                    int day = readNumber("").intValue();

                    if (day == 0) {
                        for (Transaction t : rightMonthTX) {
                            System.out.println(t.toString());
                        }
                    } else {

                        for (Transaction t : rightMonthTX) {
                            if (Integer.parseInt(stringToDateConverter(t.getDate()).substring(8, 10)) == day) {
                                txPerDay++;
                                rightDayTX.add(t);
                            }
                        }
                        System.out.println("" + txPerDay + " transaction(s)");
                        for (Transaction t : rightDayTX) {
                            System.out.println(t.toString());
                        }
                    }
                }

                case 7 -> Wallet.writeAmount();

                case 8 -> {
                    System.out.println("Enter the outside Wallet you want to inspect: ");
                    String s = scanner.nextLine();
                    try {
                        if (!CurrencyType.currencys.contains(s)) continue;
                        for (Wallet w : TxApp.outsideWallets) {
                            if (w.getCurrencyType().equals(s)) {
                                for (Transaction tx : w.getTransactions())
                                    System.out.println(tx.toString());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Invalid Input");

                    }
                }

                case 9 -> userInstructionsTxApp();
            }

        }

    }


    static void userInstructionsTxApp() {
        System.out.println("Press 0 to exit");
        System.out.println("Press 1 to get all transactions from 1 wallet");
        System.out.println("Press 2 to get transactions by transaction type");
        System.out.println("Press 3 to get transaction by amount");
        System.out.println("Press 4 to get transaction by native amount");
        System.out.println("Press 5 to get transaction by transaction hash");
        System.out.println("Press 6 to get transaction by date");
        System.out.println("Press 7 to get all wallets");
        System.out.println("Press 8 to get outside Wallet by name");
        System.out.println("Press 9 for help");
    }
}
