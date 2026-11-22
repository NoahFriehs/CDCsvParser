package at.msd.friehs_bicha.cdcsvparser.ui.compose

/**
 * The scope of the parse-screen money-spent card: which outflow ledger
 * the card's number covers. No Android imports on purpose (JVM-tested).
 */
enum class SpendScope {
    /** The wallet (crypto) scope's fiat outflows. */
    WALLET,

    /** The card scope's outflows. */
    CARD,

    /** Both scopes combined. */
    BLENDED,
}

/**
 * Pure (JVM-tested) resolution of the parse-screen money-spent card.
 *
 * The card shows ONE number. Only the MIXED wallet+card model posts both
 * scope numbers, and only there is the scope chip ([chipVisible]) shown -
 * on the parse screen it toggles which scope the one number covers. In a
 * single-scope model nothing disambiguates: the chip stays hidden and the
 * card shows the number it always showed.
 *
 * [scope] is the EFFECTIVE scope after the stale-selection fallback below;
 * [text] is the number to render for it.
 */
data class SpendCard(
    val chipVisible: Boolean,
    val scope: SpendScope,
    val text: String?,
) {
    companion object {
        /**
         * Resolves the posted scope texts for the chip's selection.
         *
         * @param walletText the wallet (crypto) scope number; null when the
         *   model has no crypto transactions
         * @param cardText the card scope number; null when the model has no
         *   card transactions
         * @param blendedText the combined number; non-null exactly when both
         *   scope numbers are present
         * @param requested the chip selection; null = no selection yet
         *
         * The default scope is the number the card has always shown: the
         * wallet scope in a crypto model (mixed included), the card scope
         * in a card-only model. A selection whose scope has no posted
         * number - the chip state survives a model change (e.g. BLENDED
         * selected, then a card-only re-import) - falls back to that
         * default, so the card always renders a number that exists.
         */
        fun resolve(
            walletText: String?,
            cardText: String?,
            blendedText: String?,
            requested: SpendScope?,
        ): SpendCard {
            val mixed = walletText != null && cardText != null
            val fallback = when {
                mixed -> SpendScope.WALLET
                cardText != null -> SpendScope.CARD
                else -> SpendScope.WALLET
            }
            val scope = requested ?: fallback
            val valid = when (scope) {
                SpendScope.WALLET -> walletText != null
                SpendScope.CARD -> cardText != null
                SpendScope.BLENDED -> mixed
            }
            val effective = if (valid) scope else fallback
            val text = when (effective) {
                SpendScope.WALLET -> walletText
                SpendScope.CARD -> cardText
                SpendScope.BLENDED -> blendedText
            }
            return SpendCard(chipVisible = mixed, scope = effective, text = text)
        }
    }
}
