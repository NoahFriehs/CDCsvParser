package at.msd.friehs_bicha.cdcsvparser.price

/**
 * Base class for crypto prices
 */
abstract class BaseCryptoPrices {

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol or null if no price was found or an error occurred
     */
    abstract fun getPrice(symbol: String): Double?

}