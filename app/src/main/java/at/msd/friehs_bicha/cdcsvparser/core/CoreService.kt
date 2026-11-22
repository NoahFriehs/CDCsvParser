package at.msd.friehs_bicha.cdcsvparser.core

import at.msd.friehs_bicha.cdcsvparser.App

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.app.AppStatus
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.AppTypeIdentifier
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.transactions.CCDBTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.DBTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionData
import at.msd.friehs_bicha.cdcsvparser.ui.display.incompleteHistoryNotes
import at.msd.friehs_bicha.cdcsvparser.util.FileUtil
import at.msd.friehs_bicha.cdcsvparser.util.FirebaseUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CCDBWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.DBWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletData
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletXmlSerializer
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher
import at.msd.friehs_bicha.cdcsvparser.util.buildCollectionsMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * The two unpriced chart series of the C++ core, posted next to the card
 * map on every C++-path post. Single source of truth for the parse-screen
 * chart panel: a recreated fragment refills its panel from the replayed
 * post, exactly like the cards.
 *
 * @property daily  daily accounting series: "series;YYYY-MM-DD;value"
 *                  lines (series = spent|value|pl|bonus), oldest first;
 *                  value/pl/bonus are EUR at the CURRENT prices and the
 *                  last point of each equals the matching card total.
 * @property wallet per-wallet series (historical chart pricing):
 *                  "CURRENCY;YYYY-MM-DD;runningBalance;runningBonus" token
 *                  lines, unpriced - the chart code values each point at
 *                  the prices valid at its time.
 */
data class ChartSeries(
    val daily: List<String>,
    val wallet: List<String>,
)

class CoreService : Service() {

    // Which incarnation of the service this is (gen 1, 2, …). Paired with
    // the trigger-side "Import dispatched" line from the caller, the
    // generation id adjudicates a parse stall from the log alone: intent
    // never reached the service vs. service destroyed before the queued
    // launch dispatched (the two suspects behind #54).
    val generation: Int = nextServiceGeneration()

