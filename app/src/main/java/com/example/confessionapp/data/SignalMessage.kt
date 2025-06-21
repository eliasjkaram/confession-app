package com.example.confessionapp.data

// Based on common WebRTC signaling needs
// Using Any for sdp/candidate for flexibility, but could be more specific
data class SignalMessage(
    val type: String, // "OFFER", "ANSWER", "ICE_CANDIDATE"
    val sdp: String? = null, // For OFFER/ANSWER
    val iceCandidateSdp: String? = null, // For ICE_CANDIDATE (sdp)
    val iceCandidateSdpMid: String? = null, // For ICE_CANDIDATE (sdpMid)
    val iceCandidateSdpMLineIndex: Int? = null, // For ICE_CANDIDATE (sdpMLineIndex)
    val senderId: String? = null // Optional: to identify sender if needed
)

// Simple ChatMessage data class
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "", // UID of the sender
    val senderDisplayName: String = "User", // "You" or "Priest"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
