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

    /**
     * Returns the prices of the entered symbols.
     *
     * The default implementation calls [getPrice] for every symbol.
     * Providers that can answer many symbols with less work
     * (e.g. one bulk API call) should override this.
     *
     * @param symbols the symbols for which prices are needed
     * @return a map from symbol to price; a missing key means no price was
     *         found, an entry of 0.0 means the symbol is known but has no value
     */
    open fun getPricesBulk(symbols: List<String>): Map<String, Double> {
        val prices = LinkedHashMap<String, Double>()
        for (symbol in symbols) {
            getPrice(symbol)?.let { price -> prices[symbol] = price }
        }
        return prices
    }

}