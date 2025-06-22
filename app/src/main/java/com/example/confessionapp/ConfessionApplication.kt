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
        // You can enable persistence here if needed:
        // Firebase.firestore.apply {
        //    firestoreSettings = FirebaseFirestoreSettings.Builder()
        //        .setPersistenceEnabled(true)
        //        .build()
        // }
    }

    val userRepository: UserRepository by lazy {
        UserRepository(firestoreInstance)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize FirebaseApp here if not done automatically,
        // though usually it's automatic via content providers.
        // FirebaseApp.initializeApp(this)

        // You could initialize other app-wide services here
        // For example, if using Timber for logging:
        // if (BuildConfig.DEBUG) {
        //     Timber.plant(Timber.DebugTree())
        // }
    }
}