    private val serviceScope = CoroutineScope(SupervisorJob() + coreDispatcher)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        FileLog.i(TAG, "Service created (gen $generation)")
        // Executor liveness per service generation: at gen n+1 (a service
        // restart WITHIN one process) this line proves whether the shared
        // cpp-core thread survived the previous service death -
        // 'alive=false' would be the wedge signature (every later import
        // queues forever, no error, no log).
        val coreThreadAtGen = coreThread
        FileLog.i(
            TAG,
            "Core executor liveness: ${coreThreadAtGen?.let { "'${it.name}' alive=${it.isAlive}" } ?: "not created yet"} (gen $generation)"
        )
    }

    override fun onDestroy() {
        FileLog.i(TAG, "Service destroyed (gen $generation)")
        CoreService.service = null
        // The dead service's own scope is cancelled (its undelivered
        // coroutines die with it), but the shared cpp-core executor is
        // app-lifetime and deliberately survives the service (KDoc on
        // coreExecutor).
        serviceScope.cancel()
        super.onDestroy()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoreService.service = this
        if (intent == null) {
            FileLog.w(TAG, "Initialization failed. Intent is null.")
            return super.onStartCommand(null, flags, startId)
        }

        // BEFORE the launch: if this line is missing while "Import
        // dispatched" is present, the intent never reached the component.
        FileLog.i(TAG, "Service start command: ${intent.action} (gen $generation)")
        serviceScope.launch {
            when (intent.action) {
                ACTION_START_SERVICE -> {
                    handleStartService()
                }

                ACTION_START_SERVICE_WITH_DATA -> {
                    handleStartServiceWithData(intent)
                }

                ACTION_START_SERVICE_WITH_FIREBASE_DATA -> {
                    handleStartServiceWithFirebaseData(intent)
                }

                ACTION_SAVE_DATA_TO_FIREBASE -> {
                    handleSaveDataToFirebase()
                }

                ACTION_STOP_SERVICE -> {
                    FileLog.d(TAG, "Stopping service.")
                    stopSelf()
                }

                else -> {
                    FileLog.w(TAG, "Initialization failed. Unknown action: ${intent.action}")
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Saves the data to Firebase
     */
    private suspend fun handleSaveDataToFirebase() {
        user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.e(TAG, "User is null")
            return
        }

        //wait until walletNames is set (bounded, so we can never spin forever)
        try {
            withTimeout(30_000) {
                while (walletNames.value == null) {
                    FileLog.v(TAG, "Waiting for walletNames to be set.")
                    delay(500)
                }
            }
        } catch (e: TimeoutCancellationException) {
            FileLog.e(TAG, "Timed out waiting for walletNames to be set.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }

        saveToFireBase()
    }

    /**
     * Loads the data from Firebase
     */
    private suspend fun handleStartServiceWithFirebaseData(intent: Intent) {
        loadFromFirebase()
        try {
            withTimeout(90_000) {
                while (!isRunning) {
                    FileLog.d(TAG, "Waiting for FB initialization to finish.")
                    delay(500)
                }
            }
        } catch (e: TimeoutCancellationException) {
            FileLog.e(TAG, "Timed out waiting for Firebase load to finish.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        replaceClearsCombinedState()
        provideDataToActivity()
    }

    /**
     * A plain single import or a cloud restore REPLACES the live model:
     * the persisted combined-view selection and the duplicate-skip count
     * of the last combined run no longer describe what is loaded.
     * Runs on the cpp-core thread (like every model-replacing path).
     */
    private fun replaceClearsCombinedState() {
        lastDuplicateSkips = 0L
        PreferenceHelper.setCombinedSelection(App.instance, emptyList())
    }

    /**
     * Loads the data from Firebase
     */
    private fun handleStartServiceWithData(intent: Intent) {
        val mode = intent.getIntExtra("mode", 0)
        // Large CSVs (e.g. BlockPit exports) do not fit into a binder parcel,
        // so the caller passes the file name (the file always exists in
        // private app storage) and we read the lines here.
        // Combined (multi-file) import: the caller passes the history file
        // names; the pipeline builds the union model HERE, on this thread
        // (the init-then-append order is a cpp-core-thread invariant).
        val combinedFileNames = intent.getStringArrayListExtra("combined")
        if (combinedFileNames != null) {
            FileLog.i(TAG, "Parsing combined import: ${combinedFileNames.size} file(s), mode $mode (gen $generation)")
            parseCombinedSelection(combinedFileNames, mode)
            return
        }
        // First statement: if "Service start command" is present but this
        // line is not, the queued launch was cancelled before it dispatched.
        val pendingFile = intent.getStringExtra("file")
        val pendingLines = intent.getStringArrayListExtra("data")?.size ?: 0
        FileLog.i(TAG, "Parsing import: ${pendingFile ?: "$pendingLines lines"}, mode $mode (gen $generation)")
        val data = when {
            intent.getStringExtra("file") != null -> {
                val fileName = intent.getStringExtra("file")!!
                try {
                    FileUtil.getFileContent(File(filesDir, fileName))
                } catch (e: IOException) {
                    FileLog.e(TAG, "Reading $fileName from storage failed: $e")
                    errorCounter.postValue((errorCounter.value ?: 0) + 1)
                    return
                }
            }

            intent.getStringArrayListExtra("data") != null ->
                intent.getStringArrayListExtra("data")!!

            else -> {
                FileLog.e(TAG, "Initialization with data failed. Data is null.")
                return
            }
        }
        val appTypeFromSettings = AppType.safeFromOrdinal(mode)
        val appType = detectAppType(data, appTypeFromSettings)
        if (appType != appTypeFromSettings) {
            FileLog.i(TAG, "File content indicates $appType; using it instead of the selected type $appTypeFromSettings.")
        }
        if (appType == AppType.BlockPit && !isCoreInitialized) {
            FileLog.e(TAG, "BlockPit files can only be parsed with the C++ core.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        if (appType == AppType.Kraken && !isCoreInitialized) {
            FileLog.e(TAG, "Kraken files can only be parsed with the C++ core.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        // The standard family is core-enforced: the Kotlin island's escape
        // parse is retired from the live paths, so a standard-family file
        // without an initialized core is a guarded refusal (the BlockPit /
        // Kraken twin - no partial parse, no empty model; the localized
        // parse-screen note rides the error counter).
        if ((appType == AppType.CdCsvParser || appType == AppType.Default) && !isCoreInitialized) {
            FileLog.e(TAG, "Standard files can only be parsed with the C++ core.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        when (isCoreInitialized) {
            true -> {
                val coreMode = CoreModeMapper.toCoreMode(appType)
                if (coreMode == null) {
                    FileLog.e(TAG, "AppType $appType is not supported by the C++ core.")
                    errorCounter.postValue((errorCounter.value ?: 0) + 1)
                    return
                }
                val dataArray = Array<String>(data.size) { i -> data[i] }
                if (initWithData(dataArray, data.size, coreMode, logFilePath, PARSE_BUDGET_MS)) {
                    FileLog.d(TAG, "Initialization with data successful.")
                    isRunning = true
                    lastFailedLines = getFailedLines().coerceAtLeast(0)
                    // Read right after the parse, still on the core thread:
                    // the cut state is per-thread and must be consumed
                    // before any other core work (same discipline as the
                    // failed-line count above).
                    lastParseBudgetCut = wasLastParseBudgetCut()
                    lastParseUnprocessedRows = getLastParseUnprocessedRows().coerceAtLeast(0)
                    if (lastParseBudgetCut) {
                        FileLog.w(TAG, "C++ core parse cut by the budget: $lastParseUnprocessedRows row(s) unprocessed.")
                        // Persist the cut for the history list marker: the
                        // note above only lives on the parse screen, the
                        // chip must survive leaving it. A file-name import
                        // has a name to mark; a data-array import (restore)
                        // has none and stays unmarked.
                        if (pendingFile != null) {
                            PreferenceHelper.recordPartialImport(App.instance, setOf(pendingFile))
                        }
                    } else if (pendingFile != null) {
                        // This file completed: a same-name upload that was
                        // cut before is now whole, so its marker goes with
                        // the re-import (otherwise the chip would mark a
                        // complete file as partial).
                        PreferenceHelper.clearPartialImport(App.instance, setOf(pendingFile))
                    }
                    if (lastFailedLines > 0) {
                        FileLog.w(TAG, "C++ core skipped $lastFailedLines unparsable CSV line(s).")
                    }
                } else {
                    // The C++ side already logged the reason (e.g. "JNI
                    // initWithData failed: Transactions is empty") to core.log.
                    FileLog.w(TAG, "Initialization with data failed (${data.size} lines, mode $coreMode, type $appType).")
                    errorCounter.postValue(errorCounter.value?.plus(1) ?: 1)
                    return
                }
                checkAndSetModes()
                // Persist the parsed C++ state for the next fast start
                // - this runs on the core thread (serviceScope is
                // the cpp-core dispatcher). A failed save only degrades
                // future fast starts; the parse itself succeeded.
                if (!save(savePath)) {
                    FileLog.w(TAG, "Saving the C++ state for fast start failed; the next fast start will fall back to the local model.")
                }
                replaceClearsCombinedState()
            }

            false -> {
                // The C++ core is the only parse path: without the native
                // library nothing can be parsed - the guarded refusal. The
                // standard / BlockPit / Kraken families already refused
                // above; this arm catches what they do not (card types on a
                // broken install - never an uncaught throw).
                FileLog.e(TAG, "The C++ core is unavailable; the import cannot be parsed.")
                errorCounter.postValue((errorCounter.value ?: 0) + 1)
            }
        }

        provideDataToActivity()

    }

    /**
     * Resolves the AppType a file's content asks for: a distinctive header
     * decides, independent of the (possibly stale) app type selected in the
     * settings. For the shared CDC/Card header the first data line decides
     * (card rows carry kind 0), so a stale or mismatched selection can no
     * longer turn a card file into an all-line-failed crypto parse (or
     * vice versa). Unknown headers: the settings decide.
     */
    private fun detectAppType(data: List<String>, appTypeFromSettings: AppType): AppType {
        val firstLine = data.firstOrNull()
        val headerType = AppTypeIdentifier.getAppType(firstLine)
        return when {
            headerType != null -> headerType
            // Shared CDC/Card header: the content decides. Card transactions
            // carry the plain kind 0, crypto rows a known kind label - a
            // content-based pick works for any selection (stale ones
            // included) and for card-family types the core cannot parse
            // (CurveCard).
            AppTypeIdentifier.isCdCardFamily(firstLine) ->
                if (AppTypeIdentifier.looksLikeCardData(
                        data.drop(1).firstOrNull { it.isNotBlank() }
                    )
                ) AppType.CroCard else AppType.CdCsvParser

            else -> appTypeFromSettings
        }
    }

    /**
     * Checks which modes are available and sets the variables accordingly (cpp)
     */
    private fun checkAndSetModes() {
        when (getModes()) {
            1 -> {
                hasCryptoTx = true
                hasCardTx = false
            }

            2 -> {
                hasCryptoTx = false
                hasCardTx = true
            }

            3 -> {
                hasCryptoTx = true
                hasCardTx = true
            }
        }
    }

    /**
     * Provides the data to the activity
     *
     * The C++ core is the only post path: with the native library loaded
     * the core's state is posted; without it there is no model to post -
     * the guarded refusal, the same surface as the parse guards.
     */
    private fun provideDataToActivity() {
        if (isCoreInitialized) {
            provideDataToActivityFromCppCore()
        } else {
            FileLog.e(TAG, "The C++ core is unavailable; no model can be posted.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
        }
    }
    @OptIn(DelicateCoroutinesApi::class)
    private fun provideDataToActivityFromCppCore() {
        calculateWalletBalances()

        // Wallets up front (before the card post below): the
        // incomplete-history note needs them with FINAL balances, and
        // the LiveData posts further down reuse this very list
        // (transactions attach there), so this is the ONLY core round
        // trip for wallets per post.
        val wallets = getWalletsAsString()
        val wallets_ = ArrayList<Wallet>()
        wallets.forEach {
            val data = WalletXmlSerializer().deserializeFromXml(it)
            wallets_.add(Wallet(data))
        }
        // Incomplete history: token wallets with a negative FINAL
        // balance - the partial-export signal (the early rows, e.g. the
        // first purchase, are missing). The detector excludes the
        // never-flagged families itself (card / fiat / outside);
        // empty list = the parse-screen note row stays off, which a
        // complete export always is for token wallets.
        val incompleteHistory = incompleteHistoryNotes(wallets_)

        val currencies = getCurrencies().toList()
        // Price pass, phase 1: a pure cache read - NO network on the
        // cpp-core thread (a cold 163-symbol cache used to block this
        // thread 10-70 s). The whole post below then renders in
        // milliseconds, priced from the cache (stale entries included,
        // by design); the phase-2 fetch at the end re-posts fresh values.
        val cachePrices = priceProvider.getPricesFromCache(currencies)
        val prices = Array<Double>(currencies.size) { i -> cachePrices[currencies[i]] ?: 0.0 }
        setPrice(prices)

        // Offline valuation note: while the connection flag says offline,
        // which era the numbers below carry - last cached prices (any age)
        // and/or the static baseline table. Online or fresh-only: the
        // helper returns null and the screen shows no note.
        val offlinePriceNote = priceProvider.offlinePriceNote(currencies)

        // Partial-import note: the per-parse cut state read on this thread
        // after the import. It survives the price re-posts of the SAME
        // import and resets with the next one (the core resets it at the
        // start of every parse), so a completed import posts no note.
        val parseBudgetNote = if (lastParseBudgetCut) {
            ParseBudgetNote(
                unprocessedRows = lastParseUnprocessedRows,
                budgetSeconds = (PARSE_BUDGET_MS / 1000).toInt(),
            )
        } else null

        var totalMoneySpent = getTotalMoneySpent()
        val totalMoneySpentString =
            StringHelper.formatAmountToString(totalMoneySpent)
        // Mixed model (both scopes live at once): the 2x2 money-spent
        // card disambiguates scope with a chip. The card-scope and the
        // combined (both-scopes) totals ride the SAME post as the wallet
        // scope; null in every single-scope model, which is what hides
        // the chip. Spend is a ledger fact - no price involved, so all
        // three price states below carry identical scope texts.
        val cardMoneySpentText: String?
        val blendedMoneySpentText: String?
        if (hasCryptoTx && hasCardTx) {
            val cardSpent = getTotalMoneySpentCard()
            cardMoneySpentText = StringHelper.formatAmountToString(cardSpent)
            blendedMoneySpentText =
                StringHelper.formatAmountToString(totalMoneySpent + cardSpent)
        } else {
            cardMoneySpentText = null
            blendedMoneySpentText = null
        }
        // Set in the offline branch below: there the 5 s retry loop owns
        // the recovery, so the phase-2 fetch must not stack on top of it.
        var offlinePlaceholder = false

        val parseData = if (hasCryptoTx) {
            // A symbol is "known" when the cache holds ANY entry for it
            // (fresh, stale, or a 0.0 "no value" answer); EUR is pinned.
            // Known prices mean the cards can show real numbers right now
            // (even a previous provider failure never blanks a cached
            // model - stale beats "no internet").
            val allPricesKnown =
                currencies.all { it == "EUR" || cachePrices.containsKey(it) }

            when {
                allPricesKnown -> {
                    val amountOfAsset = getValueOfAssets()
                    ParseData.standard(
                        moneySpentText = totalMoneySpentString,
                        assetsText = StringHelper.formatAmountToString(amountOfAsset),
                        rewardsText = StringHelper.formatAmountToString(getTotalBonus()),
                        profitLossText = StringHelper.formatAmountToString(
                            amountOfAsset - totalMoneySpent
                        ),
                        offlinePriceNote = offlinePriceNote,
                        parseBudgetNote = parseBudgetNote,
                        incompleteHistory = incompleteHistory,
                        cardMoneySpent = cardMoneySpentText,
                        blendedMoneySpent = blendedMoneySpentText,
                    )
                }

                AssetValue.getInstance().isRunning -> {
                    // The provider is healthy but some prices are not
                    // cached yet: transient placeholder; the phase-2
                    // fetch re-posts the real values.
                    FileLog.d(TAG, "Some prices are still loading; posting the placeholder.")
                    ParseData.standard(
                        moneySpentText = totalMoneySpentString,
                        assetsText = "prices loading",
                        rewardsText = "prices loading",
                        profitLossText = "prices loading",
                        offlinePriceNote = offlinePriceNote,
                        parseBudgetNote = parseBudgetNote,
                        incompleteHistory = incompleteHistory,
                        cardMoneySpent = cardMoneySpentText,
                        blendedMoneySpent = blendedMoneySpentText,
                    )
                }

                else -> {
                    offlinePlaceholder = true
                    FileLog.e(TAG, "AssetValue is not running")
                    AssetValue.getInstance().check()
                    //check periodically if internet is back (on the cpp-core thread,
                    //because provideDataToActivityFromCppCore touches JNI)
                    serviceScope.launch {
                        var checks = 0
                        while (!AssetValue.getInstance().isRunning) {
                            checks++
                            if (checks > 36) { // ~3 minutes: give up and keep the placeholder
                                FileLog.w(TAG, "Waiting for internet connection gave up.")
                                return@launch
                            }
                            FileLog.d(TAG, "Waiting for internet connection.")
                            delay(5000)
                            AssetValue.getInstance().check()
                        }
                        provideDataToActivityFromCppCore()
                    }
                    ParseData.standard(
                        moneySpentText = totalMoneySpentString,
                        assetsText = WalletAssets.NO_INTERNET,
                        rewardsText = WalletAssets.NO_INTERNET,
                        profitLossText = WalletAssets.NO_INTERNET,
                        offlinePriceNote = offlinePriceNote,
                        parseBudgetNote = parseBudgetNote,
                        incompleteHistory = incompleteHistory,
                        cardMoneySpent = cardMoneySpentText,
                        blendedMoneySpent = blendedMoneySpentText,
                    )
                }
            }
        } else {
            // Files without crypto (card/fiat scope): only "money spent"
            // exists, the attribution footer is hidden (ParseData.card).
            totalMoneySpent = getTotalMoneySpentCard()
            ParseData.card(StringHelper.formatAmountToString(totalMoneySpent), parseBudgetNote)
        }
        parsedDataLiveData.postValue(parseData)

        // The chart rows ride the SAME post as the cards: the parse
        // screen observes them instead of a one-shot side query, so a
        // recreated fragment can never miss data the cards already got.
        // This function always runs on the cpp-core thread with the core
        // settled (parse completion, phase-2 price re-post, stale-price
        // refresh), so the rows are always fresh.
        val dailyRows: List<String> = getDailySeries()?.toList() ?: emptyList()
        val walletRows: List<String> = getDailyWalletSeries()?.toList() ?: emptyList()
        FileLog.i(TAG, "chart series post: ${dailyRows.size} daily, ${walletRows.size} wallet rows")
        chartSeriesLiveData.postValue(ChartSeries(dailyRows, walletRows))

        //get transactions from core and set it to the LiveData
        val transactions = getTransactionsAsString()
        val cardTransactions = getCardTransactionsAsStrings()
        val transactions_ = ArrayList<Transaction>()
        val cardTransactions_ = ArrayList<CroCardTransaction>()
        transactions.forEach {
            val txData =
                TransactionData()
            val dataString = it.replace("\n\t", "")
            try {
                txData.fromXml(dataString)
                transactions_.add(Transaction(txData))
            } catch (e: Exception) {
                FileLog.e(TAG, "Exception: $e")
            }
        }
        transactionsLiveData.postValue(transactions_)
        cardTransactions.forEach {
            val txData =
                TransactionData()
            val dataString = it.replace("\n\t", "")
            try {
                txData.fromXml(dataString)
                cardTransactions_.add(CroCardTransaction(txData))
            } catch (e: Exception) {
                FileLog.e(TAG, "Exception: $e")
            }
        }
        cardTransactionsLiveData.postValue(cardTransactions_)


        // Standard wallets were fetched at the top of this function
        // (the note needs them before the card post); only the card
        // wallets come in here, and the transactions attach to the
        // SAME wallet objects as before.
        val cardWallets = getCardWalletsAsStrings()
        val cardWallets_ = ArrayList<Wallet>()
        transactions_.forEach { tx ->
            wallets_.find { it.walletId == tx.walletId }?.transactions?.add(tx)
        }
        cardWallets.forEach {
            val data = WalletXmlSerializer().deserializeFromXml(it)
            cardWallets_.add(CroCardWallet(data))
        }
        cardTransactions_.forEach { tx ->
            cardWallets_.find { it.walletId == tx.walletId }?.transactions?.add(tx)
        }

        walletsLiveData.postValue(wallets_)
        outsideWalletsLiveData.postValue(wallets_.filter { it.isOutsideWallet } as ArrayList<Wallet>)
        cardWalletsLiveData.postValue(cardWallets_)
        val allWalets = mutableListOf<Wallet>()
        allWalets.addAll(wallets_)
        allWalets.addAll(cardWallets_)
        allWalletsLiveData.postValue(allWalets.toCollection(ArrayList()))

        val walletNames_ = Array<String?>(wallets_.size + cardWallets_.size) { _ -> null }
        val indexAll = wallets_.size
        wallets_.forEachIndexed { index, wallet ->
            walletNames_[index] = wallet.getTypeString()
        }
        cardWallets_.forEachIndexed { index, wallet ->
            walletNames_[index + indexAll] = wallet.getTypeString()
        }
        walletNames.postValue(walletNames_)
        val assetMaps_ = ArrayList<AssetEntry>()
        wallets_.forEach {
            assetMaps_.add(AssetEntry(it.walletId, getAssetMap(it.walletId)))
        }
        cardWallets_.forEach {
            assetMaps_.add(AssetEntry(it.walletId, getCardAssetMap(it.walletId)))
        }
        assetMaps.postValue(assetMaps_)

        // Same data the LiveData posts above (explicit: the `.value` of a
        // postValue is not visible on the core thread yet).
        saveToRoomsDB(
            wallets_, transactions_,
            cardWallets_ as ArrayList<CroCardWallet>, cardTransactions_
        )

        // Price pass, phase 2: fetch what the cache is missing on the
        // price-check thread (network never on the cpp-core thread) and
        // re-run this function for the re-post. A warm pass fetches
        // nothing (no callback, no re-run); the offline branch keeps its
        // own 5 s retry loop and skips this.
        if (hasCryptoTx && !offlinePlaceholder) {
            priceProvider.fetchPricesBulkAsync(currencies) {
                // The service can be destroyed while the fetch runs; the
                // re-post is pointless without it.
                val running = CoreService.service
                if (running != null) {
                    coreExecutor.submit { running.provideDataToActivityFromCppCore() }
                }
            }
        }
    }

    private fun getCardAssetMap(walletId: Int): WalletAssets {
        // Card buckets are fiat: the C++ path only carries "money
        // spent"; the other three rows are hidden (null) - the same shape
        // the old map posted (explicit nulls).
        return WalletAssets.card(StringHelper.formatAmountToString(getMoneySpentByWID(walletId)))
    }

    /**
     * Persists the just-posted model data to Room.
     *
     * The collections are passed explicitly (the same ones the caller
     * [postValue]'d): reading the LiveData `.value` back on the core thread
     * would race the main-thread delivery of `postValue` and could observe
     * a null/stale state - that is what made the empty-state guard
     * below misfire and skipped real saves).
     */
    private fun saveToRoomsDB(
        wallets: List<Wallet>?,
        transactions: List<Transaction>?,
        cardWallets: List<CroCardWallet>?,
        cardTransactions: List<CroCardTransaction>?,
    ) {
        // A constraint conflict here must never kill the app: the in-memory
        // (C++ core + LiveData) state stays the source of truth and the next
        // parse retries the save.
        // Never wipe a stored model with an empty re-import. An empty
        // state here (e.g. an empty parse result) carries no transactions,
        // and the delete-before-insert would destroy the previously parsed
        // rows (the fast-start local fallback reads exactly from Room), so
        // the old model is kept.
        if ((transactions ?: emptyList()).isEmpty() && (cardTransactions ?: emptyList()).isEmpty()) {
            FileLog.w(TAG, "saveToRoomsDB: no transactions in the current state; keeping the stored model.")
            return
        }
        try {
            val walletDao = App.instance.db.walletDao()
            val txDao = App.instance.db.transactionDao()
            val cardWalletDao = App.instance.db.cardWalletDao()
            val cardTransactionDao = App.instance.db.cardTransactionDao()

            // Delete before re-insert (children first, FK constraint): rows
            // whose ids were never re-imported must not survive as stale data.
            txDao.deleteAll()
            walletDao.deleteAll()
            cardTransactionDao.deleteAll()
            cardWalletDao.deleteAll()

            wallets?.let {
                walletDao.insertAll(it)
            }
            transactions?.let {
                txDao.insertAll(it)
            }
            cardWallets?.let {
                cardWalletDao.insertAll(it)
            }
            cardTransactions?.let {
                cardTransactionDao.insertAll(it)
            }
            PreferenceHelper.setIsAppModelSavedLocal(App.instance, true)
            FileLog.d(TAG, "Data saved to RoomsDB")
        } catch (e: Exception) {
            FileLog.e(TAG, "Failed to save data to Room DB: $e")
        }
    }

    /**
     * Returns the amounts of the asset in the wallet
     *
     * @return the amount of the asset in the wallet
     */
    private fun getAssetMap(walletId: Int): WalletAssets {
        val moneySpent = getMoneySpentByWID(walletId)
        return WalletAssets.standard(
            moneySpentText = StringHelper.formatAmountToString(moneySpent),
            assetsText = StringHelper.formatAmountToString(getValueOfAssetsByWID(walletId)),
            rewardsText = StringHelper.formatAmountToString(getTotalBonusByWID(walletId)),
            profitLossText = StringHelper.formatAmountToString(
                getValueOfAssetsByWID(walletId) - moneySpent
            ),
            pricesRunning = AssetValue.getInstance().isRunning,
        )
    }

    /**
     * Initializes the Core
     */
    private fun handleStartService() {
        when (isCoreInitialized) {
            true -> {
                if (init(logFilePath, savePath)) {
                    // Fast start (C++ mode): init() loaded the persisted
                    // C++ state - the wallet balances and the per-wallet
                    // transaction lists, but NOT the applied
                    // per-transaction ledger series, so the daily series
                    // are still empty here. Post the loaded state from the
                    // core first (cash-priced from the cache, no series),
                    // then re-parse the last imported CSV in the background
                    // and re-post from the C++ core: the daily series and
                    // the chart appear when the rebuild finishes (a few
                    // seconds for normal exports). Both posts queue on the
                    // single cpp-core thread, so the order is guaranteed.
                    FileLog.d(TAG, "Initialization successful.")
                    isRunning = true
                    provideDataToActivity()
                    val csvFile = latestImportedCsv()
                    if (csvFile == null) {
                        FileLog.w(TAG, "Fast start (C++): no imported CSV to rebuild from; showing the loaded values only.")
                    } else {
                        rebuildCppStateFrom(csvFile)
                    }
                } else {
                    // No usable saved C++ state (a first fast start before
                    // any parse save, or a corrupt save): nothing to show -
                    // re-importing the last CSV rebuilds the state.
                    FileLog.w(TAG, "No saved C++ state; fast start has no values to show.")
                    errorCounter.postValue((errorCounter.value ?: 0) + 1)
                }
            }

            false -> {
                // The C++ core is the only parse and provide path: without
                // the native library no model can be served (a saved Room
                // model is legacy persistence - re-importing the CSV
                // rebuilds it through the core).
                FileLog.e(TAG, "The C++ core is unavailable; fast start needs it.")
                errorCounter.postValue((errorCounter.value ?: 0) + 1)
            }
        }
    }

    /**
     * The newest CSV in private storage (the last uploaded /
     * imported file - the history dialog lists exactly these). The fast-
     * start C++ rebuild reparses it, because the persisted C++ state
     * restores the transaction lists but not the applied
     * per-transaction ledger series behind the daily chart.
     */
    private fun latestImportedCsv(): File? =
        filesDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.filterNot { it.length() == 0L }
            ?.maxByOrNull { it.lastModified() }

    /**
     * Reparses [csvFile] into the C++ core (background rebuild
     * behind the fast start), persists the resulting state and re-posts
     * the overview from the C++ core: the daily chart rows only exist
     * after this re-parse. Any failure keeps the previously posted
     * leg-1 values (the loaded state) in place - nothing is zeroed.
     */
    private fun rebuildCppStateFrom(csvFile: File) {
        serviceScope.launch {
            val data = try {
                FileUtil.getFileContent(csvFile)
            } catch (e: IOException) {
                FileLog.e(TAG, "Fast-start rebuild: reading ${csvFile.name} failed: $e")
                return@launch
            }
            // Same header resolution as a single import: a distinctive header
            // decides; for the shared CDC/card header the first data line
            // decides (the plain sniffer is deliberately non-decisive for it,
            // and the saved-model fast start must still re-parse the standard
            // family, whose summary cards read the core post).
            val appTypeFromSettings = PreferenceHelper.getSelectedType(App.instance)
            val appType = detectAppType(data, appTypeFromSettings)
            if (appType == null) {
                FileLog.w(TAG, "Fast-start rebuild: header unknown, keeping the posted values.")
                return@launch
            }
            val coreMode = CoreModeMapper.toCoreMode(appType)
            if (coreMode == null) {
                FileLog.w(TAG, "Fast-start rebuild: $appType unsupported by the C++ core, keeping the local values.")
                return@launch
            }
            val dataArray = Array<String>(data.size) { i -> data[i] }
            if (initWithData(dataArray, data.size, coreMode, logFilePath, PARSE_BUDGET_MS)) {
                // Consume the cut state of THIS re-parse: the fast-start leg
                // re-derives everything from the CSV, so the note must
                // describe this parse, not the pre-restart import.
                lastParseBudgetCut = wasLastParseBudgetCut()
                lastParseUnprocessedRows = getLastParseUnprocessedRows().coerceAtLeast(0)
                if (lastParseBudgetCut) {
                    FileLog.w(TAG, "Fast-start rebuild cut by the budget: $lastParseUnprocessedRows row(s) unprocessed.")
                }
                checkAndSetModes()
                if (!save(savePath)) {
                    FileLog.w(TAG, "Saving the rebuilt C++ state failed.")
                }
                FileLog.d(TAG, "Fast start (C++): background rebuild finished; re-posting from the C++ core.")
                provideDataToActivity()
            } else {
                FileLog.w(TAG, "Fast start (C++): background rebuild failed, keeping the posted leg-1 values.")
            }
        }
    }

    /**
     * Combined import of a multi-file selection (the union view): file 1
     * goes through the normal init path (REPLACE, the C++ side), files
     * 2..N are appended to the live core - the core skips and COUNTS
     * rows that are exact-tuple duplicates of rows already present (date,
     * description, amount, currency), so overlapping selections never
     * double-account. Each file's parse mode is detected from its content
     * as for a single import, so a mixed card + crypto selection lands in
     * the right buckets.
     *
     * Runs on the cpp-core thread. A file whose type the core cannot
     * parse (or that yields zero rows) is counted in the error counter
     * and the remaining files still land; a first-file failure aborts the
     * whole selection (without an init there is nothing to append). The
     * failed-line counts are accumulated per file, the duplicate count is
     * read once at the end (the manager is fresh after the init). On
     * success the normal post-parse post (prices + LiveData) happens
     * exactly ONCE over the union.
     *
     * [fileNames] are file names in [filesDir] (the history entries' stable
     * names); [settingsMode] is the AppType ordinal the settings currently
     * carry - the fallback for files without a distinctive header, exactly
     * as for a single import.
     */
    fun parseCombinedSelection(fileNames: List<String>, settingsMode: Int) {
        if (!isCoreInitialized) {
            FileLog.w(TAG, "Combined selection needs the C++ core path; refusing ${fileNames.size} file(s).")
            errorCounter.postValue(errorCounter.value?.plus(1) ?: 1)
            return
        }
        if (fileNames.size < 2) {
            FileLog.w(TAG, "Combined selection needs at least 2 files, got ${fileNames.size}.")
            return
        }
        FileLog.i(TAG, "Parsing combined selection: ${fileNames.size} file(s) -> one union model")
        val appTypeFromSettings = AppType.safeFromOrdinal(settingsMode)

        val contents = ArrayList<List<String>>(fileNames.size)
        for (name in fileNames) {
            val data = try {
                FileUtil.getFileContent(File(filesDir, name))
            } catch (e: IOException) {
                FileLog.e(TAG, "Combined selection: reading $name from storage failed: $e")
                errorCounter.postValue(errorCounter.value?.plus(1) ?: 1)
                return
            }
            contents.add(data)
        }

        // File 1: the normal init path (REPLACE semantics).
        val firstMode = CoreModeMapper.toCoreMode(detectAppType(contents[0], appTypeFromSettings))
        if (firstMode == null) {
            FileLog.e(TAG, "Combined selection: the first file has no supported core mode.")
            errorCounter.postValue(errorCounter.value?.plus(1) ?: 1)
            return
        }
        val firstArray = Array(contents[0].size) { i -> contents[0][i] }
        if (!initWithData(firstArray, contents[0].size, firstMode, logFilePath, PARSE_BUDGET_MS)) {
            FileLog.w(TAG, "Combined selection: initialization with the first file failed; aborting.")
            errorCounter.postValue(errorCounter.value?.plus(1) ?: 1)
            return
        }
        var totalFailedLines = getFailedLines().coerceAtLeast(0)
        // Per-file budget units: every entry point gets the full budget, so
        // more than one file of the selection can be cut - accumulate. The
        // CUT NAMES feed the history marker: the chip is per file, and a
        // combined cut can hit any subset of the selection.
        var cutAny = wasLastParseBudgetCut()
        var totalUnprocessed =
            if (cutAny) getLastParseUnprocessedRows().coerceAtLeast(0) else 0
        val cutFileNames = mutableSetOf<String>()
        if (cutAny) cutFileNames += fileNames[0]
        // Every file that actually parsed (replace or append): the ones
        // of these that were NOT cut are whole in the model now, so any
        // earlier same-name marker is obsolete after this selection.
        val parsedFileNames = mutableSetOf(fileNames[0])

        // Files 2..N: the dedup append. The TM carries each file's own
        // failed-line count after the call, so accumulate per success.
        var skippedFiles = 0
        for (i in 1 until fileNames.size) {
            val data = contents[i]
            val coreMode = CoreModeMapper.toCoreMode(detectAppType(data, appTypeFromSettings))
            if (coreMode == null) {
                FileLog.e(TAG, "Combined selection: ${fileNames[i]} has no supported core mode, skipped.")
                skippedFiles++
                continue
            }
            val array = Array(data.size) { j -> data[j] }
            if (appendWithData(array, data.size, coreMode, logFilePath, PARSE_BUDGET_MS)) {
                totalFailedLines += getFailedLines().coerceAtLeast(0)
                parsedFileNames += fileNames[i]
                if (wasLastParseBudgetCut()) {
                    cutAny = true
                    totalUnprocessed += getLastParseUnprocessedRows().coerceAtLeast(0)
                    cutFileNames += fileNames[i]
                }
            } else {
                FileLog.w(TAG, "Combined selection: ${fileNames[i]} added no rows, skipped.")
                skippedFiles++
            }
        }

        val duplicateSkips = getDuplicateSkips()
        if (totalFailedLines > 0) {
            FileLog.w(TAG, "Combined selection skipped $totalFailedLines unparsable line(s) altogether.")
        }
        FileLog.i(TAG,
            "Combined selection done: ${fileNames.size} file(s), $skippedFiles skipped, " +
                "$totalFailedLines failed line(s), $duplicateSkips duplicate row(s) skipped.")

        // The success state, readable by the UI: the skip count for the
        // one-shot parse-screen note, and the selection for the badge +
        // the pre-checked re-open after a restart.
        lastDuplicateSkips = duplicateSkips
        lastParseBudgetCut = cutAny
        lastParseUnprocessedRows = totalUnprocessed
        if (cutAny) {
            FileLog.w(TAG, "Combined selection cut by the budget: $totalUnprocessed row(s) unprocessed in total.")
        }
        PreferenceHelper.setCombinedSelection(App.instance, fileNames)
        if (cutFileNames.isNotEmpty()) {
            PreferenceHelper.recordPartialImport(App.instance, cutFileNames)
        }
        val completeNames = parsedFileNames - cutFileNames
        if (completeNames.isNotEmpty()) {
            PreferenceHelper.clearPartialImport(App.instance, completeNames)
        }

        isRunning = true
        checkAndSetModes()
        if (!save(savePath)) {
            FileLog.w(TAG, "Saving the combined C++ state for fast start failed; the next fast start will fall back to the local model.")
        }
        provideDataToActivity()
    }

    /**
     * Loads the data from Firebase.
     * Runs the (JNI-touching) loadFromMap on the cpp-core thread by
     * suspending here instead of hopping to the Firestore callback thread.
     */
    private suspend fun loadFromFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            FileLog.e(TAG, "loadFromFirebase: user is null")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        val db = Firebase.firestore

        val userMapDeferred = CompletableDeferred<HashMap<String, Any>?>()
        FirebaseUtil.getUserDataFromFirestore(
            uid,
            db
        ) { result ->
            userMapDeferred.complete(result.getOrNull())
        }

        // Bounded wait: a missing document / network failure must not
        // block the core thread forever.
        val userMap: HashMap<String, Any>? = try {
            withTimeout(60_000) {
                userMapDeferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            FileLog.e(TAG, "Timed out waiting for the Firestore user document.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        if (userMap == null) {
            FileLog.e(TAG, "Firebase user document missing or unreadable.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
            return
        }
        // The SDK byte fields (the cipher payload + salt) cross into the
        // cipher layer as plain byte arrays.
        loadFromMap(FirebaseUtil.toPlainByteDoc(userMap))
        FileLog.i("FirebaseUtil", "User data loaded successfully")
    }

    /**
     * Loads the data from the fetched user document (the HashMap).
     */
    private suspend fun loadFromMap(userMap: HashMap<String, Any>) {
        userMap.let {
            val appSettingsMap = it["appSettings"] as? HashMap<String, Any>
            if (appSettingsMap == null) {
                FileLog.e(TAG, "loadFromMap: appSettings missing in user document")
                errorCounter.postValue((errorCounter.value ?: 0) + 1)
                return
            }
            val appSettings = AppSettings().fromHashMap(appSettingsMap)
            // The dbVersion gate runs on the CLEAR settings, before any
            // decrypt attempt.
            if (!appSettings.compareVersionsWithDefault()) {
                FileLog.e(TAG, "Version mismatch")
                return
            }
            // The cipher layer, next to the version gate: a document with
            // the current cipher version is decrypted on the IO pool
            // (KDF/GCM must not run on the core thread); a document
            // WITHOUT cipher metadata is a legacy plaintext document and
            // reads through exactly as before. Anything unreadable is an
            // explicit error state - never a partial load.
            val effective: Map<String, Any> = when (
                val resolved = withContext(Dispatchers.IO) {
                    CoreCipher.resolveDocPayload(it) { keyModel, salt, iterations ->
                        FirebaseUtil.keyForDocMetadata(keyModel, salt, iterations)
                    }
                }
            ) {
                is CoreCipher.DocPayloadResult.Ready -> resolved.payload
                is CoreCipher.DocPayloadResult.Unreadable -> {
                    val text = if (resolved.reason == CoreCipher.UnreadableReason.CIPHER_TOO_NEW) {
                        App.instance.getString(R.string.cloud_cipher_too_new)
                    } else {
                        App.instance.getString(R.string.cloud_cannot_decrypt)
                    }
                    FileLog.e(TAG, "loadFromMap: cloud document unreadable: ${resolved.reason}")
                    Toast.makeText(App.instance, text, Toast.LENGTH_LONG).show()
                    errorCounter.postValue((errorCounter.value ?: 0) + 1)
                    return
                }
            }
            hasCryptoTx = appSettings.hasCryptoTx == "true"
            hasCardTx = appSettings.hasCardTx == "true"
            val dbWallets = effective["wallets"] as? ArrayList<HashMap<String, *>>
            val dbOutsideWallets = effective["outsideWallets"] as? ArrayList<HashMap<String, *>>
            val dbTransactions = effective["transactions"] as? ArrayList<HashMap<String, *>>
            val dbCardWallets = effective["cardWallets"] as? ArrayList<HashMap<String, *>>
            val dbCardTransactions =
                effective["cardTransactions"] as? ArrayList<HashMap<String, *>>?

            PreferenceHelper.setUseStrictType(
                App.instance,
                appSettings.useStrictType
            )

            if (dbCardWallets.isNullOrEmpty() && dbCardTransactions.isNullOrEmpty()) {
                hasCardTx = false
            }
            if (dbWallets.isNullOrEmpty() && dbOutsideWallets.isNullOrEmpty() && dbTransactions.isNullOrEmpty()) {
                hasCryptoTx = false
            }

            if (hasCryptoTx) {
                when (isCoreInitialized) {
                    true -> {
                        val txXMl = mutableListOf<String>()
                        val walletXml = mutableListOf<String>()

                        dbTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            txXMl.add(
                                TransactionData.fromTransaction(Transaction.fromDb(hashMap)).toXml()
                            )
                        })
                        dbWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            walletXml.add(
                                WalletXmlSerializer().serializeToXml(
                                    WalletData.fromWallet(
                                        CDCWallet.fromDb(hashMap)
                                    )
                                )
                            )
                        })
                        dbOutsideWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            walletXml.add(
                                WalletXmlSerializer().serializeToXml(
                                    WalletData.fromWallet(
                                        CDCWallet.fromDb(hashMap)
                                    )
                                )
                            )
                        })

                        val dataArray = Array<String>(txXMl.size) { i -> txXMl[i] }
                        val walletArray = Array<String>(walletXml.size) { i -> walletXml[i] }

                        init(logFilePath, "")
                        // The set*Data calls below are pure appenders: clear first, so
                        // the restore REPLACES the live core state. The order is
                        // load-bearing: clearAll() throws (DataHolder has no
                        // TransactionManager yet) when no TM exists, so init() must
                        // run first - on an initialized core it is a no-op.
                        clearAll()

                        setTransactionData(dataArray)
                        setWalletData(walletArray)

                    }

                    false -> {
                        // The island rehydrate is retired with the standard
                        // escape switch: without the native library the cloud
                        // document cannot be applied - the guarded refusal.
                        FileLog.e(TAG, "Cloud restore needs the C++ core, which is unavailable.")
                        errorCounter.postValue((errorCounter.value ?: 0) + 1)
                        return
                    }
                }
            }
            if (hasCardTx && hasCryptoTx) {
                when (isCoreInitialized) {
                    true -> {
                        dbCardWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardWalletData(
                                arrayOf(
                                    WalletXmlSerializer().serializeToXml(
                                        WalletData.fromWallet(
                                            CroCardWallet.fromDb(
                                                hashMap
                                            )
                                        )
                                    )
                                )
                            )
                        })
                        dbCardTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardTransactionData(
                                arrayOf(
                                    TransactionData.fromTransaction(
                                        CroCardTransaction.fromDb(
                                            hashMap
                                        )
                                    ).toXml()
                                )
                            )
                        })
                    }

                    // Unreachable: the crypto half above already refused
                    // the restore when the core is unavailable.
                    false -> {}
                }
            }
            if (hasCardTx) {
                when (isCoreInitialized) {
                    true -> {

                        // "" on purpose: a live Firebase import must start
                        // from an empty core, never from the fast-start
                        // save state. clearAll() enforces it - the set*Data
                        // calls below are pure appenders, and it must come
                        // AFTER init() (it throws when no TM exists; init on
                        // an initialized core is a no-op).
                        init(logFilePath, "")
                        clearAll()

                        dbCardWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardWalletData(
                                arrayOf(
                                    WalletXmlSerializer().serializeToXml(
                                        WalletData.fromWallet(
                                            CroCardWallet.fromDb(
                                                hashMap
                                            )
                                        )
                                    )
                                )
                            )
                        })
                        dbCardTransactions?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                            setCardTransactionData(
                                arrayOf(
                                    TransactionData.fromTransaction(
                                        CroCardTransaction.fromDb(
                                            hashMap
                                        )
                                    ).toXml()
                                )
                            )
                        })
                    }

                    false -> {
                        // The C++ core is the only card path: without it the
                        // cloud document cannot be applied - the guarded
                        // refusal (the old island rehydrate is retired).
                        FileLog.e(TAG, "Cloud restore needs the C++ core, which is unavailable.")
                        errorCounter.postValue((errorCounter.value ?: 0) + 1)
                        return
                    }
                }
            }
            isInitialized = true
            isRunning = true
        }
    }


    /**
     * The held symbols (the core's currency list, INCLUDING the internal
     * "EUR" cash buffer). Runs on the cpp-core thread (the JNI rule) -
     * callers go through the companion wrappers that hop onto that
     * thread.
     */
    internal fun heldCurrencies(): List<String> =
        getCurrencies()?.toList() ?: emptyList()

    /**
     * Pushes prices into the core in [currencies] order (the setPrice
     * contract: same order as the held list). Runs on the cpp-core
     * thread (the JNI rule).
     */
    internal fun pushPricesToCore(currencies: List<String>, prices: Map<String, Double>) {
        setPrice(Array<Double>(currencies.size) { index -> prices[currencies[index]] ?: 0.0 })
    }

    private external fun init(logFilePath: String, savePath: String): Boolean
    private external fun getFailedLines(): Int
    private external fun wasLastParseBudgetCut(): Boolean
    private external fun getLastParseUnprocessedRows(): Int

    private external fun initWithData(
        data: Array<String>,
        dataSize: Int,
        mode: Int,
        logFilePath: String,
        parseBudgetMs: Long
    ): Boolean

    private external fun appendWithData(
        data: Array<String>,
        dataSize: Int,
        mode: Int,
        logFilePath: String,
        parseBudgetMs: Long
    ): Boolean

    private external fun getDuplicateSkips(): Long

    private external fun save(savePath: String): Boolean
    private external fun load(savePath: String)

    private external fun getModes(): Int

    private external fun setWalletData(walletData: Array<String>)
    private external fun setTransactionData(transactionData: Array<String>)
    private external fun setCardWalletData(cardWalletData: Array<String>)
    private external fun setCardTransactionData(cardTransactionData: Array<String>)
    private external fun clearAll()

    private external fun calculateWalletBalances()

    private external fun getCurrencies(): Array<String>

    private external fun setPrice(prices: Array<Double>)

    private external fun getTotalMoneySpent(): Double
    private external fun getValueOfAssets(): Double
    private external fun getTotalBonus(): Double

    /**
     * getTotalValueOfAssetsCard
     */
    private external fun getTotalMoneySpentCard(): Double

    private external fun getTransactionsAsString(): Array<String>
    private external fun getWalletsAsString(): Array<String>

    private external fun getCardTransactionsAsStrings(): Array<String>
    private external fun getCardWalletsAsStrings(): Array<String>

    private external fun getValueOfAssetsByWID(walletID: Int): Double
    private external fun getTotalBonusByWID(walletID: Int): Double
    private external fun getMoneySpentByWID(walletId: Int): Double


    companion object {

        private const val TAG = "CoreService"
        private const val CORE_THREAD_NAME = "cpp-core"

        // Monotonic incarnation counter for the "gen n" log lines (#54
        // stall adjudication); survives across service restarts.
        @Volatile
        private var serviceGeneration = 0
        private fun nextServiceGeneration(): Int = ++serviceGeneration

        // Hard wall-clock budget for one full parse in the C++ core: comfortably
        // above a real parse (the 2444-line BlockPit export takes well under a
        // second) and far below the 5-minute UI watchdog.
        private const val PARSE_BUDGET_MS = 120_000L

        /**
         * Money spent (same accounting as the "Geld ausgegeben" card, EUR inner
         * wallet excluded) bucketed by calendar month, oldest first:
         * "YYYY-MM;123.45". Runs on the cpp-core thread.
         */
        suspend fun moneySpentSeries(): List<String> = withContext(coreDispatcher) {
            if (!isRunning) return@withContext emptyList()
            getMoneySpentSeries()?.toList() ?: emptyList()
        }

        private external fun getMoneySpentSeries(): Array<String>

        private external fun getDailySeries(): Array<String>

        private external fun getDailyWalletSeries(): Array<String>

        /**
         * Money spent bucketed into fixed [windowSeconds] slots from the
         * epoch (UTC): "YYYY-MM-DD HH:MM;value" lines (flow), oldest first.
         * [fromEpoch] (0 = no limit) suppresses slots starting before it.
         * The 15-min and 6-hour chart frames run this with
         * fromEpoch = 0 - the P/L inception reference and the Σ view need
         * the pre-window slots; the JVM windows the visible flow line with
         * ChartData.windowedSpentFlow. Only non-zero-flow slots are
         * emitted, so the transfer stays sparse for long exports. Runs on
         * the cpp-core thread.
         */
        suspend fun subDailySpentSeries(windowSeconds: Int, fromEpoch: Long): List<String> =
            withContext(coreDispatcher) {
                if (!isRunning) return@withContext emptyList()
                getSubDailySpentSeries(windowSeconds, fromEpoch)?.toList() ?: emptyList()
            }

        private external fun getSubDailySpentSeries(
            windowSeconds: Int,
            fromEpoch: Long,
        ): Array<String>

        /**
         * Per-wallet UNPRICED token series in fixed [windowSeconds] slots:
         * "CURRENCY;YYYY-MM-DD HH:MM;runningBalance;runningBonus" (the
         * windowed sibling of [dailyWalletSeries]). Same scope, running state
         * carries across gaps and across the [fromEpoch] cut. The chart code
         * prices each point with the price valid at the slot.
         *
         * A LIMITED window (grid-aligned [fromEpoch] > 0, a
         * multiple of windowSeconds) additionally OPENS with one line per
         * currency whose running position is non-zero at the window start
         * (the pre-cut state at the fromEpoch slot key, before that slot's
         * own deltas) - without it, a position held before the window would
         * be unrecoverable (the rows carry running values, not deltas). A
         * fully stale export yields only those opening lines; [fromEpoch] =
         * 0 emits no opening line.
         *
         * Runs on the cpp-core thread.
         */
        suspend fun subDailyWalletSeries(windowSeconds: Int, fromEpoch: Long): List<String> =
            withContext(coreDispatcher) {
                if (!isRunning) return@withContext emptyList()
                getSubDailyWalletSeries(windowSeconds, fromEpoch)?.toList() ?: emptyList()
            }

        private external fun getSubDailyWalletSeries(
            windowSeconds: Int,
            fromEpoch: Long,
        ): Array<String>

        /**
         * The static baseline EUR price for [symbol] (the C++ canon -
         * the single source of the 144-symbol offline table); 0.0 for
         * unknown symbols. Runs on the cpp-core thread (the JNI rule);
         * the C++ side is a stateless table read, and the caller
         * ([AssetValue]) memoizes per symbol (the table is read-only for
         * the process lifetime).
         */
        fun staticBaselinePrice(symbol: String): Double = onCoreThread {
            getStaticPrice(symbol)
        }

        private external fun getStaticPrice(symbol: String): Double

        /**
         * The user-owned balance sheet (liabilities): user-maintained
         * entries whose current balance the CORE computes (closed-form
         * amortization) and which the CORE persists in its own save
         * (the additive v5 section - the app has no separate store for
         * it). The serde line is the contract (see the core's
         * BalanceSheet.h); [LiabilityLine] builds and parses it on the
         * JVM. The core triggers its own (atomic) save on a mutation
         * when a save dir is known, so an entry survives the next cold
         * start without any extra save here. Runs on the cpp-core
         * thread; a rejected line is reported as false (the sheet stays
         * untouched).
         */
        fun sheetUpsert(line: String): Boolean = onCoreThread {
            sheetSet(line)
        }

        fun sheetDeleteEntry(kindId: String): Boolean = onCoreThread {
            sheetDelete(kindId)
        }

        fun sheetLines(kind: String): List<String> = onCoreThread {
            sheetList(kind)?.toList() ?: emptyList()
        }

        /**
         * The net-worth snapshot line(s) of the core's balance sheet (the
         * read-only contract the net-worth screen shows; [NetWorthSnapshot]
         * parses the NW line on the JVM). The values are CORE-computed -
         * the core's own current EUR wallet value (the internal EUR cash
         * buffer INCLUDED), the declared asset sum and the current
         * liability balances - so the display cannot drift from the core.
         * Runs on the cpp-core thread; EMPTY when no live core (the
         * not-ready window, the screen hides its card then).
         */
        fun netWorthSnapshot(): List<String> = onCoreThread {
            sheetSnapshot()?.toList() ?: emptyList()
        }

        /**
         * The full CSV export of the core state as ONE file string (the
         * core owns the serializer and the WHOLE format: header, the
         * 9-column transaction table of every wallet, the balance-sheet
         * section and the export stamp). The caller is a byte-transport
         * only - write the bytes verbatim. Runs on the cpp-core thread;
         * EMPTY when no live core (the not-ready window).
         */
        fun csvExport(): String? = onCoreThread {
            exportCsv()
        }

        /**
         * The held symbols a price alert may target: the core's
         * currency list minus the internal "EUR" cash buffer (an alert
         * on the cash buffer is meaningless - its price is pinned to
         * 1.0). Runs on the cpp-core thread; empty when the core is
         * not live.
         */
        fun heldAlertSymbols(): List<String> = onCoreThread {
            val running = service
            if (running == null || !isRunning) return@onCoreThread emptyList()
            running.heldCurrencies().filter { it != "EUR" }
        }

        /**
         * The core's alert pass (the latched crossing state machine,
         * core-owned): pushes this process's current cache prices into
         * the core (the same order contract as the in-app price pass),
         * then lets the core evaluate the sheet's price alerts (the
         * core's current prices - the same hierarchy the valuation
         * uses) and the goal-achieved state against the goal progress.
         * Returns the FIRED lines the core built (`FIRED;PRICE;...` /
         * `FIRED;GOAL;...`) for
         * [at.msd.friehs_bicha.cdcsvparser.alerting.Notifier] to
         * deliver; the state, the thresholds and the math never cross
         * the ABI.
         *
         * Call it ONLY after a price pass that produced fresh values:
         * a no-op pass (e.g. a 429 blackout tick) must not call it -
         * the alerts then wait for the next real pass (documented).
         * Empty when the core is not live or nothing fired. Runs on the
         * cpp-core thread.
         */
        fun alertPass(): List<String> = onCoreThread {
            val running = service
            if (running == null || !isRunning) return@onCoreThread emptyList()
            val currencies = running.heldCurrencies()
            if (currencies.isEmpty()) return@onCoreThread emptyList()
            val cachePrices = priceProvider.getPricesFromCache(currencies)
            running.pushPricesToCore(currencies, cachePrices)
            alertCheck()?.toList() ?: emptyList()
        }

        /**
         * The core's portfolio-return metric (TWR + MWR/IRR): the
         * two-line metric result for the priced daily series lines
         * (`R;<YYYY-MM-DD>;<V>;<CF>` - the core's metric contract owns
         * the line format, the period/skip guards and the INSUFFICIENT
         * markers). The math is pure core side; the lines are the only
         * input. Returns the two metric lines (TWR first), EMPTY when
         * no line of [series] is valid. Runs on the cpp-core thread.
         */
        fun returnMetrics(series: List<String>): List<String> = onCoreThread {
            val result = computeReturns(series.joinToString("\n")) ?: ""
            if (result.isEmpty()) emptyList() else result.split("\n")
        }

        /**
         * The "delete all data" action (the in-app data-deletion
         * entry point): runs every [DataWipe.STEPS] exactly in order,
         * one side effect per step. The core step is the single
         * cpp-core-thread section (the TM reset + the re-post of the
         * emptied state); every other step is a plain file / DB / store
         * operation off that thread.
         *
         * A failing step does NOT abort the rest of the wipe: the
         * stores are independent (a Room failure must not leave the
         * caches and the history behind), and the result reports what
         * the core step managed to clear. The Firebase account and its
         * cloud document are NOT touched - the wipe is client-side
         * state only (the account-delete path owns the cloud side).
         */
        suspend fun deleteAllData(): DataWipe.Result {
            var coreStateCleared = false
            var filesDeleted = 0
            for (step in DataWipe.STEPS) {
                when (step) {
                    DataWipe.Step.CORE_STATE ->
                        coreStateCleared = onCoreThread {
                            val running = service
                            if (running != null && isCoreInitialized && isRunning) {
                                running.clearAll()
                                // The mode flags mirror the core's
                                // getModes(); the "no data at all" value
                                // has no when-arm there, so after the
                                // reset both scopes are gone and are
                                // dropped explicitly (companion state -
                                // [checkAndSetModes] sets them the same way).
                                hasCryptoTx = false
                                hasCardTx = false
                                // The Kotlin-side per-import state of the
                                // LAST parse is stale after the reset
                                // (the core resets its own at the start
                                // of the next parse, which no longer
                                // describes a wiped app).
                                lastFailedLines = 0
                                lastParseBudgetCut = false
                                lastParseUnprocessedRows = 0
                                lastDuplicateSkips = 0L
                                // Converge the UI: post the emptied core
                                // state so the screens hold the
                                // no-data state instead of the model
                                // that was just wiped.
                                running.provideDataToActivity()
                                true
                            } else {
                                FileLog.w(
                                    TAG,
                                    "deleteAllData: no live core to clear - the on-disk stores are wiped anyway.",
                                )
                                false
                            }
                        }

                    DataWipe.Step.CORE_SAVE_FILES,
                    DataWipe.Step.PRICE_CACHE_FILES,
                    DataWipe.Step.HISTORY_FILES ->
                        withContext(Dispatchers.IO) {
                            filesDeleted += when (step) {
                                // The whole fast-start save dir (the core
                                // writes exactly its three state files
                                // there; deleting the directory content
                                // covers any of them present).
                                DataWipe.Step.CORE_SAVE_FILES ->
                                    File(savePath).listFiles()
                                        ?.filter { it.isFile }
                                        ?.count { it.delete() } ?: 0

                                DataWipe.Step.PRICE_CACHE_FILES ->
                                    DataWipe.PRICE_CACHE_FILE_NAMES.count {
                                        File(App.instance.filesDir, it).delete()
                                    }

                                // The upload history list is exactly the
                                // CSV files in the app's files dir (the
                                // history dialog lists the same set).
                                else ->
                                    App.instance.filesDir
                                        .listFiles { f -> f.isFile && f.name.endsWith(".csv") }
                                        ?.count { it.delete() } ?: 0
                            }
                        }

                    DataWipe.Step.ROOM ->
                        withContext(Dispatchers.IO) {
                            // The same children-first order as the Room
                            // save (the FK constraints).
                            val db = App.instance.db
                            db.transactionDao().deleteAll()
                            db.walletDao().deleteAll()
                            db.cardTransactionDao().deleteAll()
                            db.cardWalletDao().deleteAll()
                        }

                    DataWipe.Step.DATASTORE -> PreferenceHelper.clearAllStored(App.instance)

                    DataWipe.Step.PRICE_CACHE_MEMORY -> AssetValue.clearPriceCache()
                }
            }
            FileLog.i(
                TAG,
                "deleteAllData done: core=${if (coreStateCleared) "cleared" else "no live core"}, " +
                    "files deleted=$filesDeleted",
            )
            return DataWipe.Result(coreStateCleared, filesDeleted)
        }

        private external fun sheetSet(line: String): Boolean

        private external fun sheetDelete(kindId: String): Boolean

        private external fun sheetList(kind: String): Array<String>

        private external fun sheetSnapshot(): Array<String>

        private external fun exportCsv(): String?

        private external fun alertCheck(): Array<String>?

        private external fun computeReturns(series: String): String?

        /**
         * All core work - and in particular every JNI call into the C++
         * core - runs on this single "cpp-core" thread. The C++
         * TransactionManager is not thread-safe, so the whole JNI surface
         * must stay on one thread; heavy Kotlin-core work (parsing, DB
         * saves) uses it as well.
         *
         * APP-LIFETIME INVARIANT: created once per process and NEVER shut
         * down - the service's onDestroy deliberately leaves it running.
         * The C++ state lives for the process lifetime on this thread; a
         * mid-process shutdown (as on a service death) would silently
         * wedge every later import: tasks queue on the dead executor, are
         * never dispatched, and leave no error and no log (the 2026-10-29
         * on-device stall). The thread is a daemon, so it costs nothing
         * at process exit.
         */
        @Volatile
        private var coreThread: Thread? = null
        private val coreExecutor = Executors.newSingleThreadExecutor {
            Thread(it, CORE_THREAD_NAME).apply {
                isDaemon = true
                coreThread = this
            }
        }
        internal val coreDispatcher: CoroutineDispatcher = coreExecutor.asCoroutineDispatcher()

        // Lines skipped by the C++ core in the last import (mirrors the
        // Kotlin core's amountTxFailed)
        @Volatile internal var lastFailedLines: Int = 0

        // The hard parse budget cut the last import short (per-parse core
        // state, read on the core thread right after the import). With
        // [lastParseUnprocessedRows] > 0 the parser stopped reading the
        // input; with 0 the wallet aggregation stopped. The core resets
        // both at the start of every parse.
        @Volatile internal var lastParseBudgetCut: Boolean = false
        @Volatile internal var lastParseUnprocessedRows: Int = 0

        // Duplicate rows the C++ core skipped in the LAST COMBINED
        // import (the fresh-TM counter; every plain single import
        // resets it to 0, the manager is new after each init).
        @Volatile internal var lastDuplicateSkips: Long = 0L

        /**
         * Runs [block] on the core thread, blocking the caller until it
         * returns. Passes through when already on the core thread.
         */
        fun <T> onCoreThread(block: () -> T): T {
            if (Thread.currentThread().name == CORE_THREAD_NAME) return block()
            val future = coreExecutor.submit(block)
            val error = try {
                return future.get(30, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.ExecutionException) {
                e.cause ?: e
            }
            throw error
        }

        @Volatile var isCoreInitialized = false

        /** The running service instance (set from [CoreService.onStartCommand]). */
        @Volatile private var service: CoreService? = null

        /**
         * Wallet/transaction rows read prices from the 5-minute-TTL cache on
         * the main thread. Once that TTL gap starts, the rows fall back to
         * stale values; this re-runs the cache-aware bulk price pass on the
         * core thread so fresh values re-appear without the user re-parsing.
         * (The provider-side cooldowns make this a fast-fail, not a storm.)
         */
        fun refreshPricesIfStale() {
            val assetValues = AssetValue.getInstance()
            if (assetValues.hasFreshPrices()) return
            // An empty cache means nothing was parsed yet in this session;
            // there is no core state to re-price.
            if (assetValues.cacheKeys().isEmpty()) return
            val running = service ?: return
            FileLog.d(TAG, "Price cache stale; re-running the bulk price pass.")
            coreExecutor.submit { running.provideDataToActivityFromCppCore() }
        }
        @Volatile var isInitialized = false

        @Volatile var isRunning = false

        @Volatile var hasCardTx = false
        @Volatile var hasCryptoTx = false

        var dataLiveData = MutableLiveData<List<String>>()

        var parsedDataLiveData = MutableLiveData<ParseData>()
        var chartSeriesLiveData = MutableLiveData<ChartSeries>()

        var walletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var outsideWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var cardWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        var allWalletsLiveData = MutableLiveData<ArrayList<Wallet>>()
        val walletNames = MutableLiveData<Array<String?>>()
        val transactionsLiveData = MutableLiveData<ArrayList<Transaction>>()
        val cardTransactionsLiveData = MutableLiveData<ArrayList<CroCardTransaction>>()

        val assetMaps = MutableLiveData<ArrayList<AssetEntry>>()

        val errorCounter = MutableLiveData<Int>()


        var priceProvider: AssetValue = AssetValue.getInstance()
        @Volatile var user = FirebaseAuth.getInstance().currentUser

        val path: String
            get() = App.instance.filesDir.absolutePath
        /**
         * Directory of the persisted C++ state (fast start).
         * Trailing slash required: the core writes `<dirPath> +
         * "wallets" | "cardWallets" | "state"`.
         */
        val savePath: String
            get() = "$path/save/"
        val logFilePath: String
            get() = "$path/log/core.log"

        init {
            dataLiveData.value = ArrayList()
            assetMaps.value = ArrayList()
            //mkdir for save
            try {
                System.loadLibrary("cdcsvparser")
                isCoreInitialized = true
                isInitialized = true
                FileLog.d(TAG, "Core library loaded.")
            } catch (e: UnsatisfiedLinkError) {
                FileLog.e(TAG, "Failed to load native library: ${e.message}")
            }
        }

        /**
         * Starts the CoreService for the fast-start path: plain
         * `ACTION_START_SERVICE`, no settings writes - the C++ core is the
         * only compute path (the core-mode choice is retired).
         */
        fun startServiceFromLocal() {
            val intent = Intent(App.instance, CoreService::class.java)
            intent.action = ACTION_START_SERVICE
            App.instance.startService(intent)
        }

        /**
         * Starts the CoreService with the data.
         *
         * If [fileName] (a file in the app's private storage) is given it is
         * passed instead of the lines: binder transactions are size-capped
         * (~512 KB), which large CSV exports exceed.
         */
        fun startServiceWithData(data: ArrayList<String>?, mode: Int, fileName: String? = null) {
            check(data != null || fileName != null) {
                "startServiceWithData needs data or a file name"
            }
            val intent = Intent(App.instance, CoreService::class.java)
            intent.action = ACTION_START_SERVICE_WITH_DATA
            val file = fileName
            when {
                file != null -> intent.putExtra("file", file)
                else -> intent.putExtra("data", data!!)
            }
            intent.putExtra("mode", mode)
            App.instance.startService(intent)
        }

        /**
         * Starts the CoreService with a COMBINED multi-file import: the
         * history file names (private storage) go over the binder, the
         * service builds the union model (init + dedup appends, on the
         * cpp-core thread). [mode] is the settings' AppType ordinal,
         * the per-file fallback for files without a distinctive header.
         */
        fun startCombinedSelection(fileNames: List<String>, mode: Int) {
            check(fileNames.size >= 2) {
                "startCombinedSelection needs at least 2 file names"
            }
            val intent = Intent(App.instance, CoreService::class.java)
            intent.action = ACTION_START_SERVICE_WITH_DATA
            intent.putStringArrayListExtra("combined", ArrayList(fileNames))
            intent.putExtra("mode", mode)
            App.instance.startService(intent)
        }


        /**
         * Returns the typed wallet display state of the wallet (all rows
         * hidden when the wallet is not in the posted list yet - the old
         * emptyMap behavior).
         */
        fun getAssetMap(walletId: Int): WalletAssets {
            return assetMaps.value?.find { it.walletId == walletId }?.assets
                ?: WalletAssets()
        }


        /**
         * Tell service to save the data to Firebase
         */
        fun saveDataToFirebase() {
            if (isCoreInitialized) {
                FileLog.i("FirebaseUtil", "Saving data to Firebase")
                App.instance.startService(
                    Intent(
                        App.instance,
                        CoreService::class.java
                    ).apply {
                        action = ACTION_SAVE_DATA_TO_FIREBASE
                    })
                return
            }
            // The C++ core is the only save source: without the native
            // library there is nothing to save - the guarded refusal.
            FileLog.e(TAG, "Saving to Firebase needs the C++ core, which is unavailable.")
            errorCounter.postValue((errorCounter.value ?: 0) + 1)
        }

        /**
         * Saves the data to Firebase as the canonical ENCRYPTED document:
         * `appSettings` (incl. `dbVersion`) in the clear so the version
         * gate can run before any decrypt attempt, plus the
         * wallet/transaction collections as one AES-256-GCM blob with
         * the cipher metadata (version / key model / KDF salt). The
         * mapping + the encryption (incl. the password-model KDF) run on
         * the IO pool - never on the core thread.
         */
        private suspend fun saveToFireBase() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                FileLog.e(TAG, "saveToFireBase: user is null")
                Toast.makeText(App.instance, R.string.error_saving_data, Toast.LENGTH_SHORT).show()
                return
            }
            val appSettings = AppSettings(
                uid,
                PreferenceHelper.getSelectedType(App.instance),
                PreferenceHelper.getUseStrictType(App.instance)
            )
            if (hasCardTx) appSettings.hasCardTx = "true"
            if (hasCryptoTx) appSettings.hasCryptoTx = "true"

            try {
                val docFields = withContext(Dispatchers.IO) {
                    val dbWallets = java.util.ArrayList<DBWallet>()
                    val dbOutsideWallets = java.util.ArrayList<DBWallet>()
                    val dbCardWallets = java.util.ArrayList<CCDBWallet>()
                    val dbTransactions = java.util.ArrayList<DBTransaction>()
                    val cardTransactions = java.util.ArrayList<CCDBTransaction>()
                    walletsLiveData.value?.forEach { dbWallets.add(DBWallet(it)) }
                    outsideWalletsLiveData.value?.forEach { dbOutsideWallets.add(DBWallet(it)) }
                    cardWalletsLiveData.value?.forEach { dbCardWallets.add(CCDBWallet(it)) }
                    transactionsLiveData.value?.forEach { dbTransactions.add(DBTransaction(it)) }
                    cardTransactionsLiveData.value?.forEach {
                        cardTransactions.add(CCDBTransaction(it))
                    }
                    val collections = buildCollectionsMap(
                        dbWallets, dbOutsideWallets, dbTransactions, dbCardWallets, cardTransactions
                    )
                    FirebaseUtil.buildEncryptedUserDoc(appSettings.toHashMap(), collections)
                }
                FirebaseUtil.writeUserDoc(
                    uid,
                    Firebase.firestore,
                    docFields,
                    App.instance,
                    App.instance.getString(R.string.data_saved_successfully),
                    App.instance.getString(R.string.error_saving_data),
                )
            } catch (e: Exception) {
                FileLog.e(TAG, "saveToFireBase: failed to build the encrypted document: ${e.message}")
                Toast.makeText(App.instance, R.string.error_saving_data, Toast.LENGTH_SHORT).show()
            }
        }


        const val ACTION_START_SERVICE = "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "at.msd.friehs_bicha.cdcsvparser.core.action.STOP_SERVICE"
        const val ACTION_START_SERVICE_WITH_DATA =
            "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE_WITH_DATA"
        const val ACTION_START_SERVICE_WITH_FIREBASE_DATA =
            "at.msd.friehs_bicha.cdcsvparser.core.action.START_SERVICE_WITH_FIREBASE_DATA"
        const val ACTION_SAVE_DATA_TO_FIREBASE =
            "at.msd.friehs_bicha.cdcsvparser.core.action.SAVE_DATA_TO_FIREBASE"

    }
}