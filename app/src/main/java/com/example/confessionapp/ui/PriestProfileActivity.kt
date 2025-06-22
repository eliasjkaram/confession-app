package com.example.confessionapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.confessionapp.ConfessionApplication
import com.example.confessionapp.R
import com.example.confessionapp.repository.UserRepository
import com.example.confessionapp.viewmodel.AuthViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestoreException

class PriestProfileActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "PriestProfileActivity"
    }

    private lateinit var languageEditText: EditText
    private lateinit var addLanguageButton: Button
    private lateinit var languagesChipGroup: ChipGroup
    private lateinit var saveProfileButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var userRepository: UserRepository
    private var currentUserUid: String? = null // This will be set by observing AuthViewModel
    private val authViewModel: AuthViewModel by viewModels()

    private val currentLanguages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_priest_profile)

        languageEditText = findViewById(R.id.language_edit_text)
        addLanguageButton = findViewById(R.id.add_language_button)
        languagesChipGroup = findViewById(R.id.languages_chip_group)
        saveProfileButton = findViewById(R.id.save_profile_button)
        progressBar = findViewById(R.id.profile_progress_bar)

        // Initialize UserRepository from Application class
        userRepository = (application as ConfessionApplication).userRepository

        observeAuthViewModel()

        addLanguageButton.setOnClickListener {
            val language = languageEditText.text.toString().trim()
            if (language.isNotEmpty()) {
                addLanguageToChipGroup(language)
                languageEditText.text.clear()
            } else {
                Toast.makeText(this, "Language cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        saveProfileButton.setOnClickListener {
            savePriestProfile()
        }
    }

    private fun observeAuthViewModel() {
        authViewModel.currentUserId.observe(this, Observer { uid ->
            this.currentUserUid = uid
            if (uid != null) {
                Log.i(TAG, "User logged in with UID: $uid. Loading profile.")
                // Enable UI elements that depend on logged-in state if they were disabled
                languageEditText.isEnabled = true // Ensure it's enabled if it was disabled
                // addLanguageButton and saveProfileButton are handled by setLoadingState
                loadPriestProfile()
            } else {
                Log.w(TAG, "User logged out or not authenticated. Disabling profile features.")
                Toast.makeText(this, "User not logged in. Profile features disabled.", Toast.LENGTH_LONG).show()
                setLoadingState(false) // Ensure loading is off
                saveProfileButton.isEnabled = false
                addLanguageButton.isEnabled = false
                languageEditText.isEnabled = false
                currentLanguages.clear()
                updateLanguagesChipGroup() // Clear displayed chips
            }
        })
    }

    private fun loadPriestProfile() {
        if (currentUserUid == null) {
            Log.w(TAG, "loadPriestProfile called but currentUserUid is null. Aborting.")
            setLoadingState(false) // Ensure loading is off
            // UI should already be disabled by observer if UID is null
            return
        }
        setLoadingState(true)
        currentUserUid?.let { uid -> // Re-check for safety, though observer should handle
            userRepository.getUserProfile(uid) { profileMap ->
                setLoadingState(false)
                if (profileMap != null) {
                    val languages = profileMap["languages"] as? List<String> ?: emptyList()
                    currentLanguages.clear()
                    currentLanguages.addAll(languages)
                    updateLanguagesChipGroup()
                } else {
                    Log.w(TAG, "Failed to load profile for UID: $uid. Profile map was null.")
                    Toast.makeText(this, "Failed to load profile. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addLanguageToChipGroup(language: String, fromLoad: Boolean = false) {
        val capitalizedLanguage = language.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (currentLanguages.add(capitalizedLanguage)) {
            if (!fromLoad) {
                updateLanguagesChipGroup()
            }
        } else if (!fromLoad) {
            Toast.makeText(this, "'$capitalizedLanguage' already added.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLanguagesChipGroup() {
        languagesChipGroup.removeAllViews()
        currentLanguages.sorted().forEach { language -> // Display sorted
            val chip = LayoutInflater.from(this).inflate(R.layout.chip_language, languagesChipGroup, false) as Chip
            // Assuming chip_language.xml exists and its root is a Chip
            // If R.layout.chip_language is just a Chip directly in XML:
            // val chip = Chip(this)
            chip.text = language
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                currentLanguages.remove(language)
                updateLanguagesChipGroup()
            }
            languagesChipGroup.addView(chip)
        }
    }

    private fun savePriestProfile() {
        currentUserUid?.let { uid ->
            // In a real app, you'd fetch the existing profile first,
            // then update only the languages field, or merge.
            // For simplicity here, we create a new map with just languages,
            // assuming other profile fields are managed elsewhere or this is a specific language settings page.
            // A more robust approach would be to fetch the full profile map first,
            // but for saving languages specifically, this might be okay if it's the only thing this screen manages.
            // If we fetch full profile first, ensure loading state covers that too.
            setLoadingState(true)
            userRepository.getUserProfile(uid) { existingProfile -> // Fetch existing to merge, or just save new map
                val profileUpdate: MutableMap<String, Any> = existingProfile?.toMutableMap() ?: mutableMapOf()
                profileUpdate["languages"] = currentLanguages.toList()

                userRepository.setUserProfile(uid, profileUpdate) { success, exception ->
                    setLoadingState(false)
                    if (success) {
                        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "Failed to update profile for UID: $uid", exception)
                        val errorMsg = when (exception?.code) {
                            FirebaseFirestoreException.Code.UNAVAILABLE -> "Network error. Please check connection."
                            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied."
                            else -> "Failed to update profile. Please try again."
                        }
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } ?: run {
            setLoadingState(false) // Ensure loading is stopped
            Log.w(TAG, "Save attempt failed: User not logged in.")
            Toast.makeText(this, "User not logged in. Cannot save profile.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        languageEditText.isEnabled = !isLoading
        addLanguageButton.isEnabled = !isLoading
        saveProfileButton.isEnabled = !isLoading
        // Consider disabling chip group interaction too, or cover with overlay
        // Only enable add/save if user is logged in (handled by currentUserUid check mostly)
        val isLoggedIn = currentUserUid != null
        languageEditText.isEnabled = !isLoading && isLoggedIn
        addLanguageButton.isEnabled = !isLoading && isLoggedIn
        saveProfileButton.isEnabled = !isLoading && isLoggedIn
        languagesChipGroup.isEnabled = !isLoading && isLoggedIn // May not prevent clicks on individual chips' close icons
    }
}
