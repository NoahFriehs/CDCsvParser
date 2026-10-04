package at.msd.friehs_bicha.cdcsvparser.price

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Typed Retrofit clients for the two price APIs (Phase 3.4).
 *
 * Replaces the hand-built OkHttp request/response code while keeping the
 * exact endpoint shapes:
 *
 * - CryptoCompare `data/price?fsym=..&tsyms=EUR` (keyless public tier; the
 *   free endpoint now 401s, which the circuit breaker in
 *   [CryptoPricesCryptoCompare] turns into a CoinGecko fallback).
 * - CoinGecko `simple/price` (bulk, ids as a comma list) + `search`
 *   (symbol -> id resolution).
 *
 * One shared [OkHttpClient] carries the existing timeouts (10 s
 * connect/write, 15 s read, 30 s call) - no new network behavior, only a
 * typed surface.
 */
object PriceApi {
    private const val CC_BASE = "https://min-api.cryptocompare.com/data/"
    private const val CG_BASE = "https://api.coingecko.com/api/v3/"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private fun retrofitOf(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** CryptoCompare keyless price endpoint. */
    private val ccRetrofit: Retrofit = retrofitOf(CC_BASE)
    private val cgRetrofit: Retrofit = retrofitOf(CG_BASE)

    val cryptoCompare: CryptoCompareApi = ccRetrofit.create(CryptoCompareApi::class.java)
    val coinGecko: CoinGeckoApi = cgRetrofit.create(CoinGeckoApi::class.java)

    // ------------------------------------------------------------------
    // Endpoint interfaces + stable DTOs
    // ------------------------------------------------------------------

    /**
     * CryptoCompare `data/price`.
     *
     * Success: `{"EUR": 42000.5}` - the symbol is not in the catalog:
     * `{"Data": "-1"}` (kept as [Any?] so both shapes parse).
     */
    interface CryptoCompareApi {
        @GET("price")
        fun price(
            @Query("fsym") fsym: String,
            @Query("tsyms") tsyms: String,
        ): Call<CcPriceResponse>
    }

    @Serializable
    data class CcPriceResponse(
        val EUR: Double? = null,
        val Data: JsonElement? = null,
    )

    interface CoinGeckoApi {
        /**
         * One bulk price call: `simple/price?ids=a,b&vs_currencies=eur` ->
         * `{"a":{"eur":1.0},"b":{"eur":2.0}}` or `{"error":"..."}`.
         */
        @GET("simple/price")
        fun simplePrice(
            @Query("ids") ids: String,
            @Query("vs_currencies") vsCurrencies: String,
        ): Call<JsonElement>

        /**
         * Catalog search: `search?query=BTC` -> `{"coins":[{"id":"bitcoin",
         * "symbol":"BTC", ...}]}`.
         */
        @GET("search")
        fun search(@Query("query") query: String): Call<CoinGeckoSearchResponse>
    }

    @Serializable
    data class CoinGeckoSearchCoin(
        val id: String,
        val symbol: String,
    )

    @Serializable
    data class CoinGeckoSearchResponse(
        val coins: List<CoinGeckoSearchCoin> = emptyList(),
    )
}

/** Convenience re-exports so call sites read `PriceApi.coinGecko.simplePrice(...)`. */
typealias CcPriceResponse = PriceApi.CcPriceResponse
typealias CoinGeckoSearchCoin = PriceApi.CoinGeckoSearchCoin
typealias CoinGeckoSearchResponse = PriceApi.CoinGeckoSearchResponse
