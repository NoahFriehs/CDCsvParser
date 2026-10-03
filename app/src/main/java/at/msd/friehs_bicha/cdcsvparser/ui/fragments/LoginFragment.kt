package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvErrorMessage: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        val btnLogin: Button = view.findViewById(R.id.btn_login)
        val btnWithoutLogin: Button = view.findViewById(R.id.btn_without_login)
        val btnForgotPassword: TextView = view.findViewById(R.id.btn_forgot_password)
        val btnSignup: TextView = view.findViewById(R.id.btn_signup)

        auth = FirebaseAuth.getInstance()

        btnWithoutLogin.setOnClickListener {
            PreferenceHelper.setIsFirstStart(requireContext(), false)
            goToMain()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                tvErrorMessage.text = getString(R.string.error_empty_fields)
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        goToMain()
                    } else {
                        tvErrorMessage.text = getString(R.string.error_login_failed)
                    }
                }
        }

        btnSignup.setOnClickListener {
            findNavController().navigate(R.id.signUpFragment)
        }

        btnForgotPassword.setOnClickListener {
            val email = etEmail.text.toString()
            if (email.isEmpty()) {
                tvErrorMessage.text = getString(R.string.error_noEmail)
                return@setOnClickListener
            }
            // Send a password reset email to the user's email address
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(requireContext(), R.string.password_reset_email_sent, Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send password reset email", Toast.LENGTH_SHORT)
                            .show()
                    }
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
