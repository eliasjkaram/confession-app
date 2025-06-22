package com.example.confessionapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.confessionapp.R
import com.example.confessionapp.data.models.PriestUser
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ConfessorDashboardActivity : AppCompatActivity() {

    private lateinit var priestsRecyclerView: RecyclerView
    private lateinit var priestAdapter: PriestAdapter
    private lateinit var userRepository: UserRepository
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confessor_dashboard)

        // Initialize UserRepository from Application class
        userRepository = (application as ConfessionApplication).userRepository

        priestsRecyclerView = findViewById(R.id.priests_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        emptyStateTextView = findViewById(R.id.empty_state_text_view)

        setupRecyclerView()
        loadVerifiedPriests()
    }

    private fun setupRecyclerView() {
        priestAdapter = PriestAdapter { priest ->
            // Handle priest selection
            Log.d("ConfessorDashboard", "Selected priest: ${priest.name} (UID: ${priest.uid})")
            Toast.makeText(this, "Selected: ${priest.name}", Toast.LENGTH_SHORT).show()
            // TODO: Implement connection logic or navigation to chat/call screen
        }
        priestsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ConfessorDashboardActivity)
            adapter = priestAdapter
        }
    }

    private fun loadVerifiedPriests(language: String? = null) {
        showLoading(true)
        emptyStateTextView.visibility = View.GONE
        priestsRecyclerView.visibility = View.GONE

        userRepository.getVerifiedPriests(language) { result ->
            showLoading(false)
            result.onSuccess { priests ->
                if (priests.isNotEmpty()) {
                    priestAdapter.submitList(priests)
                    priestsRecyclerView.visibility = View.VISIBLE
                } else {
                    emptyStateTextView.text = if (language == null) "No priests available." else "No priests available for the selected language."
                    emptyStateTextView.visibility = View.VISIBLE
                }
            }.onFailure { exception ->
                Log.e("ConfessorDashboard", "Error loading priests", exception)
                Toast.makeText(this, "Failed to load priests: ${exception.message}", Toast.LENGTH_LONG).show()
                emptyStateTextView.text = "Error loading priests."
                emptyStateTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
