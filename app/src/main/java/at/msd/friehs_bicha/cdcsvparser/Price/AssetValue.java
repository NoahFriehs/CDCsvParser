package at.msd.friehs_bicha.cdcsvparser.Price;

import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.constant.Currency;
import com.litesoftwares.coingecko.domain.Coins.*;
import com.litesoftwares.coingecko.impl.CoinGeckoApiClientImpl;

import java.util.List;
import java.util.Map;


public class AssetValue {

    List<CoinList> coinLists;
    List<CoinMarkets> coinMarkets;

    public Double getPrice(String symbol){
        CoinGeckoApiClient client = new CoinGeckoApiClientImpl();
        try {
            if (coinMarkets == null) coinMarkets = client.getCoinMarkets(Currency.EUR);
            for (CoinMarkets coinMarket : coinMarkets){
                if (coinMarket.getSymbol().contains(symbol.toLowerCase()) || coinMarket.getId().contains(symbol.toLowerCase())){
                    return coinMarket.getCurrentPrice();
                }
            }
        }catch (Exception e){
            System.out.println("|" + e.getMessage());
        }

        return getPriceTheOtherWay(symbol);
    }

    private Double getPriceTheOtherWay(String symbol){
        CoinGeckoApiClient client = new CoinGeckoApiClientImpl();
        if (coinLists == null) coinLists = client.getCoinList();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (CoinList coinList : coinLists) {
            if (coinList.getSymbol().contains(symbol.toLowerCase())||coinList.getId().contains(symbol.toLowerCase())||coinList.getName().contains(symbol.toLowerCase())){
                CoinFullData bitcoinInfo = client.getCoinById(coinList.getId());
                MarketData btcData = bitcoinInfo.getMarketData();
                Map dataPrice = btcData.getCurrentPrice();
                return (Double) dataPrice.get("eur");
            }
        }

        System.out.println("No price found for: " + symbol);

        return (double) 0;
    }

}
