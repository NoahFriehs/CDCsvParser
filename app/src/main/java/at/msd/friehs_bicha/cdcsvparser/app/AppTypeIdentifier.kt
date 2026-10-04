package at.msd.friehs_bicha.cdcsvparser.app

/**
 * App type identifier
 */
object AppTypeIdentifier {

    /**
     * Returns the app type.
     *
     * @param input the input from the file
     * @return the app type
     */
    fun getAppType(input: ArrayList<String>): AppType {

        when {
            input[0].contains(AppTypeIdentifierCdCsv) -> return AppType.CdCsvParser
            input[0].contains(curveTxString) -> return AppType.CurveCard
            input[0].contains(blockPitString) -> return AppType.BlockPit
        }

        return AppType.Default
    }

    /**
     * Header sniffing for the parse-mode decision.
     *
     * @param firstLine the first line of the CSV (or null)
     * @return the app type when the header carries a distinctive signature
     * (BlockPit, Kraken, CurveCard), or null for unknown or shared headers.
     * The CDC and Card formats share one header, so a CDC-family header is
     * intentionally not decisive — the selected settings type keeps
     * deciding between the two.
     */
    fun getAppType(firstLine: String?): AppType? {
        val line = firstLine?.takeIf { it.isNotBlank() } ?: return null
        return when {
            line.contains(blockPitString) -> AppType.BlockPit
            line.contains(krakenString) -> AppType.Kraken
            line.contains(curveTxString) -> AppType.CurveCard
            else -> null
        }
    }

    /**
     * @param firstLine the first line of the CSV (or null)
     * @return true when the header matches the shared Crypto.com header. The
     * same header is used by both the CDC and the card format, so it cannot
     * decide between the two — the settings type must.
     */
    fun isCdCardFamily(firstLine: String?): Boolean {
        return firstLine?.takeIf { it.isNotBlank() }?.contains(AppTypeIdentifierCdCsv) == true
    }

    private const val AppTypeIdentifierCdCsv =
        "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"

    private const val curveTxString =
        "Date (YYYY-MM-DD as UTC),Merchant,Txn Amount (Funding Card),Txn Currency (Funding Card),Txn Amount (Foreign Spend),Txn Currency (Foreign Spend),Card Name,Card Last 4 Digits,Type,Category,Notes"

    // BlockPit exports are `;`-separated and start with "Date (UTC)";
    // keep in sync with BLOCKPIT_HEADER in TransactionParser.cpp.
    private const val blockPitString = "Date (UTC)"

    // Kraken trade exports start with this header; keep in sync with the
    // comparison in parseKraken (TransactionParser.cpp).
    private const val krakenString = "\"txid\",\"ordertxid\",\"pair\",\"time\",\"type\",\"ordertype\",\"price\",\"cost\",\"fee\",\"vol\",\"margin\",\"misc\",\"ledgers\""

}