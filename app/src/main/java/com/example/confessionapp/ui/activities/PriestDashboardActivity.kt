package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityPriestDashboardBinding
import com.example.confessionapp.ui.viewmodels.PriestVerificationViewModel
import com.google.firebase.auth.FirebaseAuth

class PriestDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriestDashboardBinding
    private val viewModel: PriestVerificationViewModel by viewModels() // Using the same VM for status check

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriestDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()

        // Check current user
        if (FirebaseAuth.getInstance().currentUser == null) {
            // Should not happen if navigation is correct, but as a safeguard
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh status when activity is resumed, e.g., after returning from verification
        viewModel.checkPriestVerificationStatus()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarPriestDashboard.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.verificationStatus.observe(this) { (isVerified, statusString) ->
            binding.tvVerificationStatus.text = "Verification Status: ${statusString ?: "Unknown"}"
            if (isVerified) {
                binding.btnVerifyAccount.visibility = View.GONE
                binding.tvPriestAvailabilityLabel.visibility = View.VISIBLE
                binding.switchPriestAvailability.visibility = View.VISIBLE
            } else {
                binding.btnVerifyAccount.visibility = View.VISIBLE
                binding.tvPriestAvailabilityLabel.visibility = View.GONE
                binding.switchPriestAvailability.visibility = View.GONE
                if (statusString == "pending") {
                    binding.btnVerifyAccount.text = "Verification Pending"
                    binding.btnVerifyAccount.isEnabled = false
                } else {
                    binding.btnVerifyAccount.text = "Verify Account"
                    binding.btnVerifyAccount.isEnabled = true
                }
            }
        }

        viewModel.isAvailableForConfession.observe(this) { isAvailable ->
            binding.switchPriestAvailability.isChecked = isAvailable
            // Prevent triggering listener during initial setup or programmatic changes
            // binding.switchPriestAvailability.setOnCheckedChangeListener(null)
            // binding.switchPriestAvailability.isChecked = isAvailable
            // setupSwitchListener() // Re-attach listener
        }

        viewModel.availabilityUpdateResult.observe(this) { success ->
            if (!success) {
                Toast.makeText(this, "Failed to update availability status.", Toast.LENGTH_SHORT).show()
                // Revert switch state if needed, though viewModel.checkPriestVerificationStatus() should refresh it
                viewModel.checkPriestVerificationStatus()
            } else {
                Toast.makeText(this, "Availability updated.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnVerifyAccount.setOnClickListener {
            if (binding.btnVerifyAccount.isEnabled) { // Only if not pending
                val intent = Intent(this, PriestVerificationActivity::class.java)
                startActivity(intent)
            }
        }
        setupSwitchListener()
    }

    private fun setupSwitchListener() {
        binding.switchPriestAvailability.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePriestAvailability(isChecked)
        }
    }
}
