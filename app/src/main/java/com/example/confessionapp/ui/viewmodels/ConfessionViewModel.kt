package com.example.confessionapp.ui.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.data.ChatMessage
import com.example.confessionapp.data.SignalMessage
import com.example.confessionapp.repository.SignalingRepository
import com.example.confessionapp.webrtc.WebRtcConfiguration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.PeerConnection.*

enum class CallStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

class ConfessionViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ConfessionViewModel"

    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"
    private var signalingRepository: SignalingRepository? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null // Not directly used for playback here, but good to track
    private var audioSource: AudioSource? = null

    // EglBase is typically needed for video, but some WebRTC init might still require it.
    // For pure audio, it might be less critical or handled internally by PeerConnectionFactory.
    private val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }


    private val _callStatus = MutableLiveData<CallStatus>(CallStatus.IDLE)
    val callStatus: LiveData<CallStatus> = _callStatus

    private val _chatMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages

    private val _isMuted = MutableLiveData<Boolean>(false)
    val isMuted: LiveData<Boolean> = _isMuted

    private var currentRoomId: String? = null
    private var isInitiator: Boolean = false


    fun initSession(roomId: String, isCaller: Boolean) {
        if (peerConnectionFactory != null) { // Already initialized
            Log.d(TAG, "Session already initialized or in progress.")
            return
        }
        currentRoomId = roomId
        isInitiator = isCaller
        _callStatus.postValue(CallStatus.CONNECTING)

        Log.d(TAG, "Initializing WebRTC session for room: $roomId, isCaller: $isCaller")

        // Initialize PeerConnectionFactory
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(getApplication())
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val factoryOptions = PeerConnectionFactory.Options()
        // May need to set audio device module here if default isn't working well
        // factoryOptions.setAudioDeviceModule(JavaAudioDeviceModule.builder(getApplication()).createAudioDeviceModule())

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            // .setEglContext(eglBaseContext) // May not be needed for audio-only
            .createPeerConnectionFactory()

        setupSignaling(roomId)
        createPeerConnection()

        if (isCaller) {
            createOffer()
        }
    }

    private fun setupSignaling(roomId: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("confession_rooms").child(roomId)
        signalingRepository = SignalingRepository(dbRef) // Assuming SignalingRepository uses this ref for signals sub-path

        signalingRepository?.listenForSignals(roomId) { signalMap -> // The roomId here might be redundant if SignalingRepo handles it
            signalMap?.let {
                val type = it["type"] as? String
                Log.d(TAG, "Received signal: $type")
                when (type) {
                    "OFFER" -> if (!isInitiator) handleOffer(it["sdp"] as? String)
                    "ANSWER" -> if (isInitiator) handleAnswer(it["sdp"] as? String)
                    "ICE_CANDIDATE" -> handleRemoteIceCandidate(it)
                }
            }
        }
        // TODO: Also set up chat message listener on a different path e.g., "confession_rooms/$roomId/chat"
    }

    private fun createPeerConnection() {
        val rtcConfig = RTCConfiguration(WebRtcConfiguration.getIceServers())
        // rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN // Default for modern WebRTC

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: SignalingState?) {
                Log.d(TAG, "SignalingState changed: $newState")
            }

            override fun onIceConnectionChange(newState: IceConnectionState?) {
                Log.d(TAG, "IceConnectionState changed: $newState")
                when (newState) {
                    IceConnectionState.CONNECTED -> _callStatus.postValue(CallStatus.CONNECTED)
                    IceConnectionState.DISCONNECTED -> _callStatus.postValue(CallStatus.DISCONNECTED)
                    IceConnectionState.FAILED -> _callStatus.postValue(CallStatus.ERROR)
                    IceConnectionState.CLOSED -> _callStatus.postValue(CallStatus.DISCONNECTED)
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "IceConnectionReceiving changed: $receiving")
            }

            override fun onIceGatheringChange(newState: IceGatheringState?) {
                Log.d(TAG, "IceGatheringState changed: $newState")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Generated ICE candidate: $it")
                    val signal = SignalMessage(
                        type = "ICE_CANDIDATE",
                        iceCandidateSdp = it.sdp,
                        iceCandidateSdpMid = it.sdpMid,
                        iceCandidateSdpMLineIndex = it.sdpMLineIndex,
                        senderId = currentUserId
                    )
                    sendSignal(signal)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "ICE candidates removed: $candidates")
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream added: ${stream?.id}")
                stream?.audioTracks?.firstOrNull()?.let {
                    remoteAudioTrack = it
                    // Remote audio should play automatically if audio device is configured
                    Log.d(TAG, "Remote audio track found and assigned.")
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream removed: ${stream?.id}")
                remoteAudioTrack = null
            }

            override fun onDataChannel(dataChannel: DataChannel?) {
                 Log.d(TAG, "Data channel received: ${dataChannel?.label()}")
                // Not using data channels for this app's chat (using Firebase directly)
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
                // Handle renegotiation if features like adding/removing tracks dynamically are implemented
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                 // This is part of Unified Plan, onAddStream is for Plan B.
                 // Handle added tracks, especially audio.
                receiver?.track()?.let { track ->
                    if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                        remoteAudioTrack = track as AudioTrack
                        Log.d(TAG, "Remote audio track added via onAddTrack.")
                    }
                }
            }
        })

        // Create and add local audio track
        createAndAddLocalAudioTrack()
    }

    private fun createAndAddLocalAudioTrack() {
        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true) // Ensure it's enabled
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS")) // "ARDAMS" is a common stream ID
        Log.d(TAG, "Local audio track created and added.")
    }


    private fun createOffer() {
        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "Offer created successfully: ${it.description.take(50)}")
                    peerConnection?.setLocalDescription(this, it) // `this` refers to an SdpObserver
                    val offerSignal = SignalMessage(type = "OFFER", sdp = it.description, senderId = currentUserId)
                    sendSignal(offerSignal)
                } ?: onCreateFailure("SDP was null")
            }

            override fun onSetSuccess() { Log.d(TAG, "LocalDescription (offer) set successfully") }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "Failed to create offer: $error") }
            override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set local description (offer): $error") }
        }, sdpConstraints)
    }

    private fun handleOffer(sdpString: String?) {
        Log.d(TAG, "Handling received offer.")
        sdpString?.let {
            val offerSdp = SessionDescription(SessionDescription.Type.OFFER, it)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {} // Not used when setting remote
                override fun onSetSuccess() {
                    Log.d(TAG, "RemoteDescription (offer) set successfully.")
                    createAnswer()
                }
                override fun onCreateFailure(p0: String?) {} // Not used
                override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set remote description (offer): $error") }
            }, offerSdp)
        } ?: Log.e(TAG, "Offer SDP string was null")
    }

    private fun createAnswer() {
        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "Answer created successfully: ${it.description.take(50)}")
                    peerConnection?.setLocalDescription(this, it) // `this` refers to an SdpObserver
                    val answerSignal = SignalMessage(type = "ANSWER", sdp = it.description, senderId = currentUserId)
                    sendSignal(answerSignal)
                } ?: onCreateFailure("SDP was null")
            }
            override fun onSetSuccess() { Log.d(TAG, "LocalDescription (answer) set successfully.") }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "Failed to create answer: $error") }
            override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set local description (answer): $error") }
        }, sdpConstraints)
    }

    private fun handleAnswer(sdpString: String?) {
        Log.d(TAG, "Handling received answer.")
        sdpString?.let {
            val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, it)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {} // Not used
                override fun onSetSuccess() { Log.d(TAG, "RemoteDescription (answer) set successfully.") }
                override fun onCreateFailure(p0: String?) {} // Not used
                override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set remote description (answer): $error") }
            }, answerSdp)
        } ?: Log.e(TAG, "Answer SDP string was null")
    }

    private fun handleRemoteIceCandidate(signalData: Map<String, Any>) {
        Log.d(TAG, "Handling remote ICE candidate.")
        try {
            val sdp = signalData["iceCandidateSdp"] as? String
            val sdpMid = signalData["iceCandidateSdpMid"] as? String
            val sdpMLineIndex = (signalData["iceCandidateSdpMLineIndex"] as? Long)?.toInt() // Firebase might return Long

            if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                val remoteIceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                peerConnection?.addIceCandidate(remoteIceCandidate)
                Log.d(TAG, "Remote ICE candidate added: $remoteIceCandidate")
            } else {
                Log.e(TAG, "Invalid ICE candidate data received: $signalData")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing remote ICE candidate: ${e.message}")
        }
    }

    private fun sendSignal(signalMessage: SignalMessage) {
        currentRoomId?.let {
            Log.d(TAG, "Sending signal: ${signalMessage.type} to room $it")
            // Convert SignalMessage to Map<String, Any> for Firebase
            val signalMap = mutableMapOf<String, Any?>()
            signalMap["type"] = signalMessage.type
            signalMessage.sdp?.let { sdp -> signalMap["sdp"] = sdp }
            signalMessage.iceCandidateSdp?.let { iceSdp -> signalMap["iceCandidateSdp"] = iceSdp }
            signalMessage.iceCandidateSdpMid?.let { mid -> signalMap["iceCandidateSdpMid"] = mid }
            signalMessage.iceCandidateSdpMLineIndex?.let { mLineIndex -> signalMap["iceCandidateSdpMLineIndex"] = mLineIndex }
            signalMessage.senderId?.let { sender -> signalMap["senderId"] = sender }

            signalingRepository?.sendSignal(it, signalMap.filterValues { v -> v != null } as Map<String, Any>)
        }
    }

    fun toggleMute() {
        localAudioTrack?.let {
            it.setEnabled(!it.enabled())
            _isMuted.postValue(!it.enabled())
            Log.d(TAG, "Local audio track muted: ${!it.enabled()}")
        }
    }

    fun endCall() {
        Log.d(TAG, "Ending call for room: $currentRoomId")
        _callStatus.postValue(CallStatus.DISCONNECTED)
        cleanUp()
    }

    private fun cleanUp() {
        Log.d(TAG, "Cleaning up WebRTC resources.")
        localAudioTrack?.dispose()
        audioSource?.dispose()
        remoteAudioTrack?.dispose() // Remote tracks are also disposable
        peerConnection?.close()
        peerConnection?.dispose()
        // Do not dispose PeerConnectionFactory here if app can make multiple calls,
        // unless it's truly the end of all WebRTC activity.
        // For simplicity in a single call context, it can be disposed.
        // peerConnectionFactory?.dispose()
        // eglBaseContext?.release() // If eglBase was used

        localAudioTrack = null
        audioSource = null
        remoteAudioTrack = null
        peerConnection = null
        // peerConnectionFactory = null // Keep factory for potential new calls unless app is closing

        // TODO: Remove signaling listeners from SignalingRepository
        // signalingRepository?.removeListeners()
        currentRoomId = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cleaning up.")
        cleanUp()
        // Dispose factory if it's not null and this is the final cleanup
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        // EglBase.terminate() // If PeerConnectionFactory.initialize was called with a specific EglBase
    }

    // --- Chat Functionality ---
    private var chatDbRef: com.google.firebase.database.DatabaseReference? = null
    private var chatMessagesListener: com.google.firebase.database.ChildEventListener? = null
    private val loadedMessages = mutableListOf<ChatMessage>()

    private fun setupChat(roomId: String) {
        loadedMessages.clear()
        _chatMessages.postValue(loadedMessages.toList())
        chatDbRef = FirebaseDatabase.getInstance().getReference("confession_rooms").child(roomId).child("chat")

        chatMessagesListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                try {
                    val chatMessage = snapshot.getValue(ChatMessage::class.java)
                    chatMessage?.let {
                        // To avoid duplicates if listener is re-attached or due to local echo
                        if (loadedMessages.none { lm -> lm.messageId == it.messageId && it.messageId.isNotBlank() }) {
                            loadedMessages.add(it)
                            _chatMessages.postValue(loadedMessages.toList().sortedBy { m -> m.timestamp })
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing chat message: ${e.message}")
                }
            }
            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) { /* Not used for chat */ }
            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) { /* Not used for chat */ }
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) { /* Not used for chat */ }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Chat listener cancelled: ${error.message}")
            }
        }
        chatDbRef?.addChildEventListener(chatMessagesListener!!)
    }

    fun sendChatMessage(text: String, priestDisplayName: String?) {
        if (text.isBlank() || currentRoomId == null) return

        val messageId = chatDbRef?.push()?.key ?: return // Generate unique ID for message
        val senderDisplayName = if (isInitiator) "Confessor" else priestDisplayName ?: "Priest"

        val chatMessage = ChatMessage(
            messageId = messageId,
            senderId = currentUserId,
            senderDisplayName = senderDisplayName, // Or more specific: "You" vs "Priest"
            text = text,
            timestamp = System.currentTimeMillis()
        )
        chatDbRef?.child(messageId)?.setValue(chatMessage)
            ?.addOnSuccessListener { Log.d(TAG, "Chat message sent successfully.") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Failed to send chat message: ${e.message}") }
    }


    // Modify initSession to include chat setup
    fun initSession(roomId: String, isCaller: Boolean, priestDisplayName: String? = null) { // priestDisplayName for chat
        if (peerConnectionFactory != null) {
            Log.d(TAG, "Session already initialized or in progress.")
            return
        }
        currentRoomId = roomId
        this.isInitiator = isCaller // Use this.isInitiator to avoid confusion with parameter
        _callStatus.postValue(CallStatus.CONNECTING)

        Log.d(TAG, "Initializing WebRTC session for room: $roomId, isCaller: $isCaller")

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(getApplication())
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val factoryOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .createPeerConnectionFactory()

        setupSignaling(roomId)
        setupChat(roomId) // Call setupChat
        createPeerConnection()

        if (this.isInitiator) { // Use this.isInitiator
            createOffer()
        }
    }

    // Modify cleanUp to remove chat listener
    private fun cleanUp() {
        Log.d(TAG, "Cleaning up WebRTC and Chat resources.")
        localAudioTrack?.dispose()
        audioSource?.dispose()
        remoteAudioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()

        localAudioTrack = null
        audioSource = null
        remoteAudioTrack = null
        peerConnection = null

        chatMessagesListener?.let { listener ->
            chatDbRef?.removeEventListener(listener)
        }
        chatMessagesListener = null
        chatDbRef = null
        loadedMessages.clear()
        _chatMessages.postValue(emptyList())

        currentRoomId?.let { roomId ->
            signalingRepository?.removeListeners(roomId)
        }
        signalingRepository = null // Also nullify the repo itself
        currentRoomId = null
    }
}
