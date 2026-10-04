package at.msd.friehs_bicha.cdcsvparser.app

/**
 * App type enum
 */
/**
 * App type enum
 *
 * Ordinals are persisted and passed to the service intent: only append new
 * constants here, never reorder or insert.
 */
enum class AppType {
    CdCsvParser, CroCard, CurveCard, Default, Custom, Kraken, BlockPit;


    companion object {
        fun fromOrdinal(mode: Int): AppType {
            return values()[mode]
        }

        /**
         * Like [fromOrdinal] but never throws: out-of-range ordinals (e.g. from
         * persisted data written by a different version of the app) fall back
         * to [CdCsvParser].
         */
        fun safeFromOrdinal(mode: Int): AppType {
            return values().getOrElse(mode) { CdCsvParser }
        }

        /**
         * Returns the enum constant with the given name, or null if there is
         * none (instead of throwing, for untrusted persisted names).
         */
        fun safeFromName(name: String): AppType? {
            return values().firstOrNull { it.name == name }
        }
    }
}