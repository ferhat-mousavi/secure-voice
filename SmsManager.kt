package com.example.p2pvoicecall

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Base64
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SmsManager(private val context: Context) {
    private val REQUEST_TYPE_CALL = "call_request"
    private val REQUEST_TYPE_ACCEPT = "call_accept"
    private val REQUEST_TYPE_REJECT = "call_reject"
    private val REQUEST_TYPE_ICE = "ice_candidate"
    
    private val smsManager = android.telephony.SmsManager.getDefault()
    private val keyManager = KeyManager(context)
    private val contactManager = ContactManager(context)
    
    // Arama talebi gönder
    fun sendCallRequest(phoneNumber: String, sdp: SessionDescription) {
        val publicKey = contactManager.getPublicKey(phoneNumber) ?: return
        
        val jsonData = JSONObject().apply {
            put("type", REQUEST_TYPE_CALL)
            put("sdp_type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        
        val encryptedData = keyManager.encrypt(jsonData.toString(), publicKey)
        val encodedMessage = Base64.encodeToString(encryptedData, Base64.DEFAULT)
        
        sendSms(phoneNumber, encodedMessage)
    }
    
    // Arama talebini kabul et
    fun sendCallAccept(phoneNumber: String, sdp: SessionDescription) {
        val publicKey = contactManager.getPublicKey(phoneNumber) ?: return
        
        val jsonData = JSONObject().apply {
            put("type", REQUEST_TYPE_ACCEPT)
            put("sdp_type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        
        val encryptedData = keyManager.encrypt(jsonData.toString(), publicKey)
        val encodedMessage = Base64.encodeToString(encryptedData, Base64.DEFAULT)
        
        sendSms(phoneNumber, encodedMessage)
    }
    
    // Arama talebini reddet
    fun sendCallReject(phoneNumber: String) {
        val publicKey = contactManager.getPublicKey(phoneNumber) ?: return
        
        val jsonData = JSONObject().apply {
            put("type", REQUEST_TYPE_REJECT)
        }
        
        val encryptedData = keyManager.encrypt(jsonData.toString(), publicKey)
        val encodedMessage = Base64.encodeToString(encryptedData, Base64.DEFAULT)
        
        sendSms(phoneNumber, encodedMessage)
    }
    
    // ICE adayını gönder
    fun sendIceCandidate(phoneNumber: String, iceCandidate: IceCandidate) {
        val publicKey = contactManager.getPublicKey(phoneNumber) ?: return
        
        val jsonData = JSONObject().apply {
            put("type", REQUEST_TYPE_ICE)
            put("sdp_mid", iceCandidate.sdpMid)
            put("sdp_m_line_index", iceCandidate.sdpMLineIndex)
            put("candidate", iceCandidate.sdp)
        }
        
        val encryptedData = keyManager.encrypt(jsonData.toString(), publicKey)
        val encodedMessage = Base64.encodeToString(encryptedData, Base64.DEFAULT)
        
        sendSms(phoneNumber, encodedMessage)
    }
    
    // SMS gönder
    private fun sendSms(phoneNumber: String, message: String) {
        val sentPI = PendingIntent.getBroadcast(context, 0, Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE)
        
        if (message.length > 160) {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
        }
    }
}
