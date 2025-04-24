package com.example.p2pvoicecall

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*

class BluetoothPairingActivity : AppCompatActivity() {
    private val TAG = "BluetoothPairingActivity"
    private val APP_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    private val APP_NAME = "P2PVoiceCall"
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var devicesListView: ListView
    private lateinit var scanButton: Button
    private lateinit var devicesList: ArrayList<BluetoothDevice>
    private lateinit var devicesAdapter: ArrayAdapter<String>
    
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val PERMISSION_REQUEST_CODE = 1
    
    private lateinit var keyPair: KeyPair
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_pairing)
        
        // Bluetooth adaptörünü al
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bu cihaz Bluetooth desteklemiyor", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // İzinleri kontrol et
        if (!checkPermissions()) {
            requestPermissions()
        }
        
        // RSA anahtar çiftini oluştur
        generateKeyPair()
        
        // UI bileşenlerini başlat
        devicesListView = findViewById(R.id.devices_list)
        scanButton = findViewById(R.id.scan_button)
        
        devicesList = ArrayList()
        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        devicesListView.adapter = devicesAdapter
        
        // Tarama butonuna tıklama
        scanButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                startDiscovery()
            } else {
                Toast.makeText(this, "Bluetooth tarama izni gerekli", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Listedeki cihaza tıklama
        devicesListView.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter.cancelDiscovery()
            val device = devicesList[position]
            connectToDevice(device)
        }
        
        // Bluetooth tarama sonuçlarını dinle
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryReceiver, filter)
        
        // Bluetooth sunucu soketi başlat
        startBluetoothServer()
    }
    
    // RSA anahtar çiftini oluştur
    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        keyPair = keyPairGenerator.generateKeyPair()
        
        // Anahtarı cihaz deposuna kaydet
        val keyManager = KeyManager(this)
        keyManager.saveMyKeyPair(keyPair)
    }
    
    // İzinleri kontrol et
    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
    
    // İzinleri iste
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }
    
    // Bluetooth cihazlarını taramaya başla
    private fun startDiscovery() {
        devicesAdapter.clear()
        devicesList.clear()
        
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        bluetoothAdapter.startDiscovery()
        Toast.makeText(this, "Tarama başladı...", Toast.LENGTH_SHORT).show()
    }
    
    // Bulunan Bluetooth cihazlarını dinleyen receiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.name != null) {
                        if (ContextCompat.checkSelfPermission(this@BluetoothPairingActivity, 
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            devicesList.add(device)
                            devicesAdapter.add(device.name)
                            devicesAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }
    
    // Bluetooth sunucusunu başlat
    private fun startBluetoothServer() {
        Thread {
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                        == PackageManager.PERMISSION_GRANTED) {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                    var socket: BluetoothSocket?
                    
                    while (true) {
                        try {
                            socket = serverSocket?.accept()
                            if (socket != null) {
                                // Bağlantı kabul edildi
                                handleBluetoothConnection(socket)
                                serverSocket?.close()
                                break
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Socket accept() failed", e)
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "ServerSocket creation failed", e)
            }
        }.start()
    }
    
    // Diğer cihaza bağlan
    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                        == PackageManager.PERMISSION_GRANTED) {
                    clientSocket = device.createRfcommSocketToServiceRecord(APP_UUID)
                    clientSocket?.connect()
                    
                    runOnUiThread {
                        Toast.makeText(this, "${device.name} ile bağlantı kuruldu", Toast.LENGTH_SHORT).show()
                    }
                    
                    handleBluetoothConnection(clientSocket!!)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect to device", e)
                runOnUiThread {
                    Toast.makeText(this, "Bağlantı başarısız", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
        }.start()
    }
    
    // Bluetooth bağlantısını yönet
    private fun handleBluetoothConnection(socket: BluetoothSocket) {
        try {
            val inputStream = socket.inputStream
            val outputStream = socket.outputStream
            
            // Kendi public key'imi gönder
            val publicKeyBytes = keyPair.public.encoded
            outputStream.write(publicKeyBytes.size)
            outputStream.write(publicKeyBytes)
            
            // Karşı tarafın public key'ini al
            val sizeBuffer = ByteArray(1)
            inputStream.read(sizeBuffer)
            val size = sizeBuffer[0].toInt()
            
            val peerPublicKeyBytes = ByteArray(size)
            inputStream.read(peerPublicKeyBytes)
            
            // Karşı tarafın telefon numarasını al
            val phoneBytes = ByteArray(15) // Maksimum telefon numarası uzunluğu
            inputStream.read(phoneBytes)
            val phoneNumber = String(phoneBytes).trim { it <= ' ' }
            
            // Kendi telefon numaramı gönder
            val myPhoneNumber = getMyPhoneNumber()
            outputStream.write(myPhoneNumber.toByteArray())
            
            // Karşı tarafın public key'ini ve telefon numarasını kaydet
            val contactManager = ContactManager(this)
            contactManager.saveContact(phoneNumber, peerPublicKeyBytes)
            
            runOnUiThread {
                Toast.makeText(this, "Eşleşme tamamlandı! Rehbere eklendi.", Toast.LENGTH_LONG).show()
                // Ana ekrana dön
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Error in data exchange", e)
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the socket", e)
            }
        }
    }
    
    // Kendi telefon numaramı al (örnek)
    private fun getMyPhoneNumber(): String {
        // Gerçek uygulamada kullanıcıdan telefon numarasını iste
        return "+905551234567"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(discoveryReceiver)
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error on destroy", e)
        }
    }
}
