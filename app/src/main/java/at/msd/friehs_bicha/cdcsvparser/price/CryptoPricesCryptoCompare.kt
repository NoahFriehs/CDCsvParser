package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class CryptoPricesCryptoCompare : BaseCryptoPrices() {
    private val client = OkHttpClient()
    private val baseUrl = "https://min-api.cryptocompare.com/data/"

    override fun getPrice(symbol: String): Double? {
        return try {
            val url = "${baseUrl}price?fsym=$symbol&tsyms=EUR"
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string()
            val jsonObject = JSONObject(json)
            val price = jsonObject.getDouble("EUR")
            price
        } catch (e: Exception) {
            FileLog.d("CryptoCompare", "Failed to get price for $symbol, error: $e")
            if (e.message?.contains("No value for") == true) {
                return 0.0
            }
            null
        }
    }
}