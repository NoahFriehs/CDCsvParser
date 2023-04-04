package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser


class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        auth = FirebaseAuth.getInstance()

        val btnSignup = findViewById<Button>(R.id.btn_signup)
        btnSignup.setOnClickListener {
            val email = findViewById<EditText>(R.id.et_email).text.toString()
            val password = findViewById<EditText>(R.id.et_password).text.toString()
            if (TextUtils.isEmpty(email)) {
                // Handle empty email field
                findViewById<TextView>(R.id.tv_error_message).text = getString(R.string.error_signup_email_empty)
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(password)) {
                // Handle empty password field
                findViewById<TextView>(R.id.tv_error_message).text = getString(R.string.error_signup_pw_empty)
                return@setOnClickListener
            }
            if (false) {
                // Handle email format //TODO
                return@setOnClickListener
            }
            if (password.length < 6) {
                // Handle password length
                findViewById<TextView>(R.id.tv_error_message).text = getString(R.string.error_signup_pw_to_short)
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // Signup successful
                            val user = auth.currentUser
                            updateUI(user, "")
                        } else {
                            // Signup failed
                            updateUI(null, task.exception?.message)
                        }
                    }
        }

    }


    private fun updateUI(currentUser: FirebaseUser?, errorText: String?) {
        if (currentUser != null) {
            // Signup successful, navigate to the home activity
            // Replace HomeActivity with the name of your home activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // Signup failed, show an error message
            // Replace tv_error_message with the ID of your error message TextView
            findViewById<TextView>(R.id.tv_error_message).text = getString(R.string.error_signup_failed) + errorText
        }
    }

}