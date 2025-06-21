package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityLoginBinding
import com.example.confessionapp.ui.viewmodels.LoginViewModel
// Import R class, assuming it will be generated in com.example.confessionapp.R
import com.example.confessionapp.R

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        loginViewModel.anonymousSignInResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Signed in anonymously", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ConfessorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Anonymous sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }

        loginViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnConfessAnonymously.isEnabled = !isLoading
            binding.btnPriestLogin.isEnabled = !isLoading
            binding.btnPriestRegister.isEnabled = !isLoading
        }
    }

    private fun setupClickListeners() {
        binding.btnConfessAnonymously.setOnClickListener {
            loginViewModel.signInAnonymously()
        }

        binding.btnPriestLogin.setOnClickListener {
            val intent = Intent(this, PriestLoginActivity::class.java)
            startActivity(intent)
        }

        binding.btnPriestRegister.setOnClickListener {
            val intent = Intent(this, PriestRegistrationActivity::class.java)
            startActivity(intent)
        }
    }
}
