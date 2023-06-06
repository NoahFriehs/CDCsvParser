package at.msd.friehs_bicha.cdcsvparser.app

object AppTypeIdentifier {

    fun getAppType(input: ArrayList<String>): AppType {

        when {
            input[0].contains(AppTypeIdentifierCdCsv) -> return AppType.CdCsvParser
            input[0].contains(curveTxString) -> return AppType.CurveCard
        }

        return AppType.Default
    }

    private const val AppTypeIdentifierCdCsv = "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"

    var curveTxString = "Date (YYYY-MM-DD as UTC),Merchant,Txn Amount (Funding Card),Txn Currency (Funding Card),Txn Amount (Foreign Spend),Txn Currency (Foreign Spend),Card Name,Card Last 4 Digits,Type,Category,Notes"

}