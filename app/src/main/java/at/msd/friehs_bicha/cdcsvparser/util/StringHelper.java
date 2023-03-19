package at.msd.friehs_bicha.cdcsvparser.util;

import java.text.DecimalFormat;

public class StringHelper {

    public static String formatAmountToString(Double amount) {
        DecimalFormat df = new DecimalFormat("#0.00");
        return df.format(amount) + "€";
    }

}
