package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.transactions.fromOrdinal
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTypeTest {

    @Test
    fun fromOrdinalInBounds() {
        assertEquals(TransactionType.values()[0], fromOrdinal(0))
        val last = TransactionType.values().size - 1
        assertEquals(TransactionType.values()[last], fromOrdinal(last))
    }

    @Test
    fun fromOrdinalOutOfBoundsFallsBackToString() {
        // The C++ enum has values (e.g. NONE) that the Kotlin enum does not:
        // the app must never crash on persisted foreign ordinals.
        assertEquals(TransactionType.STRING, fromOrdinal(-1))
        assertEquals(TransactionType.STRING, fromOrdinal(999))
    }
}
