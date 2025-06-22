package com.example.confessionapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.ConfessionApplication
import com.example.confessionapp.R
import com.example.confessionapp.data.models.PriestUser
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException

class ConfessorDashboardActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ConfessorDashboard"
    }

    private lateinit var languageSpinner: Spinner
    private lateinit var priestsRecyclerView: RecyclerView
    private lateinit var priestAdapter: PriestAdapter
    private lateinit var userRepository: UserRepository
    private lateinit var progressBar: ProgressBar

    private var availableLanguages = mutableListOf("All Languages") // Default, will be updated
    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confessor_dashboard)

        languageSpinner = findViewById(R.id.language_spinner)
        priestsRecyclerView = findViewById(R.id.priests_recycler_view)
        progressBar = findViewById(R.id.dashboard_progress_bar)

        // Initialize UserRepository from Application class
        userRepository = (application as ConfessionApplication).userRepository

        setupRecyclerView() // Setup RV first
        loadSupportedLanguages() // Then load languages for spinner

        // Initial fetch of priests will be triggered by language spinner setup or default selection
    }

    private fun loadSupportedLanguages() {
        setLoadingState(true) // Show loading while fetching languages
        userRepository.getSupportedLanguages { result ->
            setLoadingState(false) // Hide loading after fetching languages
            result.onSuccess { fetchedLanguages ->
                Log.d(TAG, "Fetched languages: $fetchedLanguages")
                availableLanguages.clear()
                availableLanguages.add("All Languages") // Ensure "All Languages" is first
                availableLanguages.addAll(fetchedLanguages)
                setupLanguageSpinner() // Setup spinner with new languages
                // Trigger initial priest fetch if not already done by spinner's onItemSelected
                if (languageSpinner.selectedItemPosition == 0) { // If "All Languages" is selected
                    fetchPriests(null)
                } else {
                    fetchPriests(languageSpinner.selectedItem.toString())
                }
            }.onFailure { exception ->
                Log.e(TAG, "Failed to fetch supported languages", exception)
                Toast.makeText(this, "Could not load language filter. Using defaults.", Toast.LENGTH_LONG).show()
                // Keep default availableLanguages or add a few common ones as fallback
                if (availableLanguages.size <= 1) { // Only "All languages" is present
                    availableLanguages.addAll(listOf("English", "Spanish")) // Fallback defaults
                }
                setupLanguageSpinner() // Setup spinner with default/fallback languages
                fetchPriests(null) // Fetch all priests as a fallback
            }
        }
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableLanguages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val language = if (position == 0) null else availableLanguages[position] // "All Languages" maps to null
                selectedLanguage = language
                fetchPriests(selectedLanguage)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupRecyclerView() {
        priestAdapter = PriestAdapter(emptyList())
        priestsRecyclerView.layoutManager = LinearLayoutManager(this)
        priestsRecyclerView.adapter = priestAdapter
    }

    private fun fetchPriests(language: String?) {
        setLoadingState(true)
        userRepository.getVerifiedPriests(language) { result ->
            setLoadingState(false)
            result.onSuccess { priests ->
                priestsRecyclerView.visibility = View.VISIBLE // Ensure RV is visible
                if (priests.isEmpty() && language != null) { // Only show "no priests for language" if a filter is active
                    Toast.makeText(this, "No priests found for the selected language: $language.", Toast.LENGTH_SHORT).show()
                } else if (priests.isEmpty() && language == null) {
                    Toast.makeText(this, "No verified priests currently available.", Toast.LENGTH_SHORT).show()
                }
                priestAdapter.updateData(priests)
            }.onFailure { exception ->
                priestsRecyclerView.visibility = View.GONE // Hide RV on error to show empty state / allow progress bar to be more prominent if error is quick
                Log.e(TAG, "Error fetching priests for language: $language", exception)
                val errorMsg = when ((exception as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "Network error. Please check connection and try again."
                    else -> "Error fetching priests. Please try again."
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                priestAdapter.updateData(emptyList()) // Clear list on error
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        languageSpinner.isEnabled = !isLoading
        // Optionally hide RecyclerView during load, or let ProgressBar overlay
        // priestsRecyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
}
