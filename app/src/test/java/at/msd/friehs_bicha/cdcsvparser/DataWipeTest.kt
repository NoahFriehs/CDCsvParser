package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.core.DataWipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deletion-order contract of the full data wipe. The executor
 * (CoreService.deleteAllData) is Android-side; these tests pin the
 * pure step list it runs - coverage, the no-duplicate rule and the
 * load-bearing order (the core reset before the files it could
 * re-persist, the in-memory cache after its on-disk files).
 */
class DataWipeTest {

    @Test
    fun `the wipe covers every store exactly once`() {
        val expected = setOf(
            DataWipe.Step.CORE_STATE,
            DataWipe.Step.CORE_SAVE_FILES,
            DataWipe.Step.ROOM,
            DataWipe.Step.DATASTORE,
            DataWipe.Step.PRICE_CACHE_FILES,
            DataWipe.Step.PRICE_CACHE_MEMORY,
            DataWipe.Step.HISTORY_FILES,
        )
        assertEquals(expected, DataWipe.STEPS.toSet())
        // No duplicate step: a store wiped twice is a bug, a store
        // missing here is a data-loss bug.
        assertEquals(expected.size, DataWipe.STEPS.size)
    }

    @Test
    fun `the core state is reset before any derivative store`() {
        // The core TM is the source of truth: while it still holds the
        // model it could re-persist the save files (an auto-save or the
        // fast-start rebuild), so its reset must come first and happen
        // exactly once.
        assertEquals(DataWipe.Step.CORE_STATE, DataWipe.STEPS.first())
        assertTrue(
            "the core reset must not repeat after the derivatives start",
            DataWipe.STEPS.drop(1).none { it == DataWipe.Step.CORE_STATE },
        )
    }

    @Test
    fun `the in-memory price cache is cleared after its on-disk files`() {
        val filesIndex = DataWipe.STEPS.indexOf(DataWipe.Step.PRICE_CACHE_FILES)
        val memoryIndex = DataWipe.STEPS.indexOf(DataWipe.Step.PRICE_CACHE_MEMORY)
        assertTrue(filesIndex in DataWipe.STEPS.indices)
        assertTrue(memoryIndex in DataWipe.STEPS.indices)
        assertTrue(
            "clearing the memory before the files would let a stale entry rewrite a deleted cache file",
            filesIndex < memoryIndex,
        )
    }

    @Test
    fun `the price cache file list covers all three cache files`() {
        assertEquals(
            setOf(
                "history_prices.json",
                "symbol_id_cache.json",
                "history_prices_intraday.json",
            ),
            DataWipe.PRICE_CACHE_FILE_NAMES.toSet(),
        )
        assertEquals(DataWipe.PRICE_CACHE_FILE_NAMES.size, DataWipe.PRICE_CACHE_FILE_NAMES.toSet().size)
    }
}
