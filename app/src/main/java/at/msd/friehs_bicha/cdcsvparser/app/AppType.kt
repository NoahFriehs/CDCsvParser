package at.msd.friehs_bicha.cdcsvparser.app

/**
 * App type enum
 */
enum class AppType {
    CdCsvParser, CroCard, CurveCard, Default, Custom, Kraken;


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
    }
}