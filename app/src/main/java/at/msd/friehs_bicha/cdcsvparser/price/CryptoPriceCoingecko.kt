package at.msd.friehs_bicha.cdcsvparser.price

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*

class CryptoPriceCoingecko : BaseCryptoPrices() {
    private val client = OkHttpClient()
    private val baseUrl = "https://api.coingecko.com/api/v3/"

    override fun getPrice(symbol: String): Double? {
        val url = "${baseUrl}simple/price?ids=$symbol&vs_currencies=eur"
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        val json = response.body?.string()
        val jsonObject = JSONObject(json)
        val priceObject = jsonObject.getJSONObject(symbol.lowercase(Locale.getDefault()))
        return priceObject.getDouble("eur")
    }
}
