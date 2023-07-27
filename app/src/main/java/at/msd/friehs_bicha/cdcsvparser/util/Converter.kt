package at.msd.friehs_bicha.cdcsvparser.util

import android.annotation.SuppressLint
import androidx.room.TypeConverter
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import java.math.BigDecimal
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

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
        s = s.trim { it <= ' ' }.lowercase()
        return try {
            TransactionType.valueOf(s)
        } catch (e: Exception) {
            FileLog.w("Converter", "ttConverter: $s | ${e.message}")
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
    @SuppressLint("SimpleDateFormat")
    fun dateConverter(s: String?): Date? {
        if (s == null)
        {
            FileLog.w("Converter", "dateConverter: String is null")
            return null
        }
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            sdf.parse(s)
        } catch (e: Exception) {
            return try {
                val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")
                sdf.parse(s)
            } catch (e1: Exception) {
                return try {
                    val sdf = SimpleDateFormat("yyyy-mm-dd")
                    sdf.parse(s)
                } catch (e2: Exception) {
                    FileLog.w("Converter:dateConverter", "error while trying to parse $s | ${e2.message}")
                    null
                }
            }
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
            FileLog.w("Converter", "stringToDateConverter: $s | ${e.message}")
            null
        }
    }


    class BigDecimalConverter {
        @TypeConverter
        fun fromBigDecimal(value: BigDecimal?): String? {
            return value?.toString()
        }

        @TypeConverter
        fun toBigDecimal(value: String?): BigDecimal? {
            return value?.let { BigDecimal(it) }
        }
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

    fun amountConverter(s: String): BigDecimal? {
        return try {
            BigDecimal(s)
        } catch (e: Exception) {
            FileLog.w("Converter", "amountConverter: $s | ${e.message}")
            null
        }
    }
}