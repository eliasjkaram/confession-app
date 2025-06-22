package com.example.confessionapp.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PriestUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "", // Assuming email might be useful, can be removed if not
    val photoUrl: String? = null,
    val languages: List<String> = emptyList(),
    val isPriestVerified: Boolean = false
) : Parcelable
