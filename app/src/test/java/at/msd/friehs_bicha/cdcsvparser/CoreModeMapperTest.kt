package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.core.CoreModeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreModeMapperTest {

    @Test
    fun supportedTypesMapToCoreModes() {
        // Must stay in sync with enum Mode in cpp/Enums.h
        assertEquals(0, CoreModeMapper.toCoreMode(AppType.CdCsvParser))
        assertEquals(1, CoreModeMapper.toCoreMode(AppType.CroCard))
        assertEquals(4, CoreModeMapper.toCoreMode(AppType.Kraken))
    }

    @Test
    fun unsupportedTypesMapToNull() {
        // Null means "reject with an error", not "guess a mode" - the old
        // ordinal passthrough silently used the wrong parser here.
        assertNull(CoreModeMapper.toCoreMode(AppType.CurveCard))
        assertNull(CoreModeMapper.toCoreMode(AppType.Default))
        assertNull(CoreModeMapper.toCoreMode(AppType.Custom))
    }
}

class AppTypeTest {

    @Test
    fun safeFromOrdinalInBounds() {
        assertEquals(AppType.CdCsvParser, AppType.safeFromOrdinal(0))
        assertEquals(AppType.Kraken, AppType.safeFromOrdinal(5))
    }

    @Test
    fun safeFromOrdinalOutOfBoundsFallsBack() {
        assertEquals(AppType.CdCsvParser, AppType.safeFromOrdinal(-1))
        assertEquals(AppType.CdCsvParser, AppType.safeFromOrdinal(99))
    }

    @Test
    fun safeFromName() {
        assertEquals(AppType.CroCard, AppType.safeFromName("CroCard"))
        assertNull(AppType.safeFromName("NotAType"))
        assertNull(AppType.safeFromName(""))
    }
}
