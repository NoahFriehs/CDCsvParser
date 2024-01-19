package at.msd.friehs_bicha.cdcsvparser.app

/**
 * App type enum
 */
enum class AppType {
    CdCsvParser, CroCard, CurveCard, Default, Custom;


    companion object {
        fun fromOrdinal(mode: Int): AppType {
            return values()[mode]
        }
    }
}