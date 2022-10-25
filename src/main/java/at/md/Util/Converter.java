package at.md.Util;

import at.md.Transactions.TransactionType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Converter {

    public static TransactionType ttConverter(String s) {

        s = s.trim().toLowerCase();
        try {
            return TransactionType.valueOf(s);
        } catch (Exception e) {
            System.out.println(s);
            return null;
        }

    }


//    public static CurrencyType ctConverter(String s) {
//        s = s.trim().toUpperCase();
//        try {
//            return CurrencyType.valueOf(s);
//        } catch (Exception e) {
//            System.out.println(s);
//            return null;
//        }
//    }


    public static Date dateConverter(String s) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(s);
        } catch (Exception e) {
            System.out.println(s);
            return null;
        }
    }

    public static String stringToDateConverter(Date s) {
        try {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.format(s);
        } catch (Exception e) {
            System.out.println(s);
            return null;
        }
    }

}
