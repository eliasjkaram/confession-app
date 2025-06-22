package com.example.confessionapp.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.R
import com.example.confessionapp.data.models.PriestUser
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore

class ConfessorDashboardActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var priestsRecyclerView: RecyclerView
    private lateinit var priestAdapter: PriestAdapter
    private lateinit var userRepository: UserRepository

    // Define a list of languages for the spinner
    // In a real app, this might come from a remote config, constants file, or be dynamically generated
    private val availableLanguages = listOf("All Languages", "English", "Spanish", "French", "Latin", "German")
    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confessor_dashboard)

        languageSpinner = findViewById(R.id.language_spinner)
        priestsRecyclerView = findViewById(R.id.priests_recycler_view)

        // Initialize UserRepository
        userRepository = UserRepository(FirebaseFirestore.getInstance())

        setupLanguageSpinner()
        setupRecyclerView()

        // Load initial list of priests (all languages)
        fetchPriests(null)
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableLanguages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val language = availableLanguages[position]
                selectedLanguage = if (language == "All Languages") null else language
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
        // Show a loading indicator if you have one
        userRepository.getVerifiedPriests(language) { result ->
            result.onSuccess { priests ->
                if (priests.isEmpty()) {
                    Toast.makeText(this, "No priests found for the selected language.", Toast.LENGTH_SHORT).show()
                }
                priestAdapter.updateData(priests)
            }.onFailure { exception ->
                Toast.makeText(this, "Error fetching priests: ${exception.message}", Toast.LENGTH_LONG).show()
                priestAdapter.updateData(emptyList()) // Clear list on error
            }
            // Hide loading indicator
        }
    }
}
