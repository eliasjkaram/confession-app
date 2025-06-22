package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.data.CallInvitation
import com.example.confessionapp.databinding.ActivityPriestDashboardBinding
import com.example.confessionapp.ui.viewmodels.PriestDashboardViewModel // New
import com.example.confessionapp.ui.viewmodels.PriestProfileViewModel  // New
import com.google.firebase.auth.FirebaseAuth

class PriestDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriestDashboardBinding
    private val dashboardViewModel: PriestDashboardViewModel by viewModels()
    private val profileViewModel: PriestProfileViewModel by viewModels()
    private var currentInvitationDialog: AlertDialog? = null

    private val availableLanguages = arrayOf("English", "Spanish", "French", "Latin", "German", "Italian")
    private var selectedLanguagesBooleanArray = BooleanArray(availableLanguages.size)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriestDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners() // Call before observers that might rely on initial state from listeners
        setupObservers()

        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        profileViewModel.fetchPriestProfileAndStatus()
        dashboardViewModel.listenForIncomingInvitations()
    }

    override fun onPause() {
        super.onPause()
        // Consider stopping listeners if activity is not visible for long
        // viewModel.stopListeningForIncomingInvitations() // Example
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInvitationDialog?.dismiss()
        // ViewModel's onCleared will handle its listeners
    }

    private fun setupObservers() {
        // Observers for PriestProfileViewModel
        profileViewModel.isLoadingProfile.observe(this) { isLoading ->
            binding.progressBarPriestDashboard.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnEditLanguages.isEnabled = !isLoading
        }

        profileViewModel.priestProfileStatus.observe(this) { profileStatus ->
            binding.tvVerificationStatus.text = "Verification Status: ${profileStatus.verificationStatusString ?: "Unknown"}"
            if (profileStatus.isVerified) {
                binding.btnVerifyAccount.visibility = View.GONE
                binding.tvPriestAvailabilityLabel.visibility = View.VISIBLE
                binding.switchPriestAvailability.visibility = View.VISIBLE
                binding.switchPriestAvailability.isEnabled = true
                binding.btnEditLanguages.visibility = View.VISIBLE
                binding.tvPriestLanguages.visibility = View.VISIBLE
            } else {
                binding.btnVerifyAccount.visibility = View.VISIBLE
                binding.tvPriestAvailabilityLabel.visibility = View.GONE
                binding.switchPriestAvailability.visibility = View.GONE
                binding.btnEditLanguages.visibility = View.GONE
                binding.tvPriestLanguages.visibility = View.GONE
                if (profileStatus.verificationStatusString == "pending") {
                    binding.btnVerifyAccount.text = "Verification Pending"
                    binding.btnVerifyAccount.isEnabled = false
                } else {
                    binding.btnVerifyAccount.text = "Verify Account"
                    binding.btnVerifyAccount.isEnabled = true
                }
            }

            if (profileStatus.languages.isNotEmpty()) {
                binding.tvPriestLanguages.text = "Languages: ${profileStatus.languages.joinToString(", ")}"
            } else {
                binding.tvPriestLanguages.text = "Languages: Not set"
            }
            // Ensure availability switch is enabled/disabled based on verification too
            binding.switchPriestAvailability.isEnabled = profileStatus.isVerified
        }

        profileViewModel.languageUpdateResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Languages updated successfully.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to update languages.", Toast.LENGTH_SHORT).show()
            }
        }

        // Observers for PriestDashboardViewModel
        dashboardViewModel.isLoadingAvailability.observe(this) { isLoading ->
            // Could use a separate ProgressBar for availability switch, or disable switch
            binding.switchPriestAvailability.isEnabled = !isLoading && (profileViewModel.priestProfileStatus.value?.isVerified == true)
            if (isLoading) { // If general progress bar is used, show it
                 binding.progressBarPriestDashboard.visibility = View.VISIBLE
            } else {
                // Hide only if profile is also not loading
                if (profileViewModel.isLoadingProfile.value == false) {
                    binding.progressBarPriestDashboard.visibility = View.GONE
                }
            }
        }

        dashboardViewModel.isAvailableForConfession.observe(this) { isAvailable ->
            if (binding.switchPriestAvailability.isChecked != isAvailable) {
                binding.switchPriestAvailability.isChecked = isAvailable
            }
        }

        dashboardViewModel.availabilityUpdateResult.observe(this) { success ->
            if (!success) {
                Toast.makeText(this, "Failed to update availability status.", Toast.LENGTH_SHORT).show()
                dashboardViewModel.fetchPriestAvailability() // Re-fetch to revert UI
            } else {
                Toast.makeText(this, "Availability updated.", Toast.LENGTH_SHORT).show()
            }
        }

        dashboardViewModel.incomingInvitation.observe(this) { invitation ->
            currentInvitationDialog?.dismiss()
            if (invitation != null && invitation.status == CallInvitation.PENDING) {
                showInvitationDialog(invitation)
            }
        }

        dashboardViewModel.invitationAcceptedEvent.observe(this) { acceptedInvitation ->
            acceptedInvitation?.let {
                val intent = Intent(this, ConfessionActivity::class.java)
                intent.putExtra(ConfessionActivity.EXTRA_ROOM_ID, it.roomId)
                intent.putExtra(ConfessionActivity.EXTRA_IS_CALLER, false)
                startActivity(intent)
                dashboardViewModel.onInvitationAcceptedEventConsumed()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnVerifyAccount.setOnClickListener {
            if (binding.btnVerifyAccount.isEnabled) {
                startActivity(Intent(this, PriestVerificationActivity::class.java))
            }
        }

        binding.switchPriestAvailability.setOnCheckedChangeListener { _, isChecked ->
             if (profileViewModel.priestProfileStatus.value?.isVerified == true) {
                dashboardViewModel.updatePriestAvailability(isChecked)
            }
        }

        binding.btnEditLanguages.setOnClickListener {
            showEditLanguagesDialog()
        }
    }

    private fun showInvitationDialog(invitation: CallInvitation) {
        currentInvitationDialog = AlertDialog.Builder(this)
            .setTitle("Incoming Confession Request")
            .setMessage("${invitation.confessorDisplayName} would like to start a confession.")
            .setPositiveButton("Accept") { dialog, _ ->
                dashboardViewModel.acceptInvitation(invitation)
                dialog.dismiss()
            }
            .setNegativeButton("Reject") { dialog, _ ->
                dashboardViewModel.rejectInvitation(invitation)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showEditLanguagesDialog() {
        val currentPriestLanguages = profileViewModel.priestProfileStatus.value?.languages ?: emptyList()
        availableLanguages.forEachIndexed { index, lang ->
            selectedLanguagesBooleanArray[index] = currentPriestLanguages.contains(lang)
        }

        AlertDialog.Builder(this)
            .setTitle("Select Languages")
            .setMultiChoiceItems(availableLanguages, selectedLanguagesBooleanArray) { _, which, isChecked ->
                selectedLanguagesBooleanArray[which] = isChecked
            }
            .setPositiveButton("Save") { dialog, _ ->
                val newSelectedLanguages = mutableListOf<String>()
                selectedLanguagesBooleanArray.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        newSelectedLanguages.add(availableLanguages[index])
                    }
                }
                profileViewModel.updatePriestLanguages(newSelectedLanguages)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
