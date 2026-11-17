package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.LiabilityLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * The thin JVM mirror of the core's balance-sheet line serde: building
 * the set form, parsing the core's list form (incl. the single escape
 * rule), validation rejections, and the set/list round trip.
 */
class LiabilityLineTest {

    @Test
    fun buildLineProducesTheCoreSetForm() {
        val line = LiabilityLine.buildLine(
            id = "uuid-1",
            label = "Mortgage",
            principalEur = 150000.0,
            annualRatePct = 3.575,
            start = LocalDate.of(2019, 2, 1),
            end = LocalDate.of(2049, 2, 1),
            monthlyPaymentEur = 899.99,
        )
        assertEquals("L;uuid-1;Mortgage;150000;3.575;2019-02-01;2049-02-01;899.99", line)

        // Optional fields off -> dashes.
        val open = LiabilityLine.buildLine(
            id = "uuid-2",
            label = "Car",
            principalEur = 20000.0,
            annualRatePct = 4.9,
            start = LocalDate.of(2024, 5, 1),
            end = null,
            monthlyPaymentEur = null,
        )
        assertEquals("L;uuid-2;Car;20000;4.9;2024-05-01;-;-", open)
    }

    @Test
    fun listOfLinesParsesWithTheEscapeRule() {
        // A label containing a semicolon: the core escapes it as ';;'.
        val line = "L;uuid-1;Mort;;gage;150000;3.575;2019-02-01;2049-02-01;899.99;bal;92272.378308"
        val maybe = LiabilityLine.parse(line)
        if (maybe == null) {
            fail("list form line did not parse: $line")
            return
        }
        assertEquals("uuid-1", maybe.id)
        assertEquals("Mort;gage", maybe.label)
        assertEquals(150000.0, maybe.principalEur, 1e-9)
        assertEquals(3.575, maybe.annualRatePct, 1e-9)
        assertEquals(LocalDate.of(2019, 2, 1), maybe.start)
        assertEquals(LocalDate.of(2049, 2, 1), maybe.end)
        assertEquals(899.99, maybe.monthlyPaymentEur!!, 1e-9)
        assertEquals(92272.378308, maybe.balanceEur!!, 1e-6)

        // The set form (no trailer) parses with a null balance.
        val maybeSet = LiabilityLine.parse("L;uuid-2;Car;20000;4.9;2024-05-01;-;-")
        if (maybeSet == null) {
            fail("set form line did not parse")
            return
        }
        assertEquals("uuid-2", maybeSet.id)
        assertNull(maybeSet.end)
        assertNull(maybeSet.monthlyPaymentEur)
        assertNull(maybeSet.balanceEur)
    }

    @Test
    fun roundTripKeepsEveryField() {
        val original = LiabilityLine(
            id = "uuid-9",
            label = "Tax; credit; loan",
            principalEur = 32100.5,
            annualRatePct = 1.25,
            start = LocalDate.of(2025, 7, 15),
            end = null,
            monthlyPaymentEur = 250.0,
            balanceEur = 31999.1,
        )
        val line = LiabilityLine.buildLine(
            original.id, original.label, original.principalEur, original.annualRatePct,
            original.start, original.end, original.monthlyPaymentEur,
        )
        // The list form the core emits = the set form + the trailer.
        val listLine = "$line;bal;${LiabilityLine.formatDecimal(original.balanceEur ?: 0.0)}"
        val maybeParsed = LiabilityLine.parse(listLine)
        if (maybeParsed == null) {
            fail("round-trip line did not parse: $listLine")
            return
        }
        assertEquals(original.id, maybeParsed.id)
        assertEquals(original.label, maybeParsed.label)
        assertEquals(original.principalEur, maybeParsed.principalEur, 1e-9)
        assertEquals(original.annualRatePct, maybeParsed.annualRatePct, 1e-9)
        assertEquals(original.start, maybeParsed.start)
        assertEquals(original.end, maybeParsed.end)
        assertEquals(original.monthlyPaymentEur!!, maybeParsed.monthlyPaymentEur!!, 1e-9)
        assertEquals(original.balanceEur!!, maybeParsed.balanceEur!!, 1e-9)
        // And the parsed entry builds back the same set form (the upsert
        // line the edit path would send to the core).
        assertEquals(line, LiabilityLine.buildLine(
            maybeParsed.id, maybeParsed.label, maybeParsed.principalEur, maybeParsed.annualRatePct,
            maybeParsed.start, maybeParsed.end, maybeParsed.monthlyPaymentEur,
        ))
    }

    @Test
    fun parseRejectsContractViolations() {
        assertNull(LiabilityLine.parse("A;uuid-1;X;100;1;2020-01-01;-;-"))        // wrong kind
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2020-01-01;-"))          // 7 fields
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2020-01-01;-;-;"))       // 9 fields
        assertNull(LiabilityLine.parse("L;uuid-1;X;abc;1;2020-01-01;-;-"))        // no number
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2020-13-01;-;-"))        // month 13
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2024-02-30;-;-"))        // no such day
        assertNull(LiabilityLine.parse("L;uuid-1;X;-100;1;2020-01-01;-;-"))       // negative
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2020-01-01;-;nan"))      // not finite
        assertNull(LiabilityLine.parse("L;;X;100;1;2020-01-01;-;-"))              // empty id
        assertNull(LiabilityLine.parse("L;uuid 1;X;100;1;2020-01-01;-;-"))        // space in id
        // A list form with a mangled trailer fails, too.
        assertNull(LiabilityLine.parse("L;uuid-1;X;100;1;2020-01-01;-;-;x;3"))
        // The escape rule itself: a lone ';' in a label position SPLITs
        // the line (the field count breaks) - it is not a literal here.
        assertNull(LiabilityLine.parse("L;uuid-1;Mor;t;100;1;2020-01-01;-;-"))
    }
}
