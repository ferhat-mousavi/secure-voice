// SDP süreçleri için yardımcı sınıf
package com.example.p2pvoicecall

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
