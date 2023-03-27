package at.msd.friehs_bicha.cdcsvparser.price

class StaticPrices{
    var prices : HashMap<String, Double> = HashMap()

    fun setPrices()
    {
        prices.put("DOGE", 0.07)
        prices.put("CUDOS", 0.002)
        prices.put("ETH", 1.300)
        prices.put("BTC", 16700.0)
        prices.put("CRO", 0.06)
        prices.put("EUR", 1.0)
        prices.put("ETHW", 0.0)
        prices.put("LUNA2", 1.46)
        prices.put("LUNC", 0.0002)
        prices.put("LUNA", 0.0)
        prices.put("ALGO", 0.20)
    }

}

