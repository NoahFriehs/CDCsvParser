package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.general.ImportNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "partially imported" history marker kernel: encode/load round trip,
 * merge idempotence, and the corrupt-value degradation (a garbage store
 * value must read as "no markers", never throw).
 */
class ImportNotesTest {

    private val a = "2026-01-01-10-00-00.csv"
    private val b = "2026-01-02-11-00-00.csv"
    private val c = "2026-01-03-12-00-00.csv"

    @Test
    fun loadNullAndEmptyYieldNoNames() {
        assertTrue(ImportNotes.loadPartialImported(null).isEmpty())
        assertTrue(ImportNotes.loadPartialImported("").isEmpty())
        assertTrue(ImportNotes.loadPartialImported("   ").isEmpty())
    }

    @Test
    fun loadGarbageDegradesToEmpty() {
        assertTrue(ImportNotes.loadPartialImported("not json at all").isEmpty())
        // A JSON value that is not an array is no marker list.
        assertTrue(ImportNotes.loadPartialImported("\"just a string\"").isEmpty())
        assertTrue(ImportNotes.loadPartialImported("{\"wrong\":\"shape\"}").isEmpty())
        // Non-primitive and null members drop out, the valid ones survive.
        assertEquals(setOf(a), ImportNotes.loadPartialImported("[\"$a\",null,{\"x\":1}]"))
    }

    @Test
    fun encodeLoadRoundTripKeepsEveryName() {
        val value = ImportNotes.encodePartialImported(setOf(c, a, b))
        assertEquals(setOf(a, b, c), ImportNotes.loadPartialImported(value))
        // Sorted: encoding the same set twice is byte-identical.
        assertEquals(value, ImportNotes.encodePartialImported(setOf(a, c, b)))
    }

    @Test
    fun encodeEmptySetIsEmptyJsonArray() {
        assertEquals("[]", ImportNotes.encodePartialImported(emptySet()))
    }

    @Test
    fun mergeAddsNewAndKeepsExisting() {
        val base = ImportNotes.encodePartialImported(setOf(a))
        val merged = ImportNotes.mergePartialImported(base, listOf(b))
        assertEquals(setOf(a, b), ImportNotes.loadPartialImported(merged))
    }

    @Test
    fun mergeOfNothingNewReturnsTheOriginalString() {
        val base = ImportNotes.encodePartialImported(setOf(a))
        assertEquals(base, ImportNotes.mergePartialImported(base, listOf(a)))
        // Same no-op guard on an empty store value.
        assertEquals("", ImportNotes.mergePartialImported(null, emptyList()))
    }

    @Test
    fun mergeIntoCorruptValueStartsFresh() {
        val merged = ImportNotes.mergePartialImported("garbage", listOf(b))
        assertEquals(setOf(b), ImportNotes.loadPartialImported(merged))
    }

    @Test
    fun removeDropsOnlyTheNamedOnes() {
        val base = ImportNotes.encodePartialImported(setOf(a, b, c))
        val rest = ImportNotes.removePartialImported(base, listOf(a, c))
        assertEquals(setOf(b), ImportNotes.loadPartialImported(rest))
    }

    @Test
    fun removeOfUnmarkedKeepsTheOriginalString() {
        val base = ImportNotes.encodePartialImported(setOf(a))
        assertEquals(base, ImportNotes.removePartialImported(base, listOf(b)))
        // Same no-op guard on an empty store value.
        assertEquals("", ImportNotes.removePartialImported(null, listOf(a)))
        // A corrupt value with no marked name is left untouched, too.
        assertEquals("garbage", ImportNotes.removePartialImported("garbage", listOf(a)))
    }

    @Test
    fun fullReImportConvergesToAnEmptyMarker() {
        // cut at first import -> whole re-import -> no chip left.
        val afterCut = ImportNotes.mergePartialImported(null, listOf(a))
        assertEquals(setOf(a), ImportNotes.loadPartialImported(afterCut))
        assertEquals("[]", ImportNotes.removePartialImported(afterCut, listOf(a)))
        // A second whole re-import is a no-op on the empty marker.
        assertEquals("[]", ImportNotes.removePartialImported("[]", listOf(a)))
    }
}
