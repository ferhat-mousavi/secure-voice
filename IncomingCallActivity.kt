package com.example.p2pvoicecall

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {
    private lateinit var callerNameTextView: TextView
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button
    
    private lateinit var smsManager: SmsManager
    private lateinit var callerPhone: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)
        
        // UI bileşenlerini başlat
        callerNameTextView = findViewById(R.id.caller_name)
        acceptButton = findViewById(R.id.accept_button)
        rejectButton = findViewById(R.id.reject_button)
        
        // SMS yöneticisini başlat
        smsManager = SmsManager(this)
        
        // Intent bilgilerini al
        callerPhone = intent.getStringExtra("caller_phone") ?: ""
        val callerName = intent.getStringExtra("caller_name") ?: "Bilinmeyen"
        val sdpType = intent.getStringExtra("sdp_type") ?: ""
        val sdp = intent.getStringExtra("sdp") ?: ""
        
        callerNameTextView.text = "$callerName arıyor..."
        
        // Kabul et butonu
        acceptButton.setOnClickListener {
            val callIntent = Intent(this, CallActivity::class.java).apply {
                putExtra("phone_number", callerPhone)
                putExtra("contact_name", callerName)
                putExtra("is_initiator", false)
                putExtra("sdp_type", sdpType)
                putExtra("sdp", sdp)
            }
            startActivity(callIntent)
            finish()
        }
        
        // Reddet butonu
        rejectButton.setOnClickListener {
            smsManager.sendCallReject(callerPhone)
            finish()
        }
    }
}
