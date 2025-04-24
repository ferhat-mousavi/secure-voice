package com.example.p2pvoicecall

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

class ContactManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ContactsDB"
        private const val TABLE_CONTACTS = "contacts"
        private const val KEY_ID = "id"
        private const val KEY_PHONE = "phone"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_NAME = "name"
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_CONTACTS_TABLE = ("CREATE TABLE $TABLE_CONTACTS (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_PHONE TEXT," +
                "$KEY_PUBLIC_KEY TEXT," +
                "$KEY_NAME TEXT)")
        db.execSQL(CREATE_CONTACTS_TABLE)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        onCreate(db)
    }
    
    // Yeni kişi ekle
    fun saveContact(phoneNumber: String, publicKeyBytes: ByteArray) {
        val db = this.writableDatabase
        val values = ContentValues()
        
        values.put(KEY_PHONE, phoneNumber)
        values.put(KEY_PUBLIC_KEY, Base64.encodeToString(publicKeyBytes, Base64.DEFAULT))
        values.put(KEY_NAME, "Yeni Kişi") // Kullanıcı daha sonra isim ekleyebilir
        
        db.insert(TABLE_CONTACTS, null, values)
        db.close()
    }
    
    // Tüm kişileri getir
    fun getAllContacts(): List<Contact> {
        val contactList = ArrayList<Contact>()
        val selectQuery = "SELECT * FROM $TABLE_CONTACTS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHONE))
                val publicKeyString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PUBLIC_KEY))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME))
                
                val contact = Contact(id, phone, publicKeyString, name)
                contactList.add(contact)
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        return contactList
    }
    
    // Telefon numarasına göre kişi getir
    fun getContactByPhone(phoneNumber: String): Contact? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_CONTACTS,
            arrayOf(KEY_ID, KEY_PHONE, KEY_PUBLIC_KEY, KEY_NAME),
            "$KEY_PHONE = ?",
            arrayOf(phoneNumber),
            null, null, null
        )
        
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID))
            val phone = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHONE))
            val publicKeyString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PUBLIC_KEY))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME))
            
            cursor.close()
            Contact(id, phone, publicKeyString, name)
        } else {
            cursor?.close()
            null
        }
    }
    
    // Public key'i al
    fun getPublicKey(phoneNumber: String): PublicKey? {
        val contact = getContactByPhone(phoneNumber) ?: return null
        
        val publicKeyBytes = Base64.decode(contact.publicKeyBase64, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
    }
}

// İletişim Sınıfı
data class Contact(
    val id: Int,
    val phoneNumber: String,
    val publicKeyBase64: String,
    val name: String
)
