package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SignUpScreen
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Sign-up screen (Compose surface, P3.1). Validation + the Firebase
 * create-user call stay here.
 */
class SignUpFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val _errorMessage = mutableStateOf<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    SignUpScreen(
                        onSignup = { email, password -> signUp(email, password) },
                        errorMessage = _errorMessage.value,
                    )
                }
            }
        }
    }

    private fun signUp(email: String, password: String) {
        when {
            email.isEmpty() -> _errorMessage.value =
                getString(R.string.error_signup_email_empty)

            password.isEmpty() -> _errorMessage.value =
                getString(R.string.error_signup_pw_empty)

            password.length < 6 -> _errorMessage.value =
                getString(R.string.error_signup_pw_to_short)

            else -> auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        updateUI(auth.currentUser, "")
                    } else {
                        updateUI(null, task.exception?.message)
                    }
                }
        }
    }

    private fun updateUI(currentUser: FirebaseUser?, errorText: String?) {
        if (currentUser != null) {
            if (!isAdded) return
            // Plan 002: sign-up consumed the first-start screen as well.
            PreferenceHelper.setIsFirstStart(requireContext(), false)
            findNavController().popBackStack(R.id.mainFragment, false)
        } else {
            _errorMessage.value = getString(R.string.error_signup_failed) + errorText
        }
    }
}
