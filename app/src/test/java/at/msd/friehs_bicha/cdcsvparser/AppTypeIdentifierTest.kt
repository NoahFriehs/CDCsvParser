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
    private val blockPitHeader =
        "Date (UTC);Integration Name;Label;Outgoing Asset;Outgoing Amount;Incoming Asset;Incoming Amount;Fee Asset (optional);Fee Amount (optional);Comment (optional);Trx. ID (optional);Source Type;Source Name"

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
    fun detectsBlockPitHeader() {
        assertEquals(AppType.BlockPit, AppTypeIdentifier.getAppType(ArrayList(listOf(blockPitHeader, "line2"))))
    }

    @Test
    fun detectsBlockPitHeaderWithBomAndCrlf() {
        val bom = "\uFEFF"
        assertEquals(
            AppType.BlockPit,
            AppTypeIdentifier.getAppType(ArrayList(listOf(bom + blockPitHeader + "\r", "line2")))
        )
    }

    @Test
    fun unknownHeaderFallsBackToDefault() {
        assertEquals(AppType.Default, AppTypeIdentifier.getAppType(ArrayList(listOf("something,else", "line2"))))
    }

    @Test
    fun headerSniffingDetectsBlockPit() {
        assertEquals(AppType.BlockPit, AppTypeIdentifier.getAppType(blockPitHeader))
        assertEquals(AppType.BlockPit, AppTypeIdentifier.getAppType("\uFEFF" + blockPitHeader + "\r"))
    }

    @Test
    fun headerSniffingDetectsKraken() {
        val krakenHeader = "\"txid\",\"ordertxid\",\"pair\",\"time\",\"type\",\"ordertype\",\"price\",\"cost\",\"fee\",\"vol\",\"margin\",\"misc\",\"ledgers\""
        assertEquals(AppType.Kraken, AppTypeIdentifier.getAppType(krakenHeader))
    }

    @Test
    fun headerSniffingDetectsCurveCard() {
        assertEquals(AppType.CurveCard, AppTypeIdentifier.getAppType(curveHeader))
    }

    @Test
    fun headerSniffingIsNotDecisiveForSharedAndUnknownHeaders() {
        // The CDC and the card format share one header -> not decisive.
        assertEquals(null, AppTypeIdentifier.getAppType(cdcHeader))
        assertEquals(null, AppTypeIdentifier.getAppType("\uFEFF" + cdcHeader))
        assertEquals(null, AppTypeIdentifier.getAppType("something,else"))
        assertEquals(null, AppTypeIdentifier.getAppType(null))
        assertEquals(null, AppTypeIdentifier.getAppType("   "))
    }

    @Test
    fun cdCardFamilyCheck() {
        assertEquals(true, AppTypeIdentifier.isCdCardFamily(cdcHeader))
        assertEquals(true, AppTypeIdentifier.isCdCardFamily("\uFEFF" + cdcHeader + "\r"))
        assertEquals(false, AppTypeIdentifier.isCdCardFamily(blockPitHeader))
        assertEquals(false, AppTypeIdentifier.isCdCardFamily(null))
    }

    @Test
    fun cardDataLineHasKindZero() {
        assertEquals(true, AppTypeIdentifier.looksLikeCardData(
            "2024-04-01 12:00:00,Supermarket,VISA,10.00,EUR,-10.00,EUR,-10.00,-11.00,0,"))
        assertEquals(true, AppTypeIdentifier.looksLikeCardData(
            "2024-04-01 12:00:00,Supermarket,VISA,10.00,EUR,-10.00,EUR,-10.00,-11.00,0,\r"))
    }

    @Test
    fun cryptoDataLineHasKindLabelOrHash() {
        assertEquals(false, AppTypeIdentifier.looksLikeCardData(
            "2024-01-05 09:00:00,Supercharger Reward,ETH,0.0005,,,USD,0.25,0.25,supercharger_reward_to_app_credited,"))
        // On-chain row: a hash in the last column.
        assertEquals(false, AppTypeIdentifier.looksLikeCardData(
            "2024-01-05 09:00:00,Buy CRO,BTC,420.00,,,USD,231.03,231.03,crypto_purchase,abcd1234"))
    }

    @Test
    fun cardDetectionSurvivesQuotedCommaInDescription() {
        assertEquals(true, AppTypeIdentifier.looksLikeCardData(
            "2024-04-01 12:00:00,\"Shop, with comma\",VISA,10.00,EUR,-10.00,EUR,-10.00,-11.00,0,"))
    }

    @Test
    fun cardDetectionNeedsARealDataLine() {
        assertEquals(false, AppTypeIdentifier.looksLikeCardData(null))
        assertEquals(false, AppTypeIdentifier.looksLikeCardData(""))
        assertEquals(false, AppTypeIdentifier.looksLikeCardData("   "))
        // Degenerate line (too few columns) - no way to know.
        assertEquals(false, AppTypeIdentifier.looksLikeCardData(
            "2024-04-01 12:00:00,Broken,VISA,"))
    }
}
