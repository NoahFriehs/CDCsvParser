package at.msd.friehs_bicha.cdcsvparser.core

import at.msd.friehs_bicha.cdcsvparser.app.AppType

/**
 * Maps an app-facing [AppType] to the mode integer expected by the C++ core
 * (`enum Mode` in cpp/Enums.h: 0 = CDC, 1 = Card, 2 = Default, 3 = Custom, 4 = Kraken).
 *
 * Returns null for types the C++ core cannot parse. Callers must surface a
 * user-facing error instead of passing an invalid mode to the native code.
 */
object CoreModeMapper {

    fun toCoreMode(appType: AppType): Int? = when (appType) {
        AppType.CdCsvParser -> 0 // CDC
        AppType.CroCard -> 1     // Card
        AppType.Kraken -> 4      // Kraken

        // Not implemented in the C++ core - never pass these through.
        AppType.CurveCard -> null
        AppType.Default -> null
        AppType.Custom -> null
    }
}
