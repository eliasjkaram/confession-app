package com.example.confessionapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.R
import com.example.confessionapp.repository.UserRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PriestProfileActivity : AppCompatActivity() {

    private lateinit var languageEditText: EditText
    private lateinit var addLanguageButton: Button
    private lateinit var languagesChipGroup: ChipGroup
    private lateinit var saveProfileButton: Button

    private lateinit var userRepository: UserRepository
    private var currentUserUid: String? = null

    private val currentLanguages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_priest_profile)

        languageEditText = findViewById(R.id.language_edit_text)
        addLanguageButton = findViewById(R.id.add_language_button)
        languagesChipGroup = findViewById(R.id.languages_chip_group)
        saveProfileButton = findViewById(R.id.save_profile_button)

        // Initialize UserRepository
        // In a real app, you might use dependency injection (e.g., Hilt)
        userRepository = UserRepository(FirebaseFirestore.getInstance())
        currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid == null) {
            Toast.makeText(this, "User not logged in. Cannot load profile.", Toast.LENGTH_LONG).show()
            // Potentially finish activity or redirect to login
            // For now, disable save button if no user
            saveProfileButton.isEnabled = false
            addLanguageButton.isEnabled = false
        } else {
            loadPriestProfile()
        }

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

    private fun loadPriestProfile() {
        currentUserUid?.let { uid ->
            userRepository.getUserProfile(uid) { profileMap ->
                if (profileMap != null) {
                    val languages = profileMap["languages"] as? List<String> ?: emptyList()
                    currentLanguages.clear()
                    currentLanguages.addAll(languages)
                    updateLanguagesChipGroup()
                } else {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addLanguageToChipGroup(language: String, fromLoad: Boolean = false) {
        if (currentLanguages.add(language.capitalize())) { // Add to set, ensure uniqueness and capitalize
            if (!fromLoad) { // Only update chip group if not called during initial load
                 updateLanguagesChipGroup()
            }
        } else if (!fromLoad) {
             Toast.makeText(this, "$language already added", Toast.LENGTH_SHORT).show()
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
            // A more robust approach would be to fetch the full profile map first.

            userRepository.getUserProfile(uid) { existingProfile ->
                val profileUpdate: MutableMap<String, Any> = existingProfile?.toMutableMap() ?: mutableMapOf()
                profileUpdate["languages"] = currentLanguages.toList()

                // Add other fields if this activity is responsible for them
                // For example, if name can be edited here:
                // profileUpdate["name"] = nameEditText.text.toString()
                // Ensure these fields exist in your Firestore structure and PriestUser model

                userRepository.setUserProfile(uid, profileUpdate) { success ->
                    if (success) {
                        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "User not logged in. Cannot save profile.", Toast.LENGTH_LONG).show()
        }
    }
}
