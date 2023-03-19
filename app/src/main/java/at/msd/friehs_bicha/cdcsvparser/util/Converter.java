package at.msd.friehs_bicha.cdcsvparser.util;

import androidx.room.TypeConverter;

import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helper-class to convert types
 *
 */
public class Converter {

    /**
     * Converts a String to a TransactionType
     *
     * @param s the String to be converted
     * @return the TransactionType
     */
    public static TransactionType ttConverter(String s) {

        s = s.trim().toLowerCase();
        try {
            return TransactionType.valueOf(s);
        } catch (Exception e) {
            System.out.println(s);
            throw new IllegalArgumentException("Please give a correct TransactionType");
//            return null;
        }

    }


    /**
     * Converts a String to a Date
     *
     * @param s the String to be converted
     * @return the Date of the String
     */
    public static Date dateConverter(String s) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(s);
        } catch (Exception e) {
            System.out.println(s);
            return null;
        }
    }


    /**
     * Converts a Date to a String
     *
     * @param s the Date to be converted
     * @return the String of the Date
     */
    public static String stringToDateConverter(Date s) {
        try {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.format(s);
        } catch (Exception e) {
            System.out.println(s);
            return null;
        }
    }


    /**
     * Converts a BigDecimal to a Double
     */
    @TypeConverter
    public static Double bdToDouble(BigDecimal bd) {
        return bd.doubleValue();
    }


    /**
     * Converts a BigDecimal to a String
     * @param value the BigDecimal to be converted
     * @return the String of the BigDecimal
     */
    @TypeConverter
    public static String toString(BigDecimal value) {
        return value == null ? null : value.toString();
    }


    /**
     * Converts a String to a BigDecimal
     * @param value the String to be converted
     * @return the BigDecimal of the String
     */
    @TypeConverter
    public static BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }


    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

}
