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

    private const val AppTypeIdentifierCdCsv =
        "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"

    private const val curveTxString =
        "Date (YYYY-MM-DD as UTC),Merchant,Txn Amount (Funding Card),Txn Currency (Funding Card),Txn Amount (Foreign Spend),Txn Currency (Foreign Spend),Card Name,Card Last 4 Digits,Type,Category,Notes"

    // BlockPit exports are `;`-separated and start with "Date (UTC)";
    // keep in sync with BLOCKPIT_HEADER in TransactionParser.cpp.
    private const val blockPitString = "Date (UTC)"

}