package com.example.p2pvoicecall

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var addContactButton: Button
    
    private lateinit var contactManager: ContactManager
    private lateinit var contactAdapter: ContactAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // UI bileşenlerini başlat
        contactsRecyclerView = findViewById(R.id.contacts_recycler_view)
        addContactButton = findViewById(R.id.add_contact_button)
        
        // Kişi yöneticisini başlat
        contactManager = ContactManager(this)
        
        // RecyclerView'ı ayarla
        contactsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Kişileri yükle
        loadContacts()
        
        // Kişi ekle butonu
        addContactButton.setOnClickListener {
            val intent = Intent(this, BluetoothPairingActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadContacts() {
        val contacts = contactManager.getAllContacts()
        
        contactAdapter = ContactAdapter(contacts) { contact ->
            // Kişiye tıklandığında arama başlat
            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("phone_number", contact.phoneNumber)
                putExtra("contact_name", contact.name)
                putExtra("is_initiator", true)
            }
            startActivity(intent)
        }
        
        contactsRecyclerView.adapter = contactAdapter
    }
    
    override fun onResume() {
        super.onResume()
        // Aktivite her görüntülendiğinde kişileri yenile
        loadContacts()
    }
}
