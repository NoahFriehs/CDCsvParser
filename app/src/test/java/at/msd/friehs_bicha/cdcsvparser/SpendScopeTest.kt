package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.core.ParseData
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SpendCard
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SpendScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mixed-model money-spent card: which scope the parse screen's one
 * number covers. Pins the [SpendCard.resolve] contract: the chip is a
 * MIXED-model surface only, the default keeps the number the card always
 * showed (the wallet scope in a crypto model, the card scope in a
 * card-only model), and a stale chip selection (the state outlived a
 * model change) falls back to the model's scope instead of rendering an
 * absent number.
 */
class SpendScopeTest {

    // ---- the chip visibility: mixed model only -------------------------

    @Test
    fun mixedModelPostsAllThreeScopeTextsChipVisible() {
        val spend = SpendCard.resolve("-85.00 €", "-72.00 €", "-157.00 €", null)
        assertTrue(spend.chipVisible)
    }

    @Test
    fun walletOnlyModelHasNoChip() {
        val spend = SpendCard.resolve("-85.00 €", null, null, null)
        assertFalse(spend.chipVisible)
    }

    @Test
    fun cardOnlyModelHasNoChip() {
        val spend = SpendCard.resolve(null, "-72.00 €", null, null)
        assertFalse(spend.chipVisible)
    }

    @Test
    fun nothingPostedHasNoChip() {
        val spend = SpendCard.resolve(null, null, null, null)
        assertFalse(spend.chipVisible)
    }

    // ---- the default scope (no selection yet) ---------------------------

    @Test
    fun mixedModelDefaultKeepsTheWalletScopeNumber() {
        val spend = SpendCard.resolve("-85.00 €", "-72.00 €", "-157.00 €", null)
        assertEquals(SpendScope.WALLET, spend.scope)
        assertEquals("-85.00 €", spend.text)
    }

    @Test
    fun walletOnlyModelDefaultIsTheWalletScope() {
        val spend = SpendCard.resolve("-85.00 €", null, null, null)
        assertEquals(SpendScope.WALLET, spend.scope)
        assertEquals("-85.00 €", spend.text)
    }

    @Test
    fun cardOnlyModelDefaultIsTheCardScope() {
        val spend = SpendCard.resolve(null, "-72.00 €", null, null)
        assertEquals(SpendScope.CARD, spend.scope)
        assertEquals("-72.00 €", spend.text)
    }

    @Test
    fun nothingPostedDefaultsToAWalletScopeWithNoNumber() {
        val spend = SpendCard.resolve(null, null, null, null)
        assertEquals(SpendScope.WALLET, spend.scope)
        assertNull(spend.text)
    }

    // ---- an explicit selection (the chip) in the mixed model ------------

    @Test
    fun mixedModelCardSelectionShowsTheCardScopeNumber() {
        val spend = SpendCard.resolve(
            "-85.00 €", "-72.00 €", "-157.00 €", SpendScope.CARD,
        )
        assertTrue(spend.chipVisible)
        assertEquals(SpendScope.CARD, spend.scope)
        assertEquals("-72.00 €", spend.text)
    }

    @Test
    fun mixedModelBlendedSelectionShowsTheCombinedNumber() {
        val spend = SpendCard.resolve(
            "-85.00 €", "-72.00 €", "-157.00 €", SpendScope.BLENDED,
        )
        assertTrue(spend.chipVisible)
        assertEquals(SpendScope.BLENDED, spend.scope)
        assertEquals("-157.00 €", spend.text)
    }

    @Test
    fun mixedModelWalletSelectionShowsTheWalletScopeNumber() {
        val spend = SpendCard.resolve(
            "-85.00 €", "-72.00 €", "-157.00 €", SpendScope.WALLET,
        )
        assertEquals(SpendScope.WALLET, spend.scope)
        assertEquals("-85.00 €", spend.text)
    }

    // ---- the stale-selection fallback across a model change --------------

    @Test
    fun walletOnlyModelRejectsAStaleCardSelection() {
        val spend = SpendCard.resolve("-85.00 €", null, null, SpendScope.CARD)
        assertFalse(spend.chipVisible)
        assertEquals(SpendScope.WALLET, spend.scope)
        assertEquals("-85.00 €", spend.text)
    }

    @Test
    fun walletOnlyModelRejectsAStaleBlendedSelection() {
        val spend = SpendCard.resolve("-85.00 €", null, null, SpendScope.BLENDED)
        assertEquals(SpendScope.WALLET, spend.scope)
        assertEquals("-85.00 €", spend.text)
    }

    @Test
    fun cardOnlyModelRejectsAStaleWalletSelection() {
        val spend = SpendCard.resolve(null, "-72.00 €", null, SpendScope.WALLET)
        assertFalse(spend.chipVisible)
        assertEquals(SpendScope.CARD, spend.scope)
        assertEquals("-72.00 €", spend.text)
    }

    @Test
    fun cardOnlyModelRejectsAStaleBlendedSelection() {
        val spend = SpendCard.resolve(null, "-72.00 €", null, SpendScope.BLENDED)
        assertEquals(SpendScope.CARD, spend.scope)
        assertEquals("-72.00 €", spend.text)
    }

    // ---- the transport contract on ParseData ----------------------------

    @Test
    fun standardPostWithoutScopeTextsHidesTheChipByConstruction() {
        // A single-scope model never posts the scope split: the screen's
        // resolver then sees one number and renders no chips.
        val data = ParseData.standard(
            moneySpentText = "-85.00 €",
            assetsText = "1.200,00 €",
            rewardsText = "2.50 €",
            profitLossText = "1.115,00 €",
        )
        assertNull(data.cardMoneySpent)
        assertNull(data.blendedMoneySpent)
        val spend = SpendCard.resolve(
            data.moneySpent, data.cardMoneySpent, data.blendedMoneySpent, null,
        )
        assertFalse(spend.chipVisible)
        assertEquals("-85.00 €", spend.text)
    }

    @Test
    fun mixedPostCarriesAllThreeScopeTexts() {
        val data = ParseData.standard(
            moneySpentText = "-85.00 €",
            assetsText = "1.200,00 €",
            rewardsText = "2.50 €",
            profitLossText = "1.115,00 €",
            cardMoneySpent = "-72.00 €",
            blendedMoneySpent = "-157.00 €",
        )
        assertEquals("-72.00 €", data.cardMoneySpent)
        assertEquals("-157.00 €", data.blendedMoneySpent)
        val spend = SpendCard.resolve(
            data.moneySpent, data.cardMoneySpent, data.blendedMoneySpent, null,
        )
        assertTrue(spend.chipVisible)
    }
}
