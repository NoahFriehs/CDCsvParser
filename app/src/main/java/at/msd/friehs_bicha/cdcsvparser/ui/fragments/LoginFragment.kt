package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
 * Login screen (Compose surface, P3.1). The Firebase auth calls and the
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

    private fun signIn(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _errorMessage.value = getString(R.string.error_empty_fields)
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Plan 002: sign-in consumed the first-start screen -
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
