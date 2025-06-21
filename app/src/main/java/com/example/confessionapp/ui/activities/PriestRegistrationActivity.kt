package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityPriestRegistrationBinding
import com.example.confessionapp.ui.viewmodels.PriestAuthViewModel
// Import R class
import com.example.confessionapp.R

class PriestRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriestRegistrationBinding
    private val priestAuthViewModel: PriestAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriestRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        priestAuthViewModel.priestRegisterResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Priest registration successful. Please login.", Toast.LENGTH_LONG).show()
                // For now, navigate to login then dashboard.
                // Later, this could go directly to dashboard or an email verification step.
                val intent = Intent(this, PriestLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        priestAuthViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarPriestRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnRegisterPriest.isEnabled = !isLoading
            binding.etEmailPriestRegister.isEnabled = !isLoading
            binding.etPasswordPriestRegister.isEnabled = !isLoading
        }

        priestAuthViewModel.errorEvent.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                priestAuthViewModel.doneConsumingError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnRegisterPriest.setOnClickListener {
            val email = binding.etEmailPriestRegister.text.toString().trim()
            val password = binding.etPasswordPriestRegister.text.toString()
            priestAuthViewModel.registerPriest(email, password)
        }
    }
}
