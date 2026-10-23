package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.core.AssetEntry
import at.msd.friehs_bicha.cdcsvparser.core.ParseData
import at.msd.friehs_bicha.cdcsvparser.core.WalletAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed wallet display state (replacement of the R.id-keyed maps).
 * The producers live on Android (JNI / price cache); what is pure and
 * contract-relevant - the two field layouts and the no-prices token - is
 * tested here. Locale-dependent format strings are intentionally NOT
 * asserted (StringHelper's DecimalFormat follows the host locale).
 */
class ViewModelsTest {

    @Test
    fun defaultIsAllRowsHidden() {
        // The all-null default replaces the old "missing key" / "null
        // value" contract: every ValueBlock hides its row.
        val empty = WalletAssets()
        assertNull(empty.moneySpent)
        assertNull(empty.assets)
        assertNull(empty.rewards)
        assertNull(empty.profitLoss)
    }

    @Test
    fun cardLayoutCarriesOnlyMoneySpent() {
        // Card buckets are fiat: only "money spent" exists, the other
        // three rows stay hidden (the old card map posted explicit nulls).
        val card = WalletAssets.card("12.50 €")
        assertEquals("12.50 €", card.moneySpent)
        assertNull(card.assets)
        assertNull(card.rewards)
        assertNull(card.profitLoss)
    }

    @Test
    fun standardLayoutCarriesAllFourWhenPricesRunning() {
        val std = WalletAssets.standard(
            moneySpentText = "100.00 €",
            assetsText = "150.00 €",
            rewardsText = "2.00 €",
            profitLossText = "50.00 €",
            pricesRunning = true,
        )
        assertEquals("100.00 €", std.moneySpent)
        assertEquals("150.00 €", std.assets)
        assertEquals("2.00 €", std.rewards)
        assertEquals("50.00 €", std.profitLoss)
        // No token leakage when prices are running.
        assertFalse(std.assets!!.contains(WalletAssets.NO_INTERNET))
    }

    @Test
    fun standardLayoutUsesNoInternetTokenWhilePricesMissing() {
        // The three derived values carry the transport token until the
        // price lookup is running; "money spent" (a core value, no price
        // needed) is still shown. The token is mapped to the localized
        // R.string.no_internet_connection by the screen.
        val std = WalletAssets.standard(
            moneySpentText = "100.00 €",
            assetsText = "150.00 €",
            rewardsText = "2.00 €",
            profitLossText = "50.00 €",
            pricesRunning = false,
        )
        assertEquals("100.00 €", std.moneySpent)
        assertEquals(WalletAssets.NO_INTERNET, std.assets)
        assertEquals(WalletAssets.NO_INTERNET, std.rewards)
        assertEquals(WalletAssets.NO_INTERNET, std.profitLoss)
    }

    @Test
    fun assetEntryKeepsWalletIdWithItsState() {
        val entry = AssetEntry(walletId = 7, assets = WalletAssets.card("1.00 €"))
        assertEquals(7, entry.walletId)
        assertEquals("1.00 €", entry.assets.moneySpent)
        assertTrue(entry.assets.assets == null)
    }

    @Test
    fun parseDataCardLayoutOnlyMoneySpentFooterHidden() {
        // A file without crypto (card/fiat): only "money spent" exists
        // and the attribution footer is hidden (replaces the old explicit
        // null under the coinGeckoApiLabel key).
        val data = ParseData.card("-42,00 €")
        assertEquals("-42,00 €", data.moneySpent)
        assertNull(data.assets)
        assertNull(data.rewards)
        assertNull(data.profitLoss)
        assertFalse(data.attributionVisible)
    }

    @Test
    fun parseDataStandardLayoutAllFieldsFooterUp() {
        val data = ParseData.standard(
            moneySpentText = "1.000,00 €",
            assetsText = "2.500,00 €",
            rewardsText = "10,00 €",
            profitLossText = "-250,00 €",
        )
        assertEquals("1.000,00 €", data.moneySpent)
        assertEquals("2.500,00 €", data.assets)
        assertEquals("10,00 €", data.rewards)
        assertEquals("-250,00 €", data.profitLoss)
        assertTrue(data.attributionVisible)
    }

    @Test
    fun parseDataDefaultAllFieldsNullFooterUp() {
        val data = ParseData()
        assertNull(data.assets)
        assertNull(data.moneySpent)
        assertNull(data.rewards)
        assertNull(data.profitLoss)
        assertTrue(data.attributionVisible)
    }
}
