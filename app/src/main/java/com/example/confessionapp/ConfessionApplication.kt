package com.example.confessionapp

import android.app.Application
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ConfessionApplication : Application() {

    // Lazy initialization for services
    val firestoreInstance: FirebaseFirestore by lazy {
        Firebase.firestore
        // Optional: Configure Firestore settings (e.g., persistence)
        // val settings = FirebaseFirestoreSettings.Builder()
        //     .setPersistenceEnabled(true)
        //     .build()
        // Firebase.firestore.firestoreSettings = settings
        // Firebase.firestore
    }

    val userRepository: UserRepository by lazy {
        UserRepository(firestoreInstance)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize FirebaseApp here if not done automatically by FirebaseInitProvider.
        // FirebaseApp.initializeApp(this)

        // Other app-wide initializations can go here.
        // For example, Timber for logging:
        // if (BuildConfig.DEBUG) {
        //     Timber.plant(Timber.DebugTree())
        // }
        Log.d("ConfessionApplication", "Application Created and UserRepository initialized.")
    }
}

// Add a Log import for the onCreate message
import android.util.Log
