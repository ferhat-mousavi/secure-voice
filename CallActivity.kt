package com.example.p2pvoicecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class CallActivity : AppCompatActivity() {
    private val TAG = "CallActivity"
    
    private lateinit var contactNameTextView: TextView
    private lateinit var callStatusTextView: TextView
    private lateinit var endCallButton: Button
    
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    
    private lateinit var smsManager: SmsManager
    private var isInitiator: Boolean = false
    private lateinit var remotePhoneNumber: String
    
    // Gelen ICE adayları ve SDP yanıtları için BroadcastReceiver
    private val callEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "ACTION_CALL_ACCEPTED" -> {
                    val sdpType = SessionDescription.Type.valueOf(
                        intent.getStringExtra("sdp_type") ?: return
                    )
                    val sdpDescription = intent.getStringExtra("sdp") ?: return
                    val sessionDescription = SessionDescription(sdpType, sdpDescription)
                    
                    // Karşı tarafın cevabını işle
                    peerConnection?.setRemoteDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            callStatusTextView.text = "Bağlantı kuruldu"
                        }
                        
                        override fun onSetFailure(error: String?) {
                            Toast.makeText(this@CallActivity, "SDP ayarlama hatası: $error", Toast.LENGTH_SHORT).show()
                        }
                    }, sessionDescription)
                }
                
                "ACTION_CALL_REJECTED" -> {
                    Toast.makeText(this@CallActivity, "Arama reddedildi", Toast.LENGTH_SHORT).show()
                    finish()
                }
                
                "ACTION_ICE_CANDIDATE" -> {
                    val sdpMid = intent.getStringExtra("sdp_mid") ?: return
                    val sdpMLineIndex = intent.getIntExtra("sdp_m_line_index", 0)
                    val candidate = intent.getStringExtra("candidate") ?: return
                    
                    // ICE adayını ekle
                    val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                    peerConnection?.addIceCandidate(iceCandidate)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        
        // UI bileşenlerini başlat
        contactNameTextView = findViewById(R.id.contact_name)
        callStatusTextView = findViewById(R.id.call_status)
        endCallButton = findViewById(R.id.end_call_button)
        
        // SMS yöneticisini başlat
        smsManager = SmsManager(this)
        
        // Intent bilgilerini al
        remotePhoneNumber = intent.getStringExtra("phone_number") ?: ""
        val contactName = intent.getStringExtra("contact_name") ?: "Bilinmeyen"
        isInitiator = intent.getBooleanExtra("is_initiator", false)
        
        contactNameTextView.text = contactName
        
        // Broadcast receiver'ı kaydet
        val intentFilter = IntentFilter().apply {
            addAction("ACTION_CALL_ACCEPTED")
            addAction("ACTION_CALL_REJECTED")
            addAction("ACTION_ICE_CANDIDATE")
        }
        registerReceiver(callEventReceiver, intentFilter)
        
        // WebRTC'yi başlat
        initializeWebRTC()
        
        // Arama başlat veya gelen aramayı işle
        if (isInitiator) {
            startCall()
        } else {
            // Gelen arama
            val sdpType = SessionDescription.Type.valueOf(
                intent.getStringExtra("sdp_type") ?: return
            )
            val sdpDescription = intent.getStringExtra("sdp") ?: return
            handleIncomingCall(SessionDescription(sdpType, sdpDescription))
        }
        
        // Arama sonlandır butonu
        endCallButton.setOnClickListener {
            endCall()
        }
    }
    
    private fun initializeWebRTC() {
        // WebRTC başlatma
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        
        // PeerConnectionFactory oluştur
        val options = PeerConnectionFactory.Options()
        
        // Ses modülü oluştur
        val audioDeviceModule = JavaAudioDeviceModule.builder(this)
            .createAudioDeviceModule()
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        
        // Ses kaynağı oluştur
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        
        // PeerConnection oluştur
        createPeerConnection()
    }
    
    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = RTCConfiguration(iceServers)
        
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                // ICE adayını karşı tarafa gönder
                iceCandidate?.let {
                    smsManager.sendIceCandidate(remotePhoneNumber, it)
                }
            }
            
            override fun onAddStream(mediaStream: MediaStream?) {
                // Karşı tarafın ses akışını al
                runOnUiThread {
                    mediaStream?.audioTracks?.get(0)?.setEnabled(true)
                }
            }
            
            override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {}
            
            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    when (iceConnectionState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            callStatusTextView.text = "Bağlantı kuruldu"
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            callStatusTextView.text = "Bağlantı kesildi"
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            callStatusTextView.text = "Bağlantı başarısız"
                        }
                        else -> {}
                    }
                }
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            
            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {}
            
            override fun onRemoveStream(mediaStream: MediaStream?) {}
            
            override fun onDataChannel(dataChannel: DataChannel?) {}
            
            override fun onRenegotiationNeeded() {}
            
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })
        
        // Yerel ses akışını ekle
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(localAudioTrack)
        peerConnection?.addStream(mediaStream)
    }
    
    private fun startCall() {
        callStatusTextView.text = "Aranıyor..."
        
        // Offer oluştur
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        peerConnection?.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver() {
                    override fun onSetSuccess() {
                        // Offer'ı karşı tarafa SMS ile gönder
                        sessionDescription?.let {
                            smsManager.sendCallRequest(remotePhoneNumber, it)
                        }
                    }
                }, sessionDescription)
            }
            
            override fun onCreateFailure(error: String?) {
                Toast.makeText(this@CallActivity, "Offer oluşturma hatası: $error", Toast.LENGTH_SHORT).show()
            }
        }, mediaConstraints)
    }
    
    private fun handleIncomingCall(remoteSdp: SessionDescription) {
        callStatusTextView.text = "Gelen arama yanıtlanıyor..."
        
        // Karşı tarafın SDP'sini ayarla
        peerConnection?.setRemoteDescription(object : SdpObserver() {
            override fun onSetSuccess() {
                // Answer oluştur
                val mediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                
                peerConnection?.createAnswer(object : SdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        peerConnection?.setLocalDescription(object : SdpObserver() {
                            override fun onSetSuccess() {
                                // Answer'ı karşı tarafa SMS ile gönder
                                sessionDescription?.let {
                                    smsManager.sendCallAccept(remotePhoneNumber, it)
                                }
                            }
                        }, sessionDescription)
                    }
                    
                    override fun onCreateFailure(error: String?) {
                        Toast.makeText(this@CallActivity, "Answer oluşturma hatası: $error", Toast.LENGTH_SHORT).show()
                    }
                }, mediaConstraints)
            }
            
            override fun onSetFailure(error: String?) {
                Toast.makeText(this@CallActivity, "Remote SDP ayarlama hatası: $error", Toast.LENGTH_SHORT).show()
            }
        }, remoteSdp)
    }
    
    private fun endCall() {
        // Aramayı sonlandır
        peerConnection?.close()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callEventReceiver)
            peerConnection?.close()
            audioSource.dispose()
            peerConnectionFactory.dispose()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
