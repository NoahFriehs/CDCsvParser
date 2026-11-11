package at.msd.friehs_bicha.cdcsvparser.core

/**
 * Typed per-wallet display state (replaces the old `Map<String, String?>`
 * keyed by `R.id.*.toString()`). A `null` field = the corresponding row is
 * hidden on the screen (the old contract overloaded "null value" and
 * "missing key" for that - both are now simply a null field).
 *
 * The two sentinel strings carried by the fields are TRANSPORT TOKENS, not
 * user-facing literals: the screen maps them to localized resources
 * ([WalletAssets.NO_INTERNET] -> `R.string.no_internet_connection`,
 * `"prices loading"` -> `R.string.prices_loading`). The parse screen has
 * the same mapping; the wallet producers only ever emit [NO_INTERNET].
 */
data class WalletAssets(
    val moneySpent: String? = null,
    val assets: String? = null,
    val rewards: String? = null,
    val profitLoss: String? = null,
) {
    companion object {
        /** Prices not loaded yet (AssetValue not running) - transport token. */
        const val NO_INTERNET = "no internet connection"

        /**
         * Card-wallet layout: card buckets are fiat-denominated, so only
         * "money spent" exists; the other three rows are hidden (null).
         * Same shape the old card map posted (null values).
         */
        fun card(moneySpentText: String?): WalletAssets =
            WalletAssets(moneySpent = moneySpentText)

        /**
         * Standard-wallet layout: all four rows present. While the price
         * lookup is not running yet, the three derived values carry the
         * [NO_INTERNET] token (displayed as the localized "no internet
         * connection" text) - the old map's exact behavior.
         */
        fun standard(
            moneySpentText: String,
            assetsText: String,
            rewardsText: String,
            profitLossText: String,
            pricesRunning: Boolean,
        ): WalletAssets = if (pricesRunning) {
            WalletAssets(
                moneySpent = moneySpentText,
                assets = assetsText,
                rewards = rewardsText,
                profitLoss = profitLossText,
            )
        } else {
            WalletAssets(
                moneySpent = moneySpentText,
                assets = NO_INTERNET,
                rewards = NO_INTERNET,
                profitLoss = NO_INTERNET,
            )
        }
    }
}

/**
 * One wallet's posted entry (replaces the old `AssetData` map wrapper):
 * posted per wallet on [com.at.msd.friehs_bicha.cdcsvparser.core.CoreService.assetMaps].
 */
data class AssetEntry(val walletId: Int, val assets: WalletAssets)

/**
 * Parse-screen card display state (replacement of the old
 * `R.id`-keyed `parseMap`). A `null` field = row hidden; the derived
 * values carry the [WalletAssets.NO_INTERNET] / `"prices loading"`
 * transport tokens while prices are missing, mapped to localized
 * resources by the screen (same as the wallet screen).
 *
 * [attributionVisible] replaces the old `R.id.coinGeckoApiLabel` key: the
 * key only ever transported a visibility flag (explicit `null` = hide the
 * attribution footer - files without crypto -; absent = keep it up; no
 * label TEXT was ever sent through the map; the footer text itself is
 * static in the screen). `true` = show the footer.
 */
data class ParseData(
    val assets: String? = null,
    val moneySpent: String? = null,
    val rewards: String? = null,
    val profitLoss: String? = null,
    val attributionVisible: Boolean = true,
    val offlinePriceNote: OfflinePriceNote? = null,
    val parseBudgetNote: ParseBudgetNote? = null,
) {
    companion object {
        /**
         * Files without crypto transactions (card/fiat scope): only "money
         * spent" exists and the attribution footer is hidden.
         */
        fun card(moneySpentText: String?, parseBudgetNote: ParseBudgetNote? = null): ParseData =
            ParseData(
                moneySpent = moneySpentText,
                attributionVisible = false,
                parseBudgetNote = parseBudgetNote,
            )

        /**
         * Standard (crypto) files: footer stays up; the three derived
         * values follow the prices state (real values, the "prices
         * loading" token while the provider fetches, or the no-internet
         * token while it is not running).
         */
        fun standard(
            moneySpentText: String,
            assetsText: String,
            rewardsText: String,
            profitLossText: String,
            offlinePriceNote: OfflinePriceNote? = null,
            parseBudgetNote: ParseBudgetNote? = null,
        ): ParseData = ParseData(
            moneySpent = moneySpentText,
            assets = assetsText,
            rewards = rewardsText,
            profitLoss = profitLossText,
            offlinePriceNote = offlinePriceNote,
            parseBudgetNote = parseBudgetNote,
        )
    }
}

/**
 * The two-stage offline valuation note (transport for [ParseData]; the
 * screen renders the non-null parts as note row(s)). Present only while
 * [com.at.msd.friehs_bicha.cdcsvparser.price.AssetValue.isConnected] is
 * false AND degraded prices are actually in use (a cache entry of any
 * age and/or the static baseline table) - fresh-only valuations stay
 * note-free.
 */
data class OfflinePriceNote(
    /** Minutes since the oldest cache entry in use; null when no cache entry was used at all (everything from the static table). */
    val oldestAgeMinutes: Int? = null,
    /** At least one held symbol had no cache entry and was filled from the static baseline table. */
    val usesStaticBaseline: Boolean = false,
)

/**
 * Partial-import note (transport for [ParseData]): the hard parse budget
 * cut the last import short. [unprocessedRows] &gt; 0 = the parser stopped
 * reading the input (that many lines were never read); 0 = every line was
 * read but the wallet aggregation stopped (the screen renders a distinct
 * wording for the two). [budgetSeconds] is the budget the cut hit - the
 * app passes its own constant into the core, so the note names the limit
 * that actually applied. Present only when the last import was cut: it is
 * per-parse process state (the core resets it at the start of every
 * parse), never part of the saved model.
 */
data class ParseBudgetNote(
    val unprocessedRows: Int,
    val budgetSeconds: Int,
)
