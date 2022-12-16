package at.msd.friehs_bicha.cdcsvparser.price;

import java.io.Serializable;
import java.time.Instant;

public class PriceCache implements Serializable {

    private final Instant creationTime;

    public String id;

    public double price;

    public PriceCache(String id, double price) {
        this.creationTime = Instant.now();
        this.id = id;
        this.price = price;
    }

    /**
     * Checks if the object is older than five minutes
     *
     * @return true if it is older than five minutes
     */
    public boolean isOlderThanFiveMinutes() {
        return Instant.now().isAfter(creationTime.plusSeconds(300));
    }

}
