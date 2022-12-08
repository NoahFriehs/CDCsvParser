package at.msd.friehs_bicha.cdcsvparser.Price;

import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.constant.Currency;
import com.litesoftwares.coingecko.domain.Coins.*;
import com.litesoftwares.coingecko.impl.CoinGeckoApiClientImpl;

import java.util.List;


public class AssetValue {

    CoinGeckoApiClient client = new CoinGeckoApiClientImpl();

    Double getPrice(String symbol){
        List<CoinMarkets> coinMarkets = client.getCoinMarkets(Currency.EUR);
        Double btcPrice = coinMarkets.get(0).getCurrentPrice();
        for (CoinMarkets coinMarket :coinMarkets){
            if (coinMarket.getName().contains(symbol)){
                return coinMarket.getCurrentPrice();
            }
        }
        return null;
    }

}
