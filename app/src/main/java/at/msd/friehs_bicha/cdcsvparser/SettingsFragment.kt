package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SettingsScreen
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.android.material.snackbar.Snackbar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Settings screen (Compose surface, P3.1). Persistence + the Firebase
 * confirm dialogs + logout stay here.
 */
class SettingsFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val snackbarHostState = SnackbarHostState()

    // Settings values are read once into Compose state; writes go straight
    // to PreferenceHelper (the DataStore-backed facade).
    private val _appTypeIndex = mutableStateOf(0)
    private val _coreModeIndex = mutableStateOf(0)
    private val _useStrictType = mutableStateOf(false)
    private val _dataLocal = mutableStateOf(false)
    private val _fastStart = mutableStateOf(false)
    private val _signedIn = mutableStateOf(auth.currentUser != null)
    // Plan 004: runtime price API keys ("" = unset / keyless).
    private val _cgKey = mutableStateOf("")
    private val _ccKey = mutableStateOf("")

    /** The on-disk price cache files (plan 004). */
    private val PRICE_CACHE_FILES = arrayOf("history_prices.json", "symbol_id_cache.json")

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
                        onDeletePriceCaches = { deletePriceCaches() },
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
    }

    /**
     * Plan 004: removes the app's price caches - the two on-disk JSON
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
