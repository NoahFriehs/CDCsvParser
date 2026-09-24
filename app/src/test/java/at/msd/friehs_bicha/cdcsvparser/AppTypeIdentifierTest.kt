package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.AppTypeIdentifier
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypeIdentifierTest {

    private val cdcHeader =
        "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"
    private val curveHeader =
        "Date (YYYY-MM-DD as UTC),Merchant,Txn Amount (Funding Card),Txn Currency (Funding Card),Txn Amount (Foreign Spend),Txn Currency (Foreign Spend),Card Name,Card Last 4 Digits,Type,Category,Notes"

    @Test
    fun detectsCdCsvParserHeader() {
        assertEquals(AppType.CdCsvParser, AppTypeIdentifier.getAppType(ArrayList(listOf(cdcHeader, "line2"))))
    }

    @Test
    fun detectsCurveCardHeader() {
        assertEquals(AppType.CurveCard, AppTypeIdentifier.getAppType(ArrayList(listOf(curveHeader, "line2"))))
    }

    @Test
    fun toleratesBomBeforeTheHeader() {
        val bom = "\uFEFF"
        assertEquals(AppType.CdCsvParser, AppTypeIdentifier.getAppType(ArrayList(listOf(bom + cdcHeader, "line2"))))
    }

    @Test
    fun toleratesTrailingCrlf() {
        assertEquals(AppType.CdCsvParser, AppTypeIdentifier.getAppType(ArrayList(listOf(cdcHeader + "\r", "line2"))))
    }

    @Test
    fun unknownHeaderFallsBackToDefault() {
        assertEquals(AppType.Default, AppTypeIdentifier.getAppType(ArrayList(listOf("something,else", "line2"))))
    }
}
