package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.LoginScreen
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import androidx.compose.material3.SnackbarHostState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Login screen (Compose surface). The Firebase auth calls and the
 * navigation stay here; the screen itself is `LoginScreen`.
 */
class LoginFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val _errorMessage = mutableStateOf<String?>(null)
    private val snackbarHostState = SnackbarHostState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    LoginScreen(
                        onLogin = { email, password -> signIn(email, password) },
                        onSkipLogin = {
                            PreferenceHelper.setIsFirstStart(requireContext(), false)
                            goToMain()
                        },
                        onSignup = { findNavController().navigate(R.id.signUpFragment) },
                        onForgotPassword = { email -> sendPasswordReset(email) },
                        errorMessage = _errorMessage.value,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // This destination must never offer the settings action. The
        // gear itself comes from the view-scoped MenuProviders of the
        // other fragments; a navigation transition can briefly hold two
        // views alive at once, so this destination removes the item on
        // every menu build within its own view lifetime instead of
        // relying on the leaving fragment's provider to be gone first.
        // View-scoped: it is gone with this view.
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

    private fun signIn(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _errorMessage.value = getString(R.string.error_empty_fields)
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Sign-in consumed the first-start screen -
                    // same side effect as "use without login".
                    PreferenceHelper.setIsFirstStart(requireContext(), false)
                    goToMain()
                } else {
                    _errorMessage.value = getString(R.string.error_login_failed)
                }
            }
    }

    private fun sendPasswordReset(email: String) {
        if (email.isEmpty()) {
            _errorMessage.value = getString(R.string.error_noEmail)
            return
        }
        // Send a password reset email to the user's email address
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                val message = if (task.isSuccessful) {
                    R.string.password_reset_email_sent
                } else {
                    R.string.error_login_failed
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    snackbarHostState.showSnackbar(getString(message))
                }
            }
    }

    /**
     * Login complete (or skipped): back to the main screen, dropping login
     * from the back stack.
     */
    private fun goToMain() {
        if (!isAdded) return
        findNavController().popBackStack(R.id.mainFragment, false)
    }
}
