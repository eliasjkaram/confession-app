package com.example.confessionapp.data

import com.google.firebase.database.ServerValue // For timestamp

data class CallInvitation(
    val invitationId: String = "", // Unique ID for this invitation
    val roomId: String = "", // WebRTC room ID
    val confessorId: String = "", // UID of the confessor (initiator)
    val confessorDisplayName: String = "Anonymous Confessor", // Display name for the priest
    val priestId: String = "", // UID of the target priest
    var status: String = PENDING, // "PENDING", "ACCEPTED", "REJECTED", "MISSED", "EXPIRED"
    val timestamp: Any = ServerValue.TIMESTAMP, // Firebase server timestamp for creation
    var priestRespondedTimestamp: Long? = null // Timestamp when priest responded
) {
    companion object {
        const val PENDING = "PENDING"
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
        const val MISSED = "MISSED" // If priest doesn't respond in time
        const val EXPIRED = "EXPIRED" // If confessor cancels or times out waiting
    }
    // Default constructor for Firebase
    constructor() : this("", "", "", "Anonymous Confessor", "", PENDING, ServerValue.TIMESTAMP, null)
}
