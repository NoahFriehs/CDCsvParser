package at.msd.friehs_bicha.cdcsvparser.price

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.BuildConfig
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper

/**
 * Plan 004: price API key resolution. Precedence (one channel each):
 *
 * 1. the runtime key the user entered in Settings (DataStore),
 * 2. the build-time key - DEBUG builds pick it up from the gitignored
 *    root `local.properties` (`coingecko_api_key=` / `cryptocompare_api_key=`),
 *    RELEASE builds always carry `""`,
 * 3. keyless (both providers have a keyless mode).
 *
 * Blank/whitespace runtime values count as unset. Keys are only ever sent
 * to `*.coingecko.com` (header `x-cg-demo-api-key`) and
 * `min-api.cryptocompare.com` (query `api_key`) - never to any other host
 * and never to any log.
 */
object PriceApiKey {

    /** Pure precedence function (JVM-tested). */
    fun resolve(runtime: String, buildTime: String): String =
        if (runtime.isNotBlank()) runtime.trim() else buildTime

    /** Effective CoinGecko key ("" = keyless). */
    fun coinGeckoKey(context: Context): String =
        resolve(PreferenceHelper.getCoinGeckoApiKey(context), BuildConfig.CG_API_KEY)

    /** Effective CryptoCompare key ("" = no deep history source). */
    fun cryptoCompareKey(context: Context): String =
        resolve(PreferenceHelper.getCryptoCompareApiKey(context), BuildConfig.CC_API_KEY)
}
