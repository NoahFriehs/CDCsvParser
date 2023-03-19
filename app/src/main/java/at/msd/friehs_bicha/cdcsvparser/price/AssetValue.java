package at.msd.friehs_bicha.cdcsvparser.price;

import static java.lang.Thread.sleep;

import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.constant.Currency;
import com.litesoftwares.coingecko.domain.Coins.CoinFullData;
import com.litesoftwares.coingecko.domain.Coins.CoinList;
import com.litesoftwares.coingecko.domain.Coins.CoinMarkets;
import com.litesoftwares.coingecko.domain.Coins.MarketData;
import com.litesoftwares.coingecko.impl.CoinGeckoApiClientImpl;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class AssetValue implements Serializable {

    final List<PriceCache> cache;
    public boolean isRunning;
    List<CoinList> coinLists;
    List<CoinMarkets> coinMarkets;
    private Instant coinMarketsCreationTime;
    private int tries = 0;

    public AssetValue() {
        cache = new ArrayList<>();
        isRunning = true;
    }

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol
     */
    public Double getPrice(String symbol) throws InterruptedException {
        symbol = overrideSymbol(symbol);
        double price = checkCache(symbol);
        if (price != -1.0) {
            isRunning = true;
            return price;
        }
        try {
            CoinGeckoApiClient client = new CoinGeckoApiClientImpl();
            //client.ping();
            if (coinMarkets == null || Instant.now().isAfter(coinMarketsCreationTime.plusSeconds(300))) {
                coinMarkets = client.getCoinMarkets(Currency.EUR);
                this.coinMarketsCreationTime = Instant.now();
            }
            try {
                //if (coinMarkets == null) coinMarkets = client.getCoinMarkets(Currency.EUR);
                for (CoinMarkets coinMarket : coinMarkets) {
                    if (coinMarket.getSymbol().contains(symbol.toLowerCase()) || coinMarket.getId().contains(symbol.toLowerCase())) {
                        cache.add(new PriceCache(symbol, coinMarket.getCurrentPrice()));
                        return coinMarket.getCurrentPrice();
                    }
                }
            } catch (Exception e) {
                System.out.println("|" + e.getMessage());
            }
            isRunning = true;
            return getPriceTheOtherWay(symbol);
        } catch (Exception e) {
            if (e.getMessage().contains("com.litesoftwares.coingecko.exception.CoinGeckoApiException: CoinGeckoApiError(code=1015, message=Rate limited)")) {
                sleep(1000);
                return getPrice(symbol);
            }
            if (tries < 3) {
                tries++;
                sleep(1000);
                return getPrice(symbol);
            }
            isRunning = false;
            tries = 0;
            return 0.0;
        }
    }

    /**
     * Returns the price of the entered symbol in a more complicate way
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol
     */
    private Double getPriceTheOtherWay(String symbol) {
        CoinGeckoApiClient client = new CoinGeckoApiClientImpl();
        if (coinLists == null) coinLists = client.getCoinList();

        for (CoinList coinList : coinLists) {
            if (coinList.getSymbol().contains(symbol.toLowerCase()) || coinList.getId().contains(symbol.toLowerCase()) || coinList.getName().contains(symbol.toLowerCase())) {
                CoinFullData bitcoinInfo = client.getCoinById(coinList.getId());
                MarketData data = bitcoinInfo.getMarketData();
                Map<String, Double> dataPrice = data.getCurrentPrice();
                cache.add(new PriceCache(symbol, dataPrice.get("eur")));
                return dataPrice.get("eur");
            }
        }

        System.out.println("No price found for: " + symbol);

        return (double) 0;
    }

    /**
     * Replaces symbols with the right ones
     *
     * @param symbol the symbol to be checked and if needed replaced
     * @return the if needed replaced symbol
     */
    private String overrideSymbol(String symbol) {
        if (Objects.equals(symbol, "LUNA")) return "terra-luna";
        if (symbol.equals("LUNA2")) return "terra-luna-2";
        return symbol;
    }

    /**
     * Checks if the price of the symbol is stored
     *
     * @param symbol the symbol to be checked for
     * @return a price if it has it it else -1
     */
    private double checkCache(String symbol) {

        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).isOlderThanFiveMinutes()) {
                cache.remove(i);
                i--;
                continue;
            }
            if (cache.get(i).id.equals(symbol)) {
                System.out.println("used cache");
                return cache.get(i).price;
            }
        }

        return -1;
    }

}
