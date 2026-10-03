package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class SignUpFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_sign_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        view.findViewById<Button>(R.id.btn_signup).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.et_email).text.toString()
            val password = view.findViewById<EditText>(R.id.et_password).text.toString()
            if (TextUtils.isEmpty(email)) {
                view.findViewById<TextView>(R.id.tv_error_message).text =
                    getString(R.string.error_signup_email_empty)
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(password)) {
                view.findViewById<TextView>(R.id.tv_error_message).text =
                    getString(R.string.error_signup_pw_empty)
                return@setOnClickListener
            }
            if (password.length < 6) {
                view.findViewById<TextView>(R.id.tv_error_message).text =
                    getString(R.string.error_signup_pw_to_short)
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
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
            findNavController().popBackStack(R.id.mainFragment, false)
        } else {
            view?.findViewById<TextView>(R.id.tv_error_message)?.text =
                getString(R.string.error_signup_failed) + errorText
        }
    }
}
