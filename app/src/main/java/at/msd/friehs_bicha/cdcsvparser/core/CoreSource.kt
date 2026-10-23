package at.msd.friehs_bicha.cdcsvparser.core

import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * Read surface of the process-wide transaction core.
 * seam). Consumers (fragments, view-models, screens, utilities) hold a
 * [CoreSource] — never the `CoreService.Companion` directly — so the
 * core's home (a bound `Service` today) can move without touching the
 * UI layer. The surface is EXACTLY the members the consumers read
 * today; nothing else leaks through.
 *
 * [lastFailedLines] / [appModel] / [logFilePath] are read-only here on
 * purpose: all writes stay on the core side.
 */
interface CoreSource {

    /**
     * Broadcast action for a Firebase-data start. The literal itself
     * lives on the service companion (the broadcast contract owner;
     * the manifest references the same string); consumers read it
     * through this surface instead of the companion.
     */
    val ACTION_START_SERVICE_WITH_FIREBASE_DATA: String

    /** The native library loaded (the C++ core is usable). */
    val isCoreInitialized: Boolean

    /** The user's setting: run the C++ core (vs. the legacy Kotlin core). */
    val useCpp: Boolean

    /** Unparsable CSV lines of the last parse (0 = clean). */
    val lastFailedLines: Int

    /** The legacy-Kotlin-core model, when that path runs. */
    val appModel: AppModel?

    val errorCounter: MutableLiveData<Int>
    val parsedDataLiveData: MutableLiveData<ParseData>
    val chartSeriesLiveData: MutableLiveData<ChartSeries>
    val transactionsLiveData: MutableLiveData<ArrayList<Transaction>>
    val cardTransactionsLiveData: MutableLiveData<ArrayList<CroCardTransaction>>
    val walletsLiveData: MutableLiveData<ArrayList<Wallet>>
    val allWalletsLiveData: MutableLiveData<ArrayList<Wallet>>
    val firebaseDataLiveData: MutableLiveData<MutableList<FirebaseAppmodel>>

    /** The C++ core's own log file (settings screen target). */
    val logFilePath: String

    /** Runs [block] on the single `"cpp-core"` thread and returns its value. */
    fun <T> onCoreThread(block: () -> T): T

    /** Sub-daily EUR flow (spent chart frames; sparse slot lines). */
    suspend fun subDailySpentSeries(windowSeconds: Int, fromEpoch: Long): List<String>

    /** Sub-daily per-wallet balances (stock chart frames). */
    suspend fun subDailyWalletSeries(windowSeconds: Int, fromEpoch: Long): List<String>

    /** One wallet's typed asset state (standard layout). */
    fun getAssetMap(walletId: Int): WalletAssets

    fun startServiceWithData(data: ArrayList<String>?, mode: Int, fileName: String? = null)
    fun startServiceFromLocal()
    fun saveDataToFirebase()
    fun refreshPricesIfStale()
}

/**
 * Default [CoreSource]: a thin forwarder onto [CoreService] — the single
 * process-wide core holder. Every read goes through [CoreService] again,
 * so behavior (lazy companion init, .so load, thread discipline) is
 * byte-identical to the old direct reads.
 */
object Core : CoreSource {
    override val ACTION_START_SERVICE_WITH_FIREBASE_DATA: String
        get() = CoreService.ACTION_START_SERVICE_WITH_FIREBASE_DATA

    override val isCoreInitialized: Boolean
        get() = CoreService.isCoreInitialized

    override val useCpp: Boolean
        get() = CoreService.useCpp

    override val lastFailedLines: Int
        get() = CoreService.lastFailedLines

    override val appModel: AppModel?
        get() = CoreService.appModel

    override val errorCounter: MutableLiveData<Int>
        get() = CoreService.errorCounter

    override val parsedDataLiveData: MutableLiveData<ParseData>
        get() = CoreService.parsedDataLiveData

    override val chartSeriesLiveData: MutableLiveData<ChartSeries>
        get() = CoreService.chartSeriesLiveData

    override val transactionsLiveData: MutableLiveData<ArrayList<Transaction>>
        get() = CoreService.transactionsLiveData

    override val cardTransactionsLiveData: MutableLiveData<ArrayList<CroCardTransaction>>
        get() = CoreService.cardTransactionsLiveData

    override val walletsLiveData: MutableLiveData<ArrayList<Wallet>>
        get() = CoreService.walletsLiveData

    override val allWalletsLiveData: MutableLiveData<ArrayList<Wallet>>
        get() = CoreService.allWalletsLiveData

    override val firebaseDataLiveData: MutableLiveData<MutableList<FirebaseAppmodel>>
        get() = CoreService.firebaseDataLiveData

    override val logFilePath: String
        get() = CoreService.logFilePath

    override fun <T> onCoreThread(block: () -> T): T =
        CoreService.onCoreThread(block)

    override suspend fun subDailySpentSeries(
        windowSeconds: Int,
        fromEpoch: Long,
    ): List<String> = CoreService.subDailySpentSeries(windowSeconds, fromEpoch)

    override suspend fun subDailyWalletSeries(
        windowSeconds: Int,
        fromEpoch: Long,
    ): List<String> = CoreService.subDailyWalletSeries(windowSeconds, fromEpoch)

    override fun getAssetMap(walletId: Int): WalletAssets =
        CoreService.getAssetMap(walletId)

    override fun startServiceWithData(
        data: ArrayList<String>?,
        mode: Int,
        fileName: String?,
    ) = CoreService.startServiceWithData(data, mode, fileName)

    override fun startServiceFromLocal() = CoreService.startServiceFromLocal()

    override fun saveDataToFirebase() = CoreService.saveDataToFirebase()

    override fun refreshPricesIfStale() = CoreService.refreshPricesIfStale()
}
