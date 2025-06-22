package com.example.confessionapp.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityConfessorDashboardBinding
import com.example.confessionapp.ui.viewmodels.ConfessorDashboardViewModel
import com.example.confessionapp.ui.viewmodels.PriestFindingResult
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.confessionapp.ui.adapters.PriestListAdapter

class ConfessorDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfessorDashboardBinding
    private val viewModel: ConfessorDashboardViewModel by viewModels()
    private lateinit var priestListAdapter: PriestListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfessorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPriestListRecyclerView()
        setupLanguageSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupPriestListRecyclerView() {
        priestListAdapter = PriestListAdapter { selectedPriest ->
            // When a priest is clicked in the list, send an invitation to them.
            binding.rvAvailablePriests.visibility = View.GONE // Hide list after selection
            binding.tvPriestSearchStatus.visibility = View.VISIBLE // Show status text
            viewModel.sendInvitationToSelectedPriest(selectedPriest)
        }
        binding.rvAvailablePriests.apply {
            layoutManager = LinearLayoutManager(this@ConfessorDashboardActivity)
            adapter = priestListAdapter
        }
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, viewModel.supportedLanguages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguagePreference.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.priestFindingState.observe(this) { result ->
            // Default UI state adjustments for each observation
            binding.progressBarConfessorDashboard.visibility = View.GONE
            binding.rvAvailablePriests.visibility = View.GONE // Hide list by default, show only if PriestsFoundList
            binding.tvPriestSearchStatus.visibility = View.VISIBLE // Show status text by default
            binding.btnFindPriest.isEnabled = true
            binding.btnFindPriest.text = "Find a Priest" // Default button text


            when (result) {
                is PriestFindingResult.Searching -> {
                    binding.tvPriestSearchStatus.text = "Searching for available priests..."
                    binding.btnFindPriest.text = "Searching..."
                    binding.btnFindPriest.isEnabled = false
                    binding.progressBarConfessorDashboard.visibility = View.VISIBLE
                }
                is PriestFindingResult.PriestsFoundList -> {
                    if (result.priests.isEmpty()){
                        binding.tvPriestSearchStatus.text = "No priests found for the selected criteria. Try 'Any' language."
                        priestListAdapter.submitList(emptyList())
                    } else {
                        binding.tvPriestSearchStatus.text = "Select a priest to start confession:"
                        priestListAdapter.submitList(result.priests)
                        binding.rvAvailablePriests.visibility = View.VISIBLE
                    }
                    binding.btnFindPriest.text = "Refresh List"
                }
                is PriestFindingResult.SendingInvitation -> {
                    binding.tvPriestSearchStatus.text = "Sending invitation..."
                    binding.btnFindPriest.isEnabled = false
                    binding.progressBarConfessorDashboard.visibility = View.VISIBLE
                }
                is PriestFindingResult.InvitationSent -> {
                    binding.tvPriestSearchStatus.text = "Waiting for priest to accept... (Room: ${result.invitation.roomId})"
                    binding.btnFindPriest.text = "Cancel Invitation"
                }
                is PriestFindingResult.InvitationAccepted -> {
                    binding.tvPriestSearchStatus.text = "Priest accepted! Joining room: ${result.invitation.roomId}"

                    val intent = Intent(this, ConfessionActivity::class.java)
                    intent.putExtra(ConfessionActivity.EXTRA_PRIEST_ID, result.invitation.priestId)
                    intent.putExtra(ConfessionActivity.EXTRA_ROOM_ID, result.invitation.roomId)
                    intent.putExtra(ConfessionActivity.EXTRA_IS_CALLER, true)
                    startActivity(intent)
                    viewModel.cancelCurrentInvitationSearch()
                }
                is PriestFindingResult.InvitationRejected -> {
                    binding.tvPriestSearchStatus.text = "Priest rejected the invitation. (${result.invitation.roomId})"
                }
                is PriestFindingResult.InvitationTimeout -> {
                    binding.tvPriestSearchStatus.text = "Priest did not respond in time (Invitation: ${result.invitation.invitationId}). Please try again."
                    binding.btnFindPriest.text = "Find a Priest" // Reset button
                    binding.btnFindPriest.isEnabled = true
                }
                is PriestFindingResult.NoPriestsAvailable -> {
                    binding.tvPriestSearchStatus.text = "No priests are currently available for the selected language. Please try again later or select 'Any'."
                    priestListAdapter.submitList(emptyList())
                }
                is PriestFindingResult.Error, is PriestFindingResult.InvitationError -> {
                    val errorMessage = when (result) {
                        is PriestFindingResult.Error -> result.message
                        is PriestFindingResult.InvitationError -> result.message
                        else -> "An unknown error occurred."
                    }
                    binding.tvPriestSearchStatus.text = "Error: $errorMessage"
                }
                 is PriestFindingResult.Idle -> {
                    binding.tvPriestSearchStatus.visibility = View.GONE
                    priestListAdapter.submitList(emptyList())
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnFindPriest.setOnClickListener {
            val currentState = viewModel.priestFindingState.value
            if (currentState is PriestFindingResult.InvitationSent) {
                viewModel.cancelCurrentInvitationSearch()
            } else {
                // When "Find a Priest" or "Refresh List" is clicked
                val selectedLanguage = binding.spinnerLanguagePreference.selectedItem.toString()
                viewModel.searchForAvailablePriests(selectedLanguage)
            }
        }

        binding.btnMakeDonation.setOnClickListener {
            val intent = Intent(this, GooglePayDonationActivity::class.java)
            startActivity(intent)
        }
    }
}
