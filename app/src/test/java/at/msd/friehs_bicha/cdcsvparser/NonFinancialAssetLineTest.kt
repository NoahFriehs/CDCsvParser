package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.AssetCategory
import at.msd.friehs_bicha.cdcsvparser.app.NonFinancialAssetLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

/**
 * The thin JVM mirror of the core's asset line serde: building the line,
 * parsing the core's list form (incl. the single escape rule), the
 * category-vocabulary mapping, and the contract rejections.
 */
class NonFinancialAssetLineTest {

    @Test
    fun buildLineProducesTheCoreLine() {
        val line = NonFinancialAssetLine.buildLine(
            id = "uuid-1",
            label = "House",
            category = AssetCategory.RealEstate,
            valueEur = 350000.0,
            updated = LocalDateTime.of(2026, 11, 1, 9, 30, 0),
            note = null,
        )
        assertEquals("A;uuid-1;House;REAL_ESTATE;350000;2026-11-01T09:30:00;-", line)

        // Note set -> escaped text, dash only for the blank/none arm.
        val withNote = NonFinancialAssetLine.buildLine(
            id = "uuid-2",
            label = "Gold",
            category = AssetCategory.Commodity,
            valueEur = 12000.5,
            updated = LocalDateTime.of(2026, 11, 15, 18, 5, 9),
            note = "bars ; coins",
        )
        assertEquals("A;uuid-2;Gold;COMMODITY;12000.5;2026-11-15T18:05:09;bars ;; coins", withNote)
    }

    @Test
    fun parseOfCoreLinesHandlesTheEscapeRule() {
        // A ';' in the label and in the note: the core escapes both as ';;'.
        val line = "A;uuid-1;Art ;;carrying case;ART;12000;2026-11-01T09:30:00;The ;;frame;; is original"
        val maybe = NonFinancialAssetLine.parse(line)
        if (maybe == null) {
            fail("asset line did not parse: $line")
            return
        }
        assertEquals("uuid-1", maybe.id)
        assertEquals("Art ;carrying case", maybe.label)
        assertEquals(AssetCategory.Art, maybe.category)
        assertEquals(12000.0, maybe.valueEur, 1e-9)
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 30, 0), maybe.updated)
        assertEquals("The ;frame; is original", maybe.note)

        // A note-less line: the dash round-trips to null.
        val maybeNoNote = NonFinancialAssetLine.parse("A;uuid-2;Car;VEHICLE;5000;2024-03-01T12:00:00;-")
        if (maybeNoNote == null) {
            fail("dash-note line did not parse")
            return
        }
        assertEquals(AssetCategory.Vehicle, maybeNoNote.category)
        assertNull(maybeNoNote.note)
    }

    @Test
    fun roundTripKeepsEveryField() {
        val updated = LocalDateTime.of(2026, 11, 17, 18, 0, 0)
        val line = NonFinancialAssetLine.buildLine(
            id = "uuid-9",
            label = "Tax; paid; cellar",
            category = AssetCategory.Collectible,
            valueEur = 32100.5,
            updated = updated,
            note = "frame",
        )
        val maybeParsed = NonFinancialAssetLine.parse(line)
        if (maybeParsed == null) {
            fail("round-trip line did not parse: $line")
            return
        }
        assertEquals("uuid-9", maybeParsed.id)
        assertEquals("Tax; paid; cellar", maybeParsed.label)
        assertEquals(AssetCategory.Collectible, maybeParsed.category)
        assertEquals(32100.5, maybeParsed.valueEur, 1e-9)
        assertEquals(updated, maybeParsed.updated)
        assertEquals("frame", maybeParsed.note)
        // And the parsed entry builds back the same line (the upsert line
        // the edit path would send to the core).
        assertEquals(
            line,
            NonFinancialAssetLine.buildLine(
                maybeParsed.id, maybeParsed.label, maybeParsed.category,
                maybeParsed.valueEur, maybeParsed.updated, maybeParsed.note,
            )
        )
    }

    @Test
    fun parseRejectsContractViolations() {
        assertNull(NonFinancialAssetLine.parse("L;uuid-1;X;100;1;2020-01-01;-;-"))        // wrong kind
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;GOLD;100;2026-01-01T00:00:00;-"))    // unknown category
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;real_estate;100;2026-01-01T00:00:00;-")) // case-sensitive
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;100;2026-01-01;-"))          // date only, no time
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;100;2026-02-30T00:00:00;-"))  // no such day
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;100;2026-01-01T25:00:00;-"))  // hour 25
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;-50;2026-01-01T00:00:00;-"))  // negative
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;nan;2026-01-01T00:00:00;-"))  // not finite
        assertNull(NonFinancialAssetLine.parse("A;;X;ART;100;2026-01-01T00:00:00;-"))        // empty id
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;X;ART;100;2026-01-01T00:00:00;-;")) // 8 fields
        // A lone ';' in the label position SPLITS the line (the field
        // count breaks) - it is not a literal here.
        assertNull(NonFinancialAssetLine.parse("A;uuid-1;Mor;t;ART;100;2026-01-01T00:00:00;-"))
    }
}
