package com.example.p2pvoicecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Base64
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SmsReceiver : BroadcastReceiver() {
    private val REQUEST_TYPE_CALL = "call_request"
    private val REQUEST_TYPE_ACCEPT = "call_accept"
    private val REQUEST_TYPE_REJECT = "call_reject"
    private val REQUEST_TYPE_ICE = "ice_candidate"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                processReceivedSms(context, smsMessage)
            }
        }
    }
    
    private fun processReceivedSms(context: Context, smsMessage: SmsMessage) {
        val sender = smsMessage.originatingAddress ?: return
        val messageBody = smsMessage.messageBody ?: return
        
        // Rehberde kayıtlı mı kontrol et
        val contactManager = ContactManager(context)
        val contact = contactManager.getContactByPhone(sender)
        
        if (contact != null) {
            try {
                // Şifrelenmiş mesajı çöz
                val encryptedData = Base64.decode(messageBody, Base64.DEFAULT)
                val keyManager = KeyManager(context)
                val decryptedJson = keyManager.decrypt(encryptedData)
                
                val jsonData = JSONObject(decryptedJson)
                val type = jsonData.getString("type")
                
                // İlgili aktiviteye yönlendir
                when (type) {
                    REQUEST_TYPE_CALL -> {
                        // Arama talebi
                        val sdpType = SessionDescription.Type.fromCanonicalForm(
                            jsonData.getString("sdp_type")
                        )
                        val sdp = jsonData.getString("sdp")
                        
                        val intent = Intent(context, IncomingCallActivity::class.java).apply {
                            putExtra("caller_phone", sender)
                            putExtra("caller_name", contact.name)
                            putExtra("sdp_type", sdpType.toString())
                            putExtra("sdp", sdp)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    
                    REQUEST_TYPE_ACCEPT -> {
                        // Arama kabul edildi
                        val sdpType = SessionDescription.Type.fromCanonicalForm(
                            jsonData.getString("sdp_type")
                        )
                        val sdp = jsonData.getString("sdp")
                        
                        val intent = Intent("ACTION_CALL_ACCEPTED").apply {
                            putExtra("caller_phone", sender)
                            putExtra("sdp_type", sdpType.toString())
                            putExtra("sdp", sdp)
                        }
                        context.sendBroadcast(intent)
                    }
                    
                    REQUEST_TYPE_REJECT -> {
                        // Arama reddedildi
                        val intent = Intent("ACTION_CALL_REJECTED").apply {
                            putExtra("caller_phone", sender)
                        }
                        context.sendBroadcast(intent)
                    }
                    
                    REQUEST_TYPE_ICE -> {
                        // ICE adayı alındı
                        val sdpMid = jsonData.getString("sdp_mid")
                        val sdpMLineIndex = jsonData.getInt("sdp_m_line_index")
                        val candidate = jsonData.getString("candidate")
                        
                        val intent = Intent("ACTION_ICE_CANDIDATE").apply {
                            putExtra("caller_phone", sender)
                            putExtra("sdp_mid", sdpMid)
                            putExtra("sdp_m_line_index", sdpMLineIndex)
                            putExtra("candidate", candidate)
                        }
                        context.sendBroadcast(intent)
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Şifre çözme hatası veya geçersiz JSON formatı
            }
        }
    }
}
