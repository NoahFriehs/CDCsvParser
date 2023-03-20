package at.msd.friehs_bicha.cdcsvparser.util

import androidx.room.TypeConverter
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import java.math.BigDecimal
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper-class to convert types
 */
object Converter {
    /**
     * Converts a String to a TransactionType
     *
     * @param s the String to be converted
     * @return the TransactionType
     */
    fun ttConverter(s: String): TransactionType {
        var s = s
        s = s.trim { it <= ' ' }.lowercase(Locale.getDefault())
        return try {
            TransactionType.valueOf(s)
        } catch (e: Exception) {
            println(s)
            throw IllegalArgumentException("Please give a correct TransactionType")
            //            return null;
        }
    }

    /**
     * Converts a String to a Date
     *
     * @param s the String to be converted
     * @return the Date of the String
     */
    fun dateConverter(s: String?): Date? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            sdf.parse(s)
        } catch (e: Exception) {
            println(s)
            null
        }
    }

    /**
     * Converts a Date to a String
     *
     * @param s the Date to be converted
     * @return the String of the Date
     */
    fun stringToDateConverter(s: Date?): String? {
        return try {
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")
            dateFormat.format(s)
        } catch (e: Exception) {
            println(s)
            null
        }
    }

    /**
     * Converts a BigDecimal to a Double
     */
    @TypeConverter
    fun bdToDouble(bd: BigDecimal): Double {
        return bd.toDouble()
    }

    /**
     * Converts a BigDecimal to a String
     *
     * @param value the BigDecimal to be converted
     * @return the String of the BigDecimal
     */
    @JvmStatic
    @TypeConverter
    fun toString(value: BigDecimal?): String? {
        return value?.toString()
    }

    /**
     * Converts a String to a BigDecimal
     *
     * @param value the String to be converted
     * @return the BigDecimal of the String
     */
    @JvmStatic
    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return if (value == null) null else BigDecimal(value)
    }

    @JvmStatic
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return if (value == null) null else Date(value)
    }

    @JvmStatic
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}