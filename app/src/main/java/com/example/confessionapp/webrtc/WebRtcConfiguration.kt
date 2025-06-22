package com.example.confessionapp.webrtc

import org.webrtc.PeerConnection

object WebRtcConfiguration {

    // These should be fetched from a secure backend or BuildConfig in a real app
    // Placeholders based on Setup.md
    private const val TURN_SERVER_URL_UDP = "turn:your.turn.server.com:3478?transport=udp"
    private const val TURN_SERVER_URL_TCP = "turn:your.turn.server.com:3478?transport=tcp"
    private const val TURNS_SERVER_URL_TLS = "turns:your.turn.server.com:5349"
    private const val TURN_USERNAME = "your_turn_username"
    private const val TURN_PASSWORD = "your_turn_password"

    // Google STUN servers (free and public)
    private const val STUN_SERVER_1 = "stun:stun.l.google.com:19302"
    private const val STUN_SERVER_2 = "stun:stun1.l.google.com:19302"


    fun getIceServers(): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // STUN servers
        iceServers.add(PeerConnection.IceServer.builder(STUN_SERVER_1).createIceServer())
        iceServers.add(PeerConnection.IceServer.builder(STUN_SERVER_2).createIceServer())

        // TURN servers (as per Setup.md, these should be prioritized if NAT traversal fails with STUN)
        // In a real app, fetch TURN_USERNAME and TURN_PASSWORD securely
        if (TURN_USERNAME.isNotBlank() && TURN_PASSWORD.isNotBlank()) {
            iceServers.add(
                PeerConnection.IceServer.builder(TURN_SERVER_URL_UDP)
                    .setUsername(TURN_USERNAME)
                    .setPassword(TURN_PASSWORD)
                    .createIceServer()
            )
            iceServers.add(
                PeerConnection.IceServer.builder(TURN_SERVER_URL_TCP)
                    .setUsername(TURN_USERNAME)
                    .setPassword(TURN_PASSWORD)
                    .createIceServer()
            )
            iceServers.add(
                PeerConnection.IceServer.builder(TURNS_SERVER_URL_TLS)
                    .setUsername(TURN_USERNAME)
                    .setPassword(TURN_PASSWORD)
                    .createIceServer()
            )
        }
        return iceServers
    }

    // EglBase is needed for rendering video, but since we are audio-only,
    // a shared software-based EglBase context might still be needed by some internal WebRTC components.
    // Or it might not be strictly necessary if no video sink/source is attached.
    // For now, let's assume it might be needed and provide a way to get it.
    // val rootEglBase: EglBase by lazy { EglBase.create() }
}
