package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.example.data.model.Contact
import com.example.data.model.Invoice
import com.example.data.model.Item
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirestoreSyncManager(private val context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.create(context, "afaq_sync_settings")

    private fun getFirestoreInstance(): FirebaseFirestore? {
        return try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getInstance()
            }
            if (app == null) return null
            FirebaseFirestore.getInstance(app)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private var itemsListener: ListenerRegistration? = null
    private var contactsListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null

    private val _isSyncEnabled = MutableStateFlow(prefs.getBoolean("is_sync_enabled", false))
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _tenantId = MutableStateFlow(getOrGenerateTenantId())
    val tenantId: StateFlow<String> = _tenantId.asStateFlow()

    private val _syncStatus = MutableStateFlow(if (_isSyncEnabled.value) "متصل بالشبكة ✅" else "غير مفعّل ⏸️")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun getOrGenerateTenantId(): String {
        var id = prefs.getString("tenant_id", "") ?: ""
        if (id.isEmpty()) {
            id = "AFAQ-" + UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("tenant_id", id).apply()
        }
        return id
    }

    fun setTenantId(newTenantId: String) {
        val clean = newTenantId.trim().uppercase()
        if (clean.isNotEmpty()) {
            prefs.edit().putString("tenant_id", clean).apply()
            _tenantId.value = clean
            if (_isSyncEnabled.value) {
                stopListening()
                startListening({}, {}, {})
            }
        }
    }

    fun setSyncEnabled(enabled: Boolean, onItemsChanged: (List<Item>) -> Unit = {}, onContactsChanged: (List<Contact>) -> Unit = {}, onInvoicesChanged: (List<Invoice>) -> Unit = {}) {
        if (enabled) {
            val db = getFirestoreInstance()
            if (db == null) {
                _isSyncEnabled.value = false
                prefs.edit().putBoolean("is_sync_enabled", false).apply()
                _syncStatus.value = "⚠️ غير مفعّل: يتطلب إضافة google-services.json"
                Toast.makeText(context, "⚠️ تعذر تفعيل التزامن: يتطلب إضافة ملف google-services.json الخاص بـ Firebase في المشروع", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit().putBoolean("is_sync_enabled", true).apply()
            _isSyncEnabled.value = true
            _syncStatus.value = "متصل بالشبكة ✅"
            startListening(onItemsChanged, onContactsChanged, onInvoicesChanged)
        } else {
            prefs.edit().putBoolean("is_sync_enabled", false).apply()
            _isSyncEnabled.value = false
            _syncStatus.value = "غير مفعّل ⏸️"
            stopListening()
        }
    }

    fun startListening(
        onItemsChanged: (List<Item>) -> Unit,
        onContactsChanged: (List<Contact>) -> Unit,
        onInvoicesChanged: (List<Invoice>) -> Unit
    ) {
        if (!_isSyncEnabled.value) return
        val currentTenant = tenantId.value
        if (currentTenant.isEmpty()) return

        stopListening()

        val db = getFirestoreInstance()
        if (db == null) {
            _syncStatus.value = "⚠️ يتطلب إعداد ملف google-services.json"
            _isSyncEnabled.value = false
            prefs.edit().putBoolean("is_sync_enabled", false).apply()
            return
        }

        try {
            // Listen to items
            itemsListener = db.collection("tenants")
                .document(currentTenant)
                .collection("items")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _syncStatus.value = "خطأ الاتصال: ${error.message}"
                        return@addSnapshotListener
                    }
                    _syncStatus.value = "متزامن لحظياً ✅"
                    snapshot?.let { snap ->
                        val itemList = snap.documents.mapNotNull { doc ->
                            try {
                                Item(
                                    id = doc.getLong("id") ?: 0L,
                                    code = doc.getString("code") ?: "",
                                    barcode = doc.getString("barcode") ?: "",
                                    name = doc.getString("name") ?: "",
                                    category = doc.getString("category") ?: "",
                                    unit = doc.getString("unit") ?: "حبة",
                                    brand = doc.getString("brand") ?: "",
                                    color = doc.getString("color") ?: "",
                                    size = doc.getString("size") ?: "",
                                    location = doc.getString("location") ?: "",
                                    minLimit = doc.getDouble("minLimit") ?: 0.0,
                                    maxLimit = doc.getDouble("maxLimit") ?: 9999.0,
                                    purchasePrice = doc.getDouble("purchasePrice") ?: 0.0,
                                    salePrice = doc.getDouble("salePrice") ?: 0.0,
                                    wholesalePrice = doc.getDouble("wholesalePrice") ?: 0.0,
                                    specialPrice = doc.getDouble("specialPrice") ?: 0.0,
                                    currentQuantity = doc.getDouble("currentQuantity") ?: 0.0,
                                    notes = doc.getString("notes") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (itemList.isNotEmpty()) {
                            onItemsChanged(itemList)
                        }
                    }
                }

            // Listen to contacts
            contactsListener = db.collection("tenants")
                .document(currentTenant)
                .collection("contacts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        val contactList = snap.documents.mapNotNull { doc ->
                            try {
                                Contact(
                                    id = doc.getLong("id") ?: 0L,
                                    type = doc.getString("type") ?: "CUSTOMER",
                                    name = doc.getString("name") ?: "",
                                    phone = doc.getString("phone") ?: "",
                                    address = doc.getString("address") ?: "",
                                    balance = doc.getDouble("balance") ?: 0.0,
                                    creditLimit = doc.getDouble("creditLimit") ?: 0.0,
                                    notes = doc.getString("notes") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (contactList.isNotEmpty()) {
                            onContactsChanged(contactList)
                        }
                    }
                }
        } catch (e: Throwable) {
            e.printStackTrace()
            _syncStatus.value = "⚠️ فشل بدء استماع التزامن"
        }
    }

    fun stopListening() {
        try {
            itemsListener?.remove()
            contactsListener?.remove()
            invoicesListener?.remove()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        itemsListener = null
        contactsListener = null
        invoicesListener = null
    }

    // Push operations
    fun pushItem(item: Item) {
        if (!_isSyncEnabled.value) return
        try {
            val db = getFirestoreInstance() ?: return
            val currentTenant = tenantId.value
            val map = mapOf(
                "id" to item.id,
                "code" to item.code,
                "barcode" to item.barcode,
                "name" to item.name,
                "category" to item.category,
                "unit" to item.unit,
                "brand" to item.brand,
                "color" to item.color,
                "size" to item.size,
                "location" to item.location,
                "minLimit" to item.minLimit,
                "maxLimit" to item.maxLimit,
                "purchasePrice" to item.purchasePrice,
                "salePrice" to item.salePrice,
                "wholesalePrice" to item.wholesalePrice,
                "specialPrice" to item.specialPrice,
                "currentQuantity" to item.currentQuantity,
                "notes" to item.notes,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("tenants")
                .document(currentTenant)
                .collection("items")
                .document(item.id.toString())
                .set(map)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun deleteItem(itemId: Long) {
        if (!_isSyncEnabled.value) return
        try {
            val db = getFirestoreInstance() ?: return
            db.collection("tenants")
                .document(tenantId.value)
                .collection("items")
                .document(itemId.toString())
                .delete()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun pushContact(contact: Contact) {
        if (!_isSyncEnabled.value) return
        try {
            val db = getFirestoreInstance() ?: return
            val currentTenant = tenantId.value
            val map = mapOf(
                "id" to contact.id,
                "type" to contact.type,
                "name" to contact.name,
                "phone" to contact.phone,
                "address" to contact.address,
                "balance" to contact.balance,
                "creditLimit" to contact.creditLimit,
                "notes" to contact.notes,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("tenants")
                .document(currentTenant)
                .collection("contacts")
                .document(contact.id.toString())
                .set(map)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun deleteContact(contactId: Long) {
        if (!_isSyncEnabled.value) return
        try {
            val db = getFirestoreInstance() ?: return
            db.collection("tenants")
                .document(tenantId.value)
                .collection("contacts")
                .document(contactId.toString())
                .delete()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun pushInvoice(invoice: Invoice) {
        if (!_isSyncEnabled.value) return
        try {
            val db = getFirestoreInstance() ?: return
            val currentTenant = tenantId.value
            val map = mapOf(
                "id" to invoice.id,
                "invoiceNumber" to invoice.invoiceNumber,
                "type" to invoice.type,
                "contactId" to (invoice.contactId ?: 0L),
                "timestamp" to invoice.timestamp,
                "subTotal" to invoice.subTotal,
                "discount" to invoice.discount,
                "tax" to invoice.tax,
                "total" to invoice.total,
                "paidAmount" to invoice.paidAmount,
                "remainingAmount" to invoice.remainingAmount,
                "paymentMethod" to invoice.paymentMethod,
                "notes" to invoice.notes,
                "userId" to invoice.userId,
                "currencyCode" to invoice.currencyCode,
                "exchangeRate" to invoice.exchangeRate,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("tenants")
                .document(currentTenant)
                .collection("invoices")
                .document(invoice.id.toString())
                .set(map)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
