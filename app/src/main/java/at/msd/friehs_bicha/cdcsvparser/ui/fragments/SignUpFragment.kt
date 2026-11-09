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
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.SignUpScreen
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Sign-up screen (Compose surface). Validation + the Firebase
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
            // Sign-up consumed the first-start screen as well.
            PreferenceHelper.setIsFirstStart(requireContext(), false)
            findNavController().popBackStack(R.id.mainFragment, false)
        } else {
            _errorMessage.value = getString(R.string.error_signup_failed) + errorText
        }
    }
}
