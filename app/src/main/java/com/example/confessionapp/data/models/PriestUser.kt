package com.example.confessionapp.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PriestUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "", // Assuming email is part of the user data
    val photoUrl: String? = null,
    val languages: List<String> = emptyList(),
    val isPriestVerified: Boolean = false // Keep this to ensure we're dealing with verified priests
) : Parcelable
