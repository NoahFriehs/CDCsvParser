package at.msd.friehs_bicha.cdcsvparser.price

abstract class BaseCryptoPrices {

    abstract fun getPrice(symbol: String): Double?

}