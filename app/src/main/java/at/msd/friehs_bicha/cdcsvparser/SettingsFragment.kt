package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.logging.LogFilter
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SettingsScreen
import at.msd.friehs_bicha.cdcsvparser.util.CloudMigration
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher
import at.msd.friehs_bicha.cdcsvparser.util.ExportFileNaming
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Settings screen (Compose surface). Persistence + the Firebase
 * confirm dialogs + logout stay here.
 */
class SettingsFragment : Fragment() {

    /** The core surface this fragment reads. */
    internal var core: CoreSource = Core
        private set

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val snackbarHostState = SnackbarHostState()

    // SAF create-document for the data export: the user picks the target
    // in the system picker (no storage permissions). Cancel = no-op.
    private val createExportDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            if (uri != null) exportDataTo(uri)
        }

    // Settings values are read once into Compose state; writes go straight
    // to PreferenceHelper (the DataStore-backed facade).
    private val _appTypeIndex = mutableStateOf(0)
    private val _coreModeIndex = mutableStateOf(0)
    private val _useStrictType = mutableStateOf(false)
    private val _dataLocal = mutableStateOf(false)
    private val _fastStart = mutableStateOf(false)
    private val _signedIn = mutableStateOf(auth.currentUser != null)
    // Runtime price API keys ("" = unset / keyless).
    private val _cgKey = mutableStateOf("")
    private val _ccKey = mutableStateOf("")
    // The cloud backup key model (0 = device-bound keystore, 1 = password)
    // + the password of password mode (hidden until that model is selected).
    private val _cloudKeyModelIndex = mutableStateOf(0)
    private val _cloudPassword = mutableStateOf("")

    // The immediate cloud re-encrypt (key-model switch / password
    // change) runs on ONE serial IO lane: the write of one pass is on
    // file before the next pass fetches, so two rapid changes cannot
    // interleave a stale read over a fresh write.
    private val cloudReEncryptScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1)
    )

    // What the cloud document is still protected with (the pre-change
    // credentials of the NEXT re-encrypt): the password value at the
    // LAST COMMIT - the apply button for a password change, the model
    // switch for a key-model change. This is NOT the current pref value:
    // the pref is written LIVE as the user types (every keystroke), so a
    // typed-but-unapplied password already sits in it. Reading the old
    // document under this value + writing under the pref at commit time
    // keeps document and pref in step with no readability window on
    // another device. Seeded from the pref at settings load, updated at
    // the commit point, and carried forward across model switches (which
    // leave the password field itself untouched).
    private val _cloudPasswordLastCommitted = mutableStateOf<String?>(null)
    // The log viewer (file 0 = Kotlin app log, 1 = C++ core log;
    // filter 0 = All, 1 = Warning+, 2 = Error). The lines are read +
    // filtered in [refreshLog] on the IO dispatcher.
    private val _logVisible = mutableStateOf(false)
    private val _logFileIndex = mutableStateOf(0)
    private val _logFilterIndex = mutableStateOf(0)
    private val _logLines = mutableStateOf<List<String>>(emptyList())

    /** The longest tail the viewer reads into memory (2000 lines ≈ 200 KB). */
    private val LOG_TAIL_LINES = 2000

    /** The on-disk price cache files. */
    private val PRICE_CACHE_FILES = arrayOf("history_prices.json", "symbol_id_cache.json")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // #55: the settings destination must never offer the settings
        // action (tapping the gear while already on settings would be a
        // self-navigation). The gear itself comes from the view-scoped
        // MenuProviders of the other fragments; a navigation transition
        // can briefly hold two views alive at once, so this destination
        // removes the item on every menu build within its own view
        // lifetime instead of relying on the leaving fragment's provider
        // to be gone first. View-scoped: it is gone with this view.
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.findItem(R.id.action_settings)?.let { menu.removeItem(it.itemId) }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = false
            },
            viewLifecycleOwner,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        readPreferences()
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    SettingsScreen(
                        appTypeOptions = resources.getStringArray(R.array.appTypes).toList(),
                        appTypeSelected = _appTypeIndex.value,
                        onAppTypeSelected = { index ->
                            _appTypeIndex.value = index
                            PreferenceHelper.setSelectedType(requireContext(), AppType.values()[index])
                        },
                        coreModeOptions = resources.getStringArray(R.array.core_modes).toList(),
                        coreModeSelected = _coreModeIndex.value,
                        onCoreModeSelected = { index ->
                            _coreModeIndex.value = index
                            PreferenceHelper.setUseCpp(requireContext(), index == 0)
                        },
                        useStrictType = _useStrictType.value,
                        strictTypeEnabled = _appTypeIndex.value != AppType.CroCard.ordinal,
                        onUseStrictTypeChange = {
                            _useStrictType.value = it
                            PreferenceHelper.setUseStrictType(requireContext(), it)
                        },
                        dataLocal = _dataLocal.value,
                        onDataLocalChange = {
                            _dataLocal.value = it
                            PreferenceHelper.setIsDataLocal(requireContext(), it)
                        },
                        fastStart = _fastStart.value,
                        fastStartEnabled = _dataLocal.value,
                        onFastStartChange = {
                            _fastStart.value = it
                            PreferenceHelper.setFastStartEnabled(requireContext(), it)
                        },
                        isSignedIn = _signedIn.value,
                        coingeckoKey = _cgKey.value,
                        onCoinGeckoKeyChange = { key ->
                            _cgKey.value = key
                            PreferenceHelper.setCoinGeckoApiKey(requireContext(), key)
                        },
                        cryptocompareKey = _ccKey.value,
                        onCryptoCompareKeyChange = { key ->
                            _ccKey.value = key
                            PreferenceHelper.setCryptoCompareApiKey(requireContext(), key)
                        },
                        cloudKeyModelOptions = listOf(
                            getString(R.string.cloud_key_model_device),
                            getString(R.string.cloud_key_model_password),
                        ),
                        cloudKeyModelSelected = _cloudKeyModelIndex.value,
                        onCloudKeyModelSelected = { index ->
                            if (index != _cloudKeyModelIndex.value) {
                                // The document is still protected by the
                                // credentials of the OLD settings: the
                                // password pref the model switch does not
                                // touch (a keystore-protected document
                                // needs no password at all).
                                val oldPassword =
                                    PreferenceHelper.getCloudPassword(requireContext())
                                _cloudKeyModelIndex.value = index
                                PreferenceHelper.setCloudKeyModel(
                                    requireContext(),
                                    if (index == 1) CoreCipher.KEY_MODEL_PASSWORD else CoreCipher.KEY_MODEL_KEYSTORE,
                                )
                                // Re-encrypt immediately: no window in
                                // which the document is unreadable to its
                                // owner.
                                cloudReEncryptScope.launch {
                                    CloudMigration.reEncryptUnderCurrentSettings(oldPassword)
                                }
                            }
                        },
                        cloudPassword = _cloudPassword.value,
                        onCloudPasswordChange = { password ->
                            _cloudPassword.value = password
                            PreferenceHelper.setCloudPassword(requireContext(), password)
                        },
                        cloudPasswordApplyEnabled =
                            (_cloudPasswordLastCommitted.value != null)
                                && _cloudPasswordLastCommitted.value != _cloudPassword.value,
                        onCloudPasswordApply = {
                            // The commit point of a password change: read
                            // the document under the password it is still
                            // protected with, write it under the new one.
                            val committed = _cloudPasswordLastCommitted.value
                            val current = _cloudPassword.value
                            if (committed != null && committed != current) {
                                cloudReEncryptScope.launch {
                                    CloudMigration.reEncryptUnderCurrentSettings(committed)
                                }
                                _cloudPasswordLastCommitted.value = current
                            }
                        },
                        onDeletePriceCaches = { deletePriceCaches() },
                        logVisible = _logVisible.value,
                        onLogVisibleChange = { visible ->
                            _logVisible.value = visible
                            refreshLog()
                        },
                        logFileLabels = listOf(
                            getString(R.string.log_file_app),
                            getString(R.string.log_file_core),
                        ),
                        logFileSelected = _logFileIndex.value,
                        onLogFileSelected = { index ->
                            _logFileIndex.value = index
                            refreshLog()
                        },
                        logFilterLabels = listOf(
                            getString(R.string.log_filter_all),
                            getString(R.string.log_filter_warning),
                            getString(R.string.log_filter_error),
                        ),
                        logFilterSelected = _logFilterIndex.value,
                        onLogFilterSelected = { index ->
                            _logFilterIndex.value = index
                            refreshLog()
                        },
                        logLines = _logLines.value,
                        onCopyLog = { copyLogLines() },
                        onClearLog = { confirmClearLog() },
                        onExportData = {
                            createExportDocument.launch(
                                ExportFileNaming.suggestedName(LocalDateTime.now()),
                            )
                        },
                        onAbout = { findNavController().navigate(R.id.aboutUsFragment) },
                        onLogin = { findNavController().navigate(R.id.loginFragment) },
                        onLogout = { confirmLogout() },
                        onDeleteAccount = { confirmDeleteAccount() },
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }

    private fun readPreferences() {
        _appTypeIndex.value = PreferenceHelper.getSelectedType(requireContext()).ordinal
        _coreModeIndex.value = if (PreferenceHelper.getUseCpp(requireContext())) 0 else 1
        _useStrictType.value = PreferenceHelper.getUseStrictType(requireContext())
        _dataLocal.value = PreferenceHelper.getIsDataLocal(requireContext())
        _fastStart.value = PreferenceHelper.getFastStartEnabled(requireContext())
        _cgKey.value = PreferenceHelper.getCoinGeckoApiKey(requireContext())
        _ccKey.value = PreferenceHelper.getCryptoCompareApiKey(requireContext())
        _cloudKeyModelIndex.value =
            if (PreferenceHelper.getCloudKeyModel(requireContext()) == CoreCipher.KEY_MODEL_PASSWORD) 1 else 0
        val storedCloudPassword = PreferenceHelper.getCloudPassword(requireContext())
        _cloudPassword.value = storedCloudPassword
        _cloudPasswordLastCommitted.value = storedCloudPassword
    }

    /**
     * Removes the app's price caches - the two on-disk JSON
     * files (deep history + symbol->id map) and the in-memory 5-minute
     * price cache. The CSV imports (user data) and the Room DB are NOT
     * touched. The next parse re-fetches the history; until then the
     * charts fall back to the live prices only.
     */
    private fun deletePriceCaches() {
        val context = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            var deleted = 0
            for (name in PRICE_CACHE_FILES) {
                val file = File(context.filesDir, name)
                if (file.exists() && file.delete()) deleted++
            }
            // The in-memory cache is process-local; no file, no error.
            AssetValue.clearPriceCache()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                snackbarHostState.showSnackbar(
                    getString(
                        if (deleted > 0) R.string.caches_deleted else R.string.caches_already_empty,
                    ),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // No auth listener: the sign-in state cannot change while this
        // screen is open (login/logout happen on their own screens and both
        // navigate away), so a one-off read is correct and avoids a
        // listener that would outlive the fragment.
        _signedIn.value = auth.currentUser != null
    }

    /**
     * SAF result of the data export: the core builds the WHOLE file on
     * the cpp-core thread (its own serializer - the app owns no part of
     * the format), the bytes land untouched at the user's chosen target
     * and no copy is kept. Cancel = no-op (handled at the launcher),
     * not-ready core and write failures = one note row.
     */
    private fun exportDataTo(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val csv = withContext(Dispatchers.IO) { CoreService.csvExport() }
            if (csv == null) {
                if (isAdded) {
                    snackbarHostState.showSnackbar(getString(R.string.export_not_ready))
                }
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver
                        .openOutputStream(uri)
                        ?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("no output stream")
                }
            } catch (e: Exception) {
                FileLog.e("Settings-Export", "CSV export write failed: ${e.message}")
                if (isAdded) {
                    snackbarHostState.showSnackbar(getString(R.string.export_failed))
                }
                return@launch
            }
            if (isAdded) {
                snackbarHostState.showSnackbar(getString(R.string.export_written))
            }
        }
    }

    private fun logFile(fileIndex: Int): File =
        if (fileIndex == 0) FileLog.getLogFiles() else File(core.logFilePath)

    /**
     * Re-reads the chosen log tail on the IO dispatcher and
     * publish the filtered lines. Cheap to call on every toggle.
     */
    private fun refreshLog() {
        if (!_logVisible.value) return
        val fileIndex = _logFileIndex.value
        val mode = LogFilter.Mode.values()[_logFilterIndex.value]
        viewLifecycleOwner.lifecycleScope.launch {
            val lines = withContext(Dispatchers.IO) {
                val file = logFile(fileIndex)
                if (!file.exists() || !file.canRead()) emptyList()
                else file.readLines().takeLast(LOG_TAIL_LINES)
            }
            if (isAdded) _logLines.value = LogFilter.filter(lines, mode)
        }
    }

    private fun copyLogLines() {
        val lines = _logLines.value
        if (lines.isEmpty() || view == null) return
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CDCsvParser log", lines.joinToString("\n")))
        viewLifecycleOwner.lifecycleScope.launch {
            snackbarHostState.showSnackbar(getString(R.string.log_copied))
        }
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.log_clear))
            .setMessage(getString(R.string.log_clear_question))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> clearLogFile() }
            .setNegativeButton(getString(R.string.no), null)
            .create()
            .show()
    }

    private fun clearLogFile() {
        val fileIndex = _logFileIndex.value
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (fileIndex == 0) {
                    FileLog.clearLog()
                } else {
                    val file = logFile(1)
                    if (file.exists()) file.writeText("")
                }
            }
            if (isAdded) {
                _logLines.value = emptyList()
                snackbarHostState.showSnackbar(getString(R.string.log_cleared))
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logoutQuestion))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> logout() }
            .setNegativeButton(getString(R.string.no), null)
            .create()
            .show()
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_account))
            .setMessage(getString(R.string.deleteQuestion))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> deleteUser() }
            .setNegativeButton(getString(R.string.no), null)
            .create()
            .show()
    }

    private fun deleteUser() {
        val user = auth.currentUser
        if (user == null) {
            FileLog.e("Settings-DeleteUser", "deleteUser: user is null")
            return
        }
        val db = Firebase.firestore
        // Delete the data document first and do NOT write a placeholder
        // before it: if the delete fails (offline, rules, quota) the user's
        // document must stay untouched instead of being wiped by the
        // placeholder write.
        db.collection("user").document(user.uid).delete().addOnCompleteListener {
            if (it.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User deleted from database.")
            } else {
                FileLog.e("Settings-DeleteUser", "User could not be deleted from database: ${it.exception}")
            }
        }

        user.delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User account deleted.")
                view?.let { v ->
                    Snackbar.make(v, R.string.user_deleted, Snackbar.LENGTH_LONG).show()
                }
            } else {
                val exception = task.exception
                if (exception != null) {
                    FileLog.d("Settings-DeleteUser", exception.toString())
                }
                if (exception?.toString()?.contains("requires recent authentication") == true) {
                    FileLog.d("Settings-DeleteUser", "User needs to reauthenticate.")
                    view?.let { v ->
                        Snackbar.make(v, R.string.user_needs_to_autheticate, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
        logout()
    }

    private fun logout() {
        if (!isAdded) return
        auth.signOut()
        findNavController().popBackStack(R.id.mainFragment, false)
    }
}
