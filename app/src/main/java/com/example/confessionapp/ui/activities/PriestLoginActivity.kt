package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityPriestLoginBinding
import com.example.confessionapp.ui.viewmodels.PriestAuthViewModel
// Import R class
import com.example.confessionapp.R

class PriestLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriestLoginBinding
    private val priestAuthViewModel: PriestAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriestLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        priestAuthViewModel.priestLoginResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Priest login successful", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, PriestDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        priestAuthViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarPriestLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLoginPriest.isEnabled = !isLoading
            binding.etEmailPriestLogin.isEnabled = !isLoading
            binding.etPasswordPriestLogin.isEnabled = !isLoading
        }

        priestAuthViewModel.errorEvent.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                priestAuthViewModel.doneConsumingError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLoginPriest.setOnClickListener {
            val email = binding.etEmailPriestLogin.text.toString().trim()
            val password = binding.etPasswordPriestLogin.text.toString()
            priestAuthViewModel.loginPriest(email, password)
        }
    }
}
