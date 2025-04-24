package com.example.p2pvoicecall

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class KeyManager(private val context: Context) {
    private val SHARED_PREFS_NAME = "KeyManagerPrefs"
    private val PUBLIC_KEY = "my_public_key"
    private val PRIVATE_KEY = "my_private_key"
    
    // Kendi anahtar çiftimi kaydet
    fun saveMyKeyPair(keyPair: KeyPair) {
        val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        val publicKeyString = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)
        val privateKeyString = Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT)
        
        editor.putString(PUBLIC_KEY, publicKeyString)
        editor.putString(PRIVATE_KEY, privateKeyString)
        editor.apply()
    }
    
    // Kendi anahtar çiftimi al
    fun getMyKeyPair(): KeyPair? {
        val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        
        val publicKeyString = sharedPrefs.getString(PUBLIC_KEY, null)
        val privateKeyString = sharedPrefs.getString(PRIVATE_KEY, null)
        
        if (publicKeyString != null && privateKeyString != null) {
            val publicKeyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
            val privateKeyBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
            
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            
            return KeyPair(publicKey, privateKey)
        }
        
        return null
    }
    
    // Verilen public key ile şifrele
    fun encrypt(data: String, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data.toByteArray())
    }
    
    // Kendi private key'im ile şifre çöz
    fun decrypt(encryptedData: ByteArray): String {
        val keyPair = getMyKeyPair() ?: throw Exception("KeyPair bulunamadı")
        
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decryptedBytes = cipher.doFinal(encryptedData)
        return String(decryptedBytes)
    }
}
