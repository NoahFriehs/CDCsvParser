package at.msd.friehs_bicha.cdcsvparser.core

/**
 * The "delete all data" action (the in-app data-deletion entry point):
 * which stores the action wipes, in which order, and what it reports
 * back.
 *
 * This file is pure on purpose: [STEPS] is the single source of truth
 * for the wipe and is JVM-tested (coverage + order); the Android
 * executor ([CoreService.deleteAllData]) maps each step to exactly one
 * side effect and runs them in this order.
 *
 * The order is a contract:
 *
 * 1. The core TM's in-memory state comes FIRST. It is the source of
 *    truth, and while it still holds the model, other activity (an
 *    auto-save triggered by a balance-sheet mutation, a fast-start
 *    rebuild re-parsing the newest CSV) could re-persist the save files
 *    after the wipe deleted them. With the TM reset first, deleting the
 *    files on disk is the final word. The executor re-posts the emptied
 *    core state in this same step, so the screens converge to the
 *    no-data state instead of holding the pre-wipe model.
 * 2. The derivative stores follow: the core's own save files (fast
 *    start), the legacy Room copy, all stored settings, the
 *    refetchable price caches and the uploaded CSV history. None of
 *    them is a source of truth any more once step 1 settled.
 * 3. The in-memory price cache is cleared AFTER its on-disk files, so
 *    the window in which a stale in-memory entry could land in a freshly
 *    deleted cache file is the shortest one (the same property the
 *    settings' cache-delete action has).
 *
 * Deliberately NOT part of the wipe (the client-side-state-only scope):
 * the Firebase account and its cloud document (the account-delete path
 * owns those) and the device Keystore key (a key, not user data; the
 * existing cloud document stays readable to its owner).
 */
object DataWipe {

    enum class Step {
        /** The core TM's import state (one JNI call on the cpp-core thread). */
        CORE_STATE,
        /** The core's persisted save files (the fast-start state). */
        CORE_SAVE_FILES,
        /** The legacy Room copy (wallets + transactions, card wallets + card transactions). */
        ROOM,
        /** All stored app settings (the DataStore store + the in-process cache). */
        DATASTORE,
        /** The on-disk price caches (deep history, symbol map, intraday refinement). */
        PRICE_CACHE_FILES,
        /** The process-local in-memory price cache. */
        PRICE_CACHE_MEMORY,
        /** The imported CSV history files (the upload history list). */
        HISTORY_FILES,
    }

    /** The exact deletion order the executor runs, one side effect per step. */
    val STEPS: List<Step> = listOf(
        Step.CORE_STATE,
        Step.CORE_SAVE_FILES,
        Step.ROOM,
        Step.DATASTORE,
        Step.PRICE_CACHE_FILES,
        Step.PRICE_CACHE_MEMORY,
        Step.HISTORY_FILES,
    )

    /**
     * The on-disk price cache files, by name in the app's files
     * directory. One list both wipe paths share: the full wipe deletes
     * them, and the settings' cache-delete action removes exactly these
     * (so adding a cache file can never drift between the two).
     */
    val PRICE_CACHE_FILE_NAMES: List<String> = listOf(
        "history_prices.json",
        "symbol_id_cache.json",
        "history_prices_intraday.json",
    )

    /**
     * What the action reports after it has run every step: whether the
     * live core TM could be reset in memory (a live core is needed -
     * the on-disk stores are wiped either way, so the app state is
     * clean on the next start), and how many files were actually
     * deleted (save + caches + history).
     */
    data class Result(
        val coreStateCleared: Boolean,
        val filesDeleted: Int,
    )
}
