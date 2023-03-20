package at.msd.friehs_bicha.cdcsvparser.price

import java.io.Serializable
import java.time.Instant

/**
 * Object to store price for a specific token for 5 mins
 */
class PriceCache(id: String?, price: Double) : Serializable {
    val id: String?
    val price: Double
    private val creationTime: Instant

    init {
        creationTime = Instant.now()
        this.id = id
        this.price = price
    }

    /**
     * Checks if the object is older than five minutes
     *
     * @return true if it is older than five minutes
     */
    val isOlderThanFiveMinutes: Boolean
        get() = Instant.now().isAfter(creationTime.plusSeconds(300))
}