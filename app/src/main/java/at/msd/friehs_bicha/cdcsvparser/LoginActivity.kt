package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnWithoutLogin: Button
    private lateinit var btnForgotPassword: TextView
    private lateinit var btnSignup: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnSignup = findViewById(R.id.btn_signup)
        tvErrorMessage = findViewById(R.id.tv_error_message)
        btnWithoutLogin = findViewById(R.id.btn_without_login)
        btnForgotPassword = findViewById(R.id.btn_forgot_password)

        auth = FirebaseAuth.getInstance()

        btnWithoutLogin.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                tvErrorMessage.text = getString(R.string.error_empty_fields)
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Login successful, go to main activity
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // Login failed, display error message
                        tvErrorMessage.text = getString(R.string.error_login_failed)
                    }
                }
        }

        btnSignup.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnForgotPassword.setOnClickListener {
            // Get the email address entered by the user
            val email = etEmail.text.toString()

            if (email.isEmpty()) {
                tvErrorMessage.text = getString(R.string.error_noEmail)
                return@setOnClickListener
            }

            // Send a password reset email to the user's email address
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Password reset email sent successfully
                        Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                    } else {
                        // Password reset email failed to send
                        Toast.makeText(this, "Failed to send password reset email", Toast.LENGTH_SHORT).show()
                    }
                }

        }
    }
}
