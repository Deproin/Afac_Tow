package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.api.SupabaseApiService
import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseSyncManager(private val context: Context) {
    
    private var supabaseUrl: String = com.example.BuildConfig.SUPABASE_URL
    private var supabaseAnonKey: String = com.example.BuildConfig.SUPABASE_ANON_KEY
    private var supabaseAccessToken: String = ""
    private val authHeader: String
        get() = if (supabaseAccessToken.isNotEmpty()) "Bearer $supabaseAccessToken" else "Bearer $supabaseAnonKey"
        
    private var companyId: String = "" // This is the Company ID

    val isSyncEnabled = MutableStateFlow(false)
    val syncStatusMessage = MutableStateFlow("جاري الاتصال بالسحابة...")
    
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val safeUrl: String
        get() {
            val url = supabaseUrl
            return if (url.endsWith("/")) url else "$url/"
        }

    private val retrofit by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
    }

    private val api by lazy { retrofit.create(SupabaseApiService::class.java) }
    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    private val db = AppDatabase.getDatabase(context)
    private val syncDao = db.syncDao()

    fun initialize(url: String, key: String, companyId: String) {
        if (url.isNotEmpty()) this.supabaseUrl = url
        if (key.isNotEmpty()) this.supabaseAnonKey = key
        this.companyId = companyId
        
        if (this.companyId.isNotEmpty()) {
            isSyncEnabled.value = true
            connectRealtime()
            startSyncWorker()
            coroutineScope.launch {
                try {
                    val res = api.getCompanies(supabaseAnonKey, authHeader)
                    if (res.isSuccessful) {
                        syncStatusMessage.value = "متصل بالسحابة 🟢"
                    } else {
                        Log.e("SupabaseSync", "Connection failed with HTTP code: ${res.code()}")
                        syncStatusMessage.value = "خطأ في الاتصال بالسحابة 🔴 (${res.code()})"
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Connection error: ${e.message}")
                    syncStatusMessage.value = "غير متصل بالسحابة 🟠"
                }
                pushPendingChanges()
                downloadAllCompanyData()
            }
        }
    }
    
    private fun connectRealtime() {
        if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) return
        
        val wsUrl = supabaseUrl.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=$supabaseAnonKey&v=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SupabaseSync", "Connected to Supabase Realtime")
                syncStatusMessage.value = "متصل بالسحابة 🟢"
                subscribeToDatabaseChanges(webSocket)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingChange(text)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                syncStatusMessage.value = "غير متصل 🔴"
                reconnect()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                syncStatusMessage.value = "خطأ في الاتصال ⚠️"
                reconnect()
            }
        })
    }
    
    private fun subscribeToDatabaseChanges(ws: WebSocket) {
        // We only subscribe to events where company_id = our companyId, if possible.
        // Supabase Realtime allows filtering if RLS is setup, but we listen to public schema.
        val joinMsg = """
            {
                "topic": "realtime:public",
                "event": "phx_join",
                "payload": {},
                "ref": "1"
            }
        """.trimIndent()
        ws.send(joinMsg)
    }
    
    private fun handleIncomingChange(jsonPayload: String) {
        try {
            val json = JSONObject(jsonPayload)
            if (json.optString("event") == "phx_reply") return
            
            val payload = json.optJSONObject("payload") ?: return
            val type = payload.optString("type")
            val table = payload.optString("table")
            val record = payload.optJSONObject("record") ?: payload.optJSONObject("old_record")
            
            if (record != null) {
                val recCompany = record.optString("company_id")
                if (recCompany.isNotEmpty() && recCompany != companyId) return
            }

            Log.d("SupabaseSync", "Realtime change received: $type on $table")

            // Instantly pull changes from cloud into local Room database
            coroutineScope.launch {
                try {
                    downloadAllCompanyData()
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Error pulling realtime changes: ${e.message}")
                }
            }

            // Real-time user permissions and auto-kick check if 'users' table changed
            if (table == "users" && record != null) {
                val updatedSyncId = record.optString("syncid")
                val isSuspended = record.optBoolean("issuspended", false)
                
                coroutineScope.launch {
                    val allUsers = db.userDao().getAllUsersList()
                    val targetUser = allUsers.find { it.syncId == updatedSyncId }
                    if (targetUser != null) {
                        if (isSuspended) {
                            Log.w("SupabaseSync", "User account suspended in realtime!")
                            val suspendedUser = targetUser.copy(isSuspended = true)
                            db.userDao().updateUser(suspendedUser)
                        } else {
                            val updatedUser = targetUser.copy(
                                isSuspended = isSuspended,
                                permSale = record.optBoolean("permsale", targetUser.permSale),
                                permPurchase = record.optBoolean("permpurchase", targetUser.permPurchase),
                                permDeleteInvoice = record.optBoolean("permdeleteinvoice", targetUser.permDeleteInvoice),
                                permEditInvoice = record.optBoolean("permeditinvoice", targetUser.permEditInvoice),
                                permViewProfits = record.optBoolean("permviewprofits", targetUser.permViewProfits),
                                permViewReports = record.optBoolean("permviewreports", targetUser.permViewReports),
                                permEditPrices = record.optBoolean("permeditprices", targetUser.permEditPrices),
                                permBackup = record.optBoolean("permbackup", targetUser.permBackup),
                                permSettings = record.optBoolean("permsettings", targetUser.permSettings),
                                permAI = record.optBoolean("permai", targetUser.permAI),
                                permAccountStatement = record.optBoolean("permaccountstatement", targetUser.permAccountStatement),
                                permStocktake = record.optBoolean("permstocktake", targetUser.permStocktake),
                                permPrint = record.optBoolean("permprint", targetUser.permPrint),
                                permShare = record.optBoolean("permshare", targetUser.permShare)
                            )
                            db.userDao().updateUser(updatedUser)
                            Log.d("SupabaseSync", "Updated user permissions in Room in real time!")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun reconnect() {
        if (!isSyncEnabled.value) return
        coroutineScope.launch {
            delay(5000)
            connectRealtime()
        }
    }
    
    fun disconnect() {
        isSyncEnabled.value = false
        syncJob?.cancel()
        webSocket?.close(1000, "User logged out")
        webSocket = null
    }

    private fun startSyncWorker() {
        syncJob?.cancel()
        syncJob = coroutineScope.launch {
            while (isActive && isSyncEnabled.value) {
                try {
                    pushPendingChanges()
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Push failed: ${e.message}")
                }
                delay(10000) // check every 10 seconds
            }
        }
    }

    suspend fun pushPendingChanges() {
        if (companyId.isEmpty() || companyId == "company-default") {
            val syncCode = LicenseManager(context).getCompanySyncCode()
            if (syncCode.isNotEmpty() && syncCode != "company-default") {
                companyId = syncCode
            } else {
                val storeName = db.enterpriseSettingDao().getSettingsDirect()?.name?.trim()
                if (!storeName.isNullOrEmpty()) {
                    companyId = storeName
                }
            }
        }
        if (companyId.isEmpty() || companyId == "company-default") return

        // Ensure company exists to avoid foreign key constraint violations
        try {
            val storeName = db.enterpriseSettingDao().getSettingsDirect()?.name?.trim()?.ifEmpty { companyId } ?: companyId
            val companyDto = CompanyDto(companyId = companyId, name = storeName, createdAt = System.currentTimeMillis())
            val compRes = api.createCompany(companyDto, supabaseAnonKey, authHeader)
            if (!compRes.isSuccessful) {
                Log.e("SupabaseSync", "Failed to create company ${companyId}: code=${compRes.code()}, error=${compRes.errorBody()?.string()}")
            } else {
                Log.d("SupabaseSync", "Successfully created company ${companyId} on Supabase!")
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to upsert company: ${e.message}")
        }

        // Users - Push all active users to ensure employees are available in Supabase
        val users = db.userDao().getAllUsersList()
        users.forEach { user ->
            val dto = UserDto(
                syncId = user.syncId, companyId = companyId, username = user.username, passwordHash = user.passwordHash,
                isSuspended = user.isSuspended, lastLogin = user.lastLogin, operationCount = user.operationCount,
                permSale = user.permSale, permPurchase = user.permPurchase, permDeleteInvoice = user.permDeleteInvoice,
                permEditInvoice = user.permEditInvoice, permViewProfits = user.permViewProfits, permViewReports = user.permViewReports,
                permEditPrices = user.permEditPrices, permBackup = user.permBackup, permSettings = user.permSettings,
                permAI = user.permAI, permAccountStatement = user.permAccountStatement, permStocktake = user.permStocktake,
                permPrint = user.permPrint, permShare = user.permShare, syncState = "SYNCED", updatedAt = user.updatedAt, isDeleted = user.isDeleted
            )
            val res = api.upsertUser(dto, supabaseAnonKey, authHeader)
            if (res.isSuccessful) {
                syncDao.markUserSynced(user.syncId)
            } else {
                Log.e("SupabaseSync", "Failed to upsert user ${user.username}: code=${res.code()}, error=${res.errorBody()?.string()}")
            }
        }

        // Items
        val items = syncDao.getPendingItems()
        items.forEach { item ->
            val dto = ItemDto(
                syncId = item.syncId, companyId = companyId, code = item.code, barcode = item.barcode, name = item.name,
                category = item.category, unit = item.unit, brand = item.brand, color = item.color, size = item.size,
                location = item.location, minLimit = item.minLimit, maxLimit = item.maxLimit, purchasePrice = item.purchasePrice,
                salePrice = item.salePrice, wholesalePrice = item.wholesalePrice, specialPrice = item.specialPrice,
                currentQuantity = item.currentQuantity, notes = item.notes, imagePath = item.imagePath, expiryDate = item.expiryDate,
                syncState = "SYNCED", updatedAt = item.updatedAt, isDeleted = item.isDeleted
            )
            val res = api.upsertItem(dto, supabaseAnonKey, authHeader)
            if (res.isSuccessful) syncDao.markItemSynced(item.syncId)
        }

        // Item Units
        val itemUnits = syncDao.getPendingItemUnits()
        itemUnits.forEach { iu ->
            val parentItem = db.itemDao().getItemById(iu.itemId)
            if (parentItem != null) {
                val dto = ItemUnitDto(
                    syncId = iu.syncId, companyId = companyId, itemSyncId = parentItem.syncId, unitName = iu.unitName,
                    conversionFactor = iu.conversionFactor, purchasePrice = iu.purchasePrice, salePrice = iu.salePrice,
                    barcode = iu.barcode, syncState = "SYNCED", updatedAt = iu.updatedAt, isDeleted = iu.isDeleted
                )
                val res = api.upsertItemUnit(dto, supabaseAnonKey, authHeader)
                if (res.isSuccessful) syncDao.markItemUnitSynced(iu.syncId)
            }
        }

        // Invoices
        val invoices = syncDao.getPendingInvoices()
        invoices.forEach { inv ->
            val contact = inv.contactId?.let { db.contactDao().getContactById(it) }
            val user = inv.userId?.let { db.userDao().getUserById(it) }
            val dto = InvoiceDto(
                syncId = inv.syncId, companyId = companyId, invoiceNumber = inv.invoiceNumber, type = inv.type,
                contactSyncId = contact?.syncId, timestamp = inv.timestamp, subTotal = inv.subTotal, discount = inv.discount,
                tax = inv.tax, total = inv.total, paidAmount = inv.paidAmount, remainingAmount = inv.remainingAmount,
                paymentMethod = inv.paymentMethod, notes = inv.notes, userSyncId = user?.syncId, currencyCode = inv.currencyCode,
                exchangeRate = inv.exchangeRate, syncState = "SYNCED", updatedAt = inv.updatedAt, isDeleted = inv.isDeleted
            )
            val res = api.upsertInvoice(dto, supabaseAnonKey, authHeader)
            if (res.isSuccessful) syncDao.markInvoiceSynced(inv.syncId)
        }

        // Invoice Items
        val invoiceItems = syncDao.getPendingInvoiceItems()
        invoiceItems.forEach { ii ->
            val invoice = db.invoiceDao().getInvoiceById(ii.invoiceId)
            val item = db.itemDao().getItemById(ii.itemId)
            if (invoice != null && item != null) {
                val dto = InvoiceItemDto(
                    syncId = ii.syncId, companyId = companyId, invoiceSyncId = invoice.syncId, itemSyncId = item.syncId,
                    quantity = ii.quantity, unitPrice = ii.unitPrice, discount = ii.discount, total = ii.total,
                    unitName = ii.unitName, conversionFactor = ii.conversionFactor, syncState = "SYNCED",
                    updatedAt = ii.updatedAt, isDeleted = ii.isDeleted
                )
                val res = api.upsertInvoiceItem(dto, supabaseAnonKey, authHeader)
                if (res.isSuccessful) syncDao.markInvoiceItemSynced(ii.syncId)
            }
        }

        // Contacts
        val contacts = syncDao.getPendingContacts()
        contacts.forEach { c ->
            val dto = ContactDto(
                syncId = c.syncId, companyId = companyId, type = c.type, name = c.name, phone = c.phone,
                address = c.address, balance = c.balance, creditLimit = c.creditLimit, notes = c.notes,
                syncState = "SYNCED", updatedAt = c.updatedAt, isDeleted = c.isDeleted
            )
            val res = api.upsertContact(dto, supabaseAnonKey, authHeader)
            if (res.isSuccessful) syncDao.markContactSynced(c.syncId)
        }
        
        // Settings
        val settings = syncDao.getPendingEnterpriseSettings()
        settings.forEach { s ->
            val dto = EnterpriseSettingDto(
                syncId = "setting-1", companyId = companyId, name = s.name, activity = s.activity, address = s.address,
                phone = s.phone, whatsapp = s.whatsapp, email = s.email, website = s.website, taxId = s.taxId, crId = s.crId,
                currency = s.currency, decimalPlaces = s.decimalPlaces, invoiceFooter = s.invoiceFooter,
                allowSellBelowCost = s.allowSellBelowCost, allowNegativeStock = s.allowNegativeStock,
                syncState = "SYNCED", updatedAt = s.updatedAt, isDeleted = s.isDeleted
            )
            val res = api.upsertEnterpriseSetting(dto, supabaseAnonKey, authHeader)
            if (res.isSuccessful) syncDao.markEnterpriseSettingSynced()
        }
    }
        
    /**
     * Downloads all data for the company from Supabase and merges it into local Room DB.
     * Called when joining a company or syncing down.
     */
    suspend fun downloadAllCompanyData() = withContext(Dispatchers.IO) {
        if (companyId.isEmpty() || supabaseAnonKey.isEmpty()) return@withContext

        try {
            // 1. Users
            val userRes = api.getUsers("eq.$companyId", supabaseAnonKey, authHeader)
            if (userRes.isSuccessful) {
                userRes.body()?.forEach { u ->
                    val existing = db.userDao().getAllUsers().firstOrNull()?.find { it.username == u.username }
                    if (existing == null) {
                        db.userDao().insertUser(
                            User(
                                syncId = u.syncId, username = u.username, passwordHash = u.passwordHash,
                                isSuspended = u.isSuspended, lastLogin = u.lastLogin, operationCount = u.operationCount,
                                permSale = u.permSale, permPurchase = u.permPurchase, permDeleteInvoice = u.permDeleteInvoice,
                                permEditInvoice = u.permEditInvoice, permViewProfits = u.permViewProfits, permViewReports = u.permViewReports,
                                permEditPrices = u.permEditPrices, permBackup = u.permBackup, permSettings = u.permSettings,
                                permAI = u.permAI, permAccountStatement = u.permAccountStatement, permStocktake = u.permStocktake,
                                permPrint = u.permPrint, permShare = u.permShare, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.userDao().updateUser(
                            existing.copy(
                                syncId = u.syncId, passwordHash = u.passwordHash,
                                isSuspended = u.isSuspended, lastLogin = u.lastLogin, operationCount = u.operationCount,
                                permSale = u.permSale, permPurchase = u.permPurchase, permDeleteInvoice = u.permDeleteInvoice,
                                permEditInvoice = u.permEditInvoice, permViewProfits = u.permViewProfits, permViewReports = u.permViewReports,
                                permEditPrices = u.permEditPrices, permBackup = u.permBackup, permSettings = u.permSettings,
                                permAI = u.permAI, permAccountStatement = u.permAccountStatement, permStocktake = u.permStocktake,
                                permPrint = u.permPrint, permShare = u.permShare, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 2. Items
            val itemRes = api.getItems("eq.$companyId", supabaseAnonKey, authHeader)
            if (itemRes.isSuccessful) {
                itemRes.body()?.forEach { item ->
                    val existing = syncDao.getItemBySyncId(item.syncId)
                    if (existing == null) {
                        db.itemDao().insertItem(
                            Item(
                                syncId = item.syncId, code = item.code, barcode = item.barcode, name = item.name,
                                category = item.category, unit = item.unit, brand = item.brand, color = item.color,
                                size = item.size, location = item.location, minLimit = item.minLimit, maxLimit = item.maxLimit,
                                purchasePrice = item.purchasePrice, salePrice = item.salePrice, wholesalePrice = item.wholesalePrice,
                                specialPrice = item.specialPrice, currentQuantity = item.currentQuantity, notes = item.notes,
                                imagePath = item.imagePath, expiryDate = item.expiryDate, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.itemDao().updateItem(
                            existing.copy(
                                code = item.code, barcode = item.barcode, name = item.name,
                                category = item.category, unit = item.unit, brand = item.brand, color = item.color,
                                size = item.size, location = item.location, minLimit = item.minLimit, maxLimit = item.maxLimit,
                                purchasePrice = item.purchasePrice, salePrice = item.salePrice, wholesalePrice = item.wholesalePrice,
                                specialPrice = item.specialPrice, currentQuantity = item.currentQuantity, notes = item.notes,
                                imagePath = item.imagePath, expiryDate = item.expiryDate, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 3. Contacts
            val contactRes = api.getContacts("eq.$companyId", supabaseAnonKey, authHeader)
            if (contactRes.isSuccessful) {
                contactRes.body()?.forEach { c ->
                    val existing = syncDao.getContactBySyncId(c.syncId)
                    if (existing == null) {
                        db.contactDao().insertContact(
                            Contact(
                                syncId = c.syncId, type = c.type, name = c.name, phone = c.phone,
                                address = c.address, balance = c.balance, creditLimit = c.creditLimit,
                                notes = c.notes, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.contactDao().updateContact(
                            existing.copy(
                                type = c.type, name = c.name, phone = c.phone,
                                address = c.address, balance = c.balance, creditLimit = c.creditLimit,
                                notes = c.notes, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 4. Accounts
            val accRes = api.getAccounts("eq.$companyId", supabaseAnonKey, authHeader)
            if (accRes.isSuccessful) {
                accRes.body()?.forEach { a ->
                    val existing = syncDao.getAccountBySyncId(a.syncId)
                    if (existing == null) {
                        db.accountDao().insertAccount(
                            Account(
                                syncId = a.syncId, code = a.code, name = a.name, type = a.type,
                                balance = a.balance, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.accountDao().updateAccount(
                            existing.copy(
                                code = a.code, name = a.name, type = a.type,
                                balance = a.balance, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 5. Invoices
            val invRes = api.getInvoices("eq.$companyId", supabaseAnonKey, authHeader)
            if (invRes.isSuccessful) {
                invRes.body()?.forEach { i ->
                    val existing = syncDao.getInvoiceBySyncId(i.syncId)
                    if (existing == null) {
                        db.invoiceDao().insertInvoice(
                            Invoice(
                                syncId = i.syncId, invoiceNumber = i.invoiceNumber, type = i.type,
                                date = i.date, contactId = i.contactId, contactName = i.contactName,
                                contactType = i.contactType, contactPhone = i.contactPhone,
                                totalAmount = i.totalAmount, discount = i.discount, tax = i.tax,
                                netAmount = i.netAmount, paidAmount = i.paidAmount, remainingAmount = i.remainingAmount,
                                notes = i.notes, paymentMethod = i.paymentMethod, warehouseId = i.warehouseId,
                                isReturn = i.isReturn, isPosted = i.isPosted, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.invoiceDao().updateInvoice(
                            existing.copy(
                                invoiceNumber = i.invoiceNumber, type = i.type,
                                date = i.date, contactId = i.contactId, contactName = i.contactName,
                                contactType = i.contactType, contactPhone = i.contactPhone,
                                totalAmount = i.totalAmount, discount = i.discount, tax = i.tax,
                                netAmount = i.netAmount, paidAmount = i.paidAmount, remainingAmount = i.remainingAmount,
                                notes = i.notes, paymentMethod = i.paymentMethod, warehouseId = i.warehouseId,
                                isReturn = i.isReturn, isPosted = i.isPosted, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 6. Invoice Items
            val invItemRes = api.getInvoiceItems("eq.$companyId", supabaseAnonKey, authHeader)
            if (invItemRes.isSuccessful) {
                invItemRes.body()?.forEach { ii ->
                    val existing = syncDao.getInvoiceItemBySyncId(ii.syncId)
                    if (existing == null) {
                        db.invoiceDao().insertInvoiceItem(
                            InvoiceItem(
                                syncId = ii.syncId, invoiceId = ii.invoiceId, itemId = ii.itemId,
                                itemName = ii.itemName, barcode = ii.barcode, quantity = ii.quantity,
                                unitPrice = ii.unitPrice, total = ii.total, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.invoiceDao().insertInvoiceItem(
                            existing.copy(
                                invoiceId = ii.invoiceId, itemId = ii.itemId,
                                itemName = ii.itemName, barcode = ii.barcode, quantity = ii.quantity,
                                unitPrice = ii.unitPrice, total = ii.total, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 7. Cash Transactions
            val cashRes = api.getCashTransactions("eq.$companyId", supabaseAnonKey, authHeader)
            if (cashRes.isSuccessful) {
                cashRes.body()?.forEach { ct ->
                    val existing = syncDao.getCashTransactionBySyncId(ct.syncId)
                    if (existing == null) {
                        db.cashTransactionDao().insertCashTransaction(
                            CashTransaction(
                                syncId = ct.syncId, receiptNumber = ct.receiptNumber, type = ct.type,
                                date = ct.date, amount = ct.amount, contactId = ct.contactId,
                                contactName = ct.contactName, accountId = ct.accountId, accountName = ct.accountName,
                                notes = ct.notes, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.cashTransactionDao().updateCashTransaction(
                            existing.copy(
                                receiptNumber = ct.receiptNumber, type = ct.type,
                                date = ct.date, amount = ct.amount, contactId = ct.contactId,
                                contactName = ct.contactName, accountId = ct.accountId, accountName = ct.accountName,
                                notes = ct.notes, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 8. Journal Entries
            val jeRes = api.getJournalEntries("eq.$companyId", supabaseAnonKey, authHeader)
            if (jeRes.isSuccessful) {
                jeRes.body()?.forEach { je ->
                    val existing = syncDao.getJournalEntryBySyncId(je.syncId)
                    if (existing == null) {
                        db.journalDao().insertEntry(
                            JournalEntry(
                                syncId = je.syncId, entryNumber = je.entryNumber, date = je.date,
                                description = je.description, totalAmount = je.totalAmount, syncState = "SYNCED",
                                referenceId = je.referenceId, referenceType = je.referenceType, currencyCode = je.currencyCode
                            )
                        )
                    } else {
                        db.journalDao().updateEntry(
                            existing.copy(
                                entryNumber = je.entryNumber, date = je.date,
                                description = je.description, totalAmount = je.totalAmount, syncState = "SYNCED",
                                referenceId = je.referenceId, referenceType = je.referenceType, currencyCode = je.currencyCode
                            )
                        )
                    }
                }
            }

            // 9. Journal Entry Lines
            val jelRes = api.getJournalEntryLines("eq.$companyId", supabaseAnonKey, authHeader)
            if (jelRes.isSuccessful) {
                jelRes.body()?.forEach { jel ->
                    val existing = syncDao.getJournalEntryLineBySyncId(jel.syncId)
                    if (existing == null) {
                        db.journalDao().insertEntryLine(
                            JournalEntryLine(
                                syncId = jel.syncId, journalEntryId = jel.journalEntryId,
                                accountId = jel.accountId, accountName = jel.accountName,
                                debit = jel.debit, credit = jel.credit, description = jel.description, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.journalDao().insertEntryLine(
                            existing.copy(
                                journalEntryId = jel.journalEntryId,
                                accountId = jel.accountId, accountName = jel.accountName,
                                debit = jel.debit, credit = jel.credit, description = jel.description, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 10. Enterprise Settings
            val esRes = api.getEnterpriseSettings("eq.$companyId", supabaseAnonKey, authHeader)
            if (esRes.isSuccessful) {
                esRes.body()?.firstOrNull()?.let { es ->
                    val existing = db.enterpriseSettingDao().getSettingsDirect()
                    if (existing == null) {
                        db.enterpriseSettingDao().insertSettings(
                            EnterpriseSetting(
                                id = 1, syncId = es.syncId, name = es.name, activityType = es.activityType,
                                address = es.address, phone1 = es.phone1, phone2 = es.phone2,
                                taxNumber = es.taxNumber, commercialRecord = es.commercialRecord,
                                taxRate = es.taxRate, isTaxInclusive = es.isTaxInclusive,
                                isSalesInvoiceDirectPrint = es.isSalesInvoiceDirectPrint,
                                currency = es.currency, notes = es.notes, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.enterpriseSettingDao().insertSettings(
                            existing.copy(
                                syncId = es.syncId, name = es.name, activityType = es.activityType,
                                address = es.address, phone1 = es.phone1, phone2 = es.phone2,
                                taxNumber = es.taxNumber, commercialRecord = es.commercialRecord,
                                taxRate = es.taxRate, isTaxInclusive = es.isTaxInclusive,
                                isSalesInvoiceDirectPrint = es.isSalesInvoiceDirectPrint,
                                currency = es.currency, notes = es.notes, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            // 11. Item Units
            val iuRes = api.getItemUnits("eq.$companyId", supabaseAnonKey, authHeader)
            if (iuRes.isSuccessful) {
                iuRes.body()?.forEach { iu ->
                    val existing = syncDao.getItemUnitBySyncId(iu.syncId)
                    if (existing == null) {
                        db.itemUnitDao().insertItemUnit(
                            ItemUnit(
                                syncId = iu.syncId, itemId = iu.itemId, unitName = iu.unitName,
                                conversionFactor = iu.conversionFactor, barcode = iu.barcode,
                                purchasePrice = iu.purchasePrice, salePrice = iu.salePrice, syncState = "SYNCED"
                            )
                        )
                    } else {
                        db.itemUnitDao().updateItemUnit(
                            existing.copy(
                                itemId = iu.itemId, unitName = iu.unitName,
                                conversionFactor = iu.conversionFactor, barcode = iu.barcode,
                                purchasePrice = iu.purchasePrice, salePrice = iu.salePrice, syncState = "SYNCED"
                            )
                        )
                    }
                }
            }

            Log.d("SupabaseSync", "Download and merge of company data completed successfully!")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error downloading company data: ${e.message}")
        }
    }

    /**
     * Verifies employee login against Supabase company data, sets companyId, and pulls all data.
     */
    suspend fun verifyAndDownloadCompanyData(companyName: String, username: String, passHash: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val safeKey = supabaseAnonKey

        try {
            // 1. Check companies table
            val compRes = api.getCompanies(safeKey, authHeader)
            var targetCompanyId = ""

            if (compRes.isSuccessful) {
                val cleanCompName = companyName.trim()
                val matchedComp = compRes.body()?.find { 
                    it.name.trim().equals(cleanCompName, ignoreCase = true) || 
                    it.companyId.trim().equals(cleanCompName, ignoreCase = true)
                }
                if (matchedComp != null) {
                    targetCompanyId = matchedComp.companyId
                }
            }

            if (targetCompanyId.isEmpty()) {
                targetCompanyId = companyName.trim()
            }

            val cleanUser = username.trim()
            val cleanPass = passHash.trim()
            val shaPass = sha256(cleanPass)

            // 2. Fetch users for this target company
            val userRes = api.getUsers(companyId = "eq.$targetCompanyId", apiKey = safeKey, auth = authHeader)
            var remoteUsers = if (userRes.isSuccessful) userRes.body() ?: emptyList() else emptyList()

            var matchedUser = remoteUsers.find { 
                val userMatch = it.username.trim().equals(cleanUser, ignoreCase = true)
                val remotePass = it.passwordHash.trim()
                val passMatch = remotePass.isEmpty() ||
                        remotePass.equals(cleanPass, ignoreCase = true) || 
                        remotePass == shaPass || 
                        sha256(remotePass) == shaPass
                userMatch && passMatch
            }

            // Fallback: If not found by company_id, search Supabase by username directly across companies
            if (matchedUser == null) {
                val fallbackRes = api.getUsers(username = "eq.$cleanUser", apiKey = safeKey, auth = authHeader)
                if (fallbackRes.isSuccessful) {
                    val fallbackUsers = fallbackRes.body() ?: emptyList()
                    matchedUser = fallbackUsers.find { 
                        val remotePass = it.passwordHash.trim()
                        remotePass.isEmpty() ||
                        remotePass.equals(cleanPass, ignoreCase = true) || 
                        remotePass == shaPass || 
                        sha256(remotePass) == shaPass
                    }
                    if (matchedUser != null && matchedUser.companyId.isNotEmpty()) {
                        targetCompanyId = matchedUser.companyId
                    }
                }
            }

            if (matchedUser != null) {
                companyId = targetCompanyId
                val licenseManager = LicenseManager(context)
                licenseManager.setCompanySyncCode(companyId)

                // --- SUPABASE AUTH INTEGRATION (FOR RLS) ---
                try {
                    val cleanUsername = matchedUser.username.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    val cleanCompId = targetCompanyId.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val authEmail = "${cleanUsername}@${cleanCompId}.afac"
                    val authPass = "AfacAuth123!_${cleanUsername}"
                    
                    val authReq = AuthRequestDto(email = authEmail, password = authPass)
                    var authRes = api.signIn(authReq, safeKey)
                    if (!authRes.isSuccessful) {
                        authRes = api.signUp(authReq, safeKey)
                    }
                    
                    if (authRes.isSuccessful && authRes.body()?.accessToken != null) {
                        supabaseAccessToken = authRes.body()!!.accessToken!!
                        Log.d("SupabaseSync", "Successfully authenticated with Supabase Auth JWT!")
                    } else {
                        Log.e("SupabaseSync", "Failed Supabase Auth: ${authRes.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Auth Error: ${e.message}")
                }
                // -------------------------------------------

                // Also save or update user locally so Room login succeeds 100%
                val allUsers = db.userDao().getAllUsers().firstOrNull() ?: emptyList()
                val existing = allUsers.find { it.username.trim().equals(matchedUser.username.trim(), ignoreCase = true) }
                
                val userToSave = User(
                    id = existing?.id ?: 0,
                    syncId = matchedUser.syncId,
                    username = matchedUser.username,
                    passwordHash = matchedUser.passwordHash,
                    isSuspended = matchedUser.isSuspended,
                    permSale = matchedUser.permSale,
                    permPurchase = matchedUser.permPurchase,
                    permDeleteInvoice = matchedUser.permDeleteInvoice,
                    permEditInvoice = matchedUser.permEditInvoice,
                    permViewProfits = matchedUser.permViewProfits,
                    permViewReports = matchedUser.permViewReports,
                    permEditPrices = matchedUser.permEditPrices,
                    permBackup = matchedUser.permBackup,
                    permSettings = matchedUser.permSettings,
                    permAI = matchedUser.permAI,
                    permAccountStatement = matchedUser.permAccountStatement,
                    permStocktake = matchedUser.permStocktake,
                    permPrint = matchedUser.permPrint,
                    permShare = matchedUser.permShare,
                    syncState = "SYNCED"
                )

                if (existing != null) {
                    db.userDao().updateUser(userToSave)
                } else {
                    db.userDao().insertUser(userToSave)
                }

                downloadAllCompanyData()

                isSyncEnabled.value = true
                connectRealtime()
                startSyncWorker()

                return@withContext Pair(true, "تم الانضمام وتنزيل بيانات المؤسسة بنجاح!")
            } else {
                return@withContext Pair(false, "اسم المستخدم أو كلمة المرور غير صحيحة لهذه المؤسسة.")
            }
        } catch (e: Exception) {
            return@withContext Pair(false, "تعذر الاتصال بالسحابة: ${e.message}")
        }
    }

    suspend fun deleteItemCloud(syncId: String) = withContext(Dispatchers.IO) {
        try {
            api.deleteItem("eq.$syncId", supabaseAnonKey, authHeader)
            Log.d("SupabaseSync", "Successfully deleted item $syncId from cloud")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to delete item $syncId: ${e.message}")
        }
    }

    suspend fun deleteContactCloud(syncId: String) = withContext(Dispatchers.IO) {
        try {
            api.deleteContact("eq.$syncId", supabaseAnonKey, authHeader)
            Log.d("SupabaseSync", "Successfully deleted contact $syncId from cloud")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to delete contact $syncId: ${e.message}")
        }
    }

    suspend fun deleteUserCloud(syncId: String) = withContext(Dispatchers.IO) {
        try {
            api.deleteUser("eq.$syncId", supabaseAnonKey, authHeader)
            Log.d("SupabaseSync", "Successfully deleted user $syncId from cloud")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to delete user $syncId: ${e.message}")
        }
    }

    suspend fun deleteInvoiceCloud(syncId: String) = withContext(Dispatchers.IO) {
        try {
            api.deleteInvoice("eq.$syncId", supabaseAnonKey, authHeader)
            Log.d("SupabaseSync", "Successfully deleted invoice $syncId from cloud")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to delete invoice $syncId: ${e.message}")
        }
    }
}
