package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiHelper
import com.example.data.model.*
import com.example.data.repository.AppRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.*

data class StockSupplyEntry(
    val itemId: Long,
    val quantity: Double,
    val unitCostInCurrency: Double,
    val expiryDate: String = "" // YYYY-MM-DD
)

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    USER_MANAGEMENT,
    INVENTORY,
    CONTACTS,
    SALES,
    PURCHASES,
    SALES_RETURN,
    PURCHASES_RETURN,
    TREASURY,
    JOURNAL_ENTRIES,
    REPORTS,
    SETTINGS,
    AI_ASSISTANT,
    LICENSE,
    OPERATIONS,
    PARTNERS
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AppRepository(application)
    val licenseManager = com.example.util.LicenseManager(application)
    val firestoreSyncManager = com.example.util.FirestoreSyncManager(application)
    val supabaseSyncManager = com.example.util.SupabaseSyncManager(application)



    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val syncCode = licenseManager.getCompanySyncCode()
                val companyIdToUse = if (syncCode.isNotEmpty()) syncCode else "company-default"
                supabaseSyncManager.initialize(
                    url = if (com.example.BuildConfig.SUPABASE_URL.isNotEmpty()) com.example.BuildConfig.SUPABASE_URL else "https://szrvoorbetreklxnbujl.supabase.co",
                    key = if (com.example.BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()) com.example.BuildConfig.SUPABASE_ANON_KEY else "sb_publishable_JzvW3kKPY54uv-BB1VOYSg_oKjZRbSM",
                    companyId = companyIdToUse
                )

                scheduleDailyAutoBackup(isAutoBackupEnabled())

                if (firestoreSyncManager.isSyncEnabled.value) {
                    startFirestoreSyncListeners()
                }

                users.collect { userList ->
                    val current = _currentUser.value
                    if (current != null && !current.username.equals("admin", ignoreCase = true)) {
                        val updatedUser = userList.find { it.username.trim().equals(current.username.trim(), ignoreCase = true) }
                        if (updatedUser != null && updatedUser.isSuspended) {
                            withContext(Dispatchers.Main) {
                                logout()
                                loginError.value = "تم إيقاف حسابك حالياً من قبل مدير المنشأة 🔴"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startFirestoreSyncListeners() {
        try {
            firestoreSyncManager.startListening(
                onItemsChanged = { remoteItems ->
                    viewModelScope.launch(Dispatchers.IO) {
                        remoteItems.forEach { item ->
                            val existing = repository.itemDao.getItemById(item.id)
                            if (existing != null) {
                                repository.itemDao.updateItem(item)
                            } else {
                                repository.itemDao.insertItem(item)
                            }
                        }
                    }
                },
                onContactsChanged = { remoteContacts ->
                    viewModelScope.launch(Dispatchers.IO) {
                        remoteContacts.forEach { contact ->
                            val existing = repository.contactDao.getContactById(contact.id)
                            if (existing != null) {
                                repository.contactDao.updateContact(contact)
                            } else {
                                repository.contactDao.insertContact(contact)
                            }
                        }
                    }
                },
                onInvoicesChanged = {}
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // License & Operations Tracking Flows
    val invoicesCount = repository.invoiceDao.countInvoicesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val cashTxCount = repository.cashTransactionDao.countCashTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val journalEntriesCount = repository.journalDao.countJournalEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOperationsCount = combine(invoicesCount, cashTxCount, journalEntriesCount) { inv, cash, j ->
        inv + cash + j
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val licenseStatus = totalOperationsCount.map { opsCount ->
        licenseManager.evaluateStatus(opsCount)
    }.stateIn(viewModelScope, SharingStarted.Lazily, com.example.util.LicenseStatus.FreeTrial(100))




    // Current Navigation Screen
    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Pending Editing Operations State
    var pendingEditingInvoice by mutableStateOf<Invoice?>(null)
    var pendingEditingJournalEntry by mutableStateOf<JournalEntry?>(null)
    var pendingEditingCashTx by mutableStateOf<CashTransaction?>(null)

    // Pending dialog to auto-open when navigating to a screen
    // Values: "RECEIPT", "PAYMENT", "JOURNAL", "NEW_SALE", "NEW_PURCHASE", "NEW_SALE_RETURN", "NEW_PURCHASE_RETURN", "NEW_ITEM"
    var pendingDialogToOpen by mutableStateOf<String?>(null)

    fun navigateWithDialog(screen: AppScreen, dialogKey: String, onNavigate: (AppScreen) -> Unit) {
        pendingDialogToOpen = dialogKey
        onNavigate(screen)
    }

    fun startEditingInvoice(invoice: Invoice, onNavigate: (AppScreen) -> Unit) {
        pendingEditingInvoice = invoice
        val targetScreen = when (invoice.type) {
            "SALE_CASH", "SALE_CREDIT" -> AppScreen.SALES
            "PURCHASE_CASH", "PURCHASE_CREDIT" -> AppScreen.PURCHASES
            "SALE_RETURN" -> AppScreen.SALES_RETURN
            "PURCHASE_RETURN" -> AppScreen.PURCHASES_RETURN
            else -> AppScreen.SALES
        }
        onNavigate(targetScreen)
    }

    fun startEditingJournalEntry(entry: JournalEntry, onNavigate: (AppScreen) -> Unit) {
        pendingEditingJournalEntry = entry
        onNavigate(AppScreen.JOURNAL_ENTRIES)
    }

    fun startEditingCashTx(tx: CashTransaction, onNavigate: (AppScreen) -> Unit) {
        pendingEditingCashTx = tx
        onNavigate(AppScreen.TREASURY)
    }



    // Database flows converted to StateFlows for Compose
    val users = repository.userDao.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items = repository.itemDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemStocks = repository.itemStockDao.getAllItemStocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getStocksForItem(itemId: Long): Flow<List<ItemStock>> {
        return repository.itemStockDao.getStocksForItem(itemId)
    }

    val expiringSoonItems = items.map { list ->
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = java.util.Date()
        list.filter { item ->
            if (item.expiryDate.isBlank()) false
            else {
                try {
                    val expDate = sdf.parse(item.expiryDate.trim())
                    if (expDate != null) {
                        val diffMs = expDate.time - today.time
                        val diffDays = diffMs / (1000 * 60 * 60 * 24)
                        diffDays <= 45 // Alert for items expiring within 45 days or expired
                    } else false
                } catch (e: Exception) {
                    false
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemUnits = repository.itemUnitDao.getAllItemUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses = repository.warehouseDao.getAllWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts = repository.contactDao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices = repository.invoiceDao.getAllInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts = repository.accountDao.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalEntries = repository.journalDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashTransactions = repository.cashTransactionDao.getAllCashTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankTransactions = repository.bankTransactionDao.getAllBankTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs = repository.auditLogDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = repository.enterpriseSettingDao.getSettings()
        .map { it ?: EnterpriseSetting() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnterpriseSetting())

    val currencies = repository.currencyDao.getAllCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val partners = repository.partnerDao.getAllPartners()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val lowStockItems = repository.itemDao.getLowStockItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Active logged-in user state
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Dashboard Smart Metrics (Native Room Flows for ZERO Memory Leak!)
    val dashboardItemsCount = repository.itemDao.countItemsFlow().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val dashboardStockValue = repository.itemDao.getStockValueFlow().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val dashboardClientsCount = repository.contactDao.countContactsByTypeFlow("CUSTOMER").stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val dashboardSuppliersCount = repository.contactDao.countContactsByTypeFlow("SUPPLIER").stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val dashboardExpenses = repository.cashTransactionDao.sumCashTransactionsByTypeFlow("PAYMENT").stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // Daily Sales & Growth Metrics
    private val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private val todayStart = calendar.timeInMillis
    private val todayEnd = todayStart + 86400000L - 1L
    private val yesterdayStart = todayStart - 86400000L
    private val yesterdayEnd = todayStart - 1L

    private val todaySalesFlow = repository.invoiceDao.sumInvoicesByDateFlow(listOf("SALE_CASH", "SALE_CREDIT"), todayStart, todayEnd)
    private val todayReturnsFlow = repository.invoiceDao.sumInvoicesByDateFlow(listOf("SALE_RETURN"), todayStart, todayEnd)
    private val yesterdaySalesFlow = repository.invoiceDao.sumInvoicesByDateFlow(listOf("SALE_CASH", "SALE_CREDIT"), yesterdayStart, yesterdayEnd)
    private val yesterdayReturnsFlow = repository.invoiceDao.sumInvoicesByDateFlow(listOf("SALE_RETURN"), yesterdayStart, yesterdayEnd)

    val todayNetSales = combine(todaySalesFlow, todayReturnsFlow) { s, r -> 
        (s ?: 0.0) - (r ?: 0.0) 
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private val yesterdayNetSales = combine(yesterdaySalesFlow, yesterdayReturnsFlow) { s, r -> 
        (s ?: 0.0) - (r ?: 0.0) 
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val todaySalesGrowth = combine(todayNetSales, yesterdayNetSales) { today, yesterday ->
        if (yesterday == 0.0) {
            if (today > 0) 100.0 else 0.0
        } else {
            ((today - yesterday) / yesterday) * 100.0
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // Login screen states
    val loginError = MutableStateFlow<String?>(null)

    // Chart Data (Last 7 Days)
    private val sevenDaysAgo = todayStart - (6 * 86400000L)
    
    val weeklySalesData = repository.invoiceDao.getWeeklySalesFlow(sevenDaysAgo).map { list ->
        val dataMap = list.associate { it.dayDate to it.total.toFloat() }
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        (6 downTo 0).map { daysAgo ->
            val date = java.util.Date(todayStart - (daysAgo * 86400000L))
            val dateStr = dateFormat.format(date)
            dataMap[dateStr] ?: 0f
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, List(7) { 0f })

    val weeklyProfitsData = repository.journalDao.getWeeklyProfitsFlow(sevenDaysAgo).map { list ->
        val dataMap = list.associate { it.dayDate to it.total.toFloat() }
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        (6 downTo 0).map { daysAgo ->
            val date = java.util.Date(todayStart - (daysAgo * 86400000L))
            val dateStr = dateFormat.format(date)
            dataMap[dateStr] ?: 0f
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, List(7) { 0f })

    // AI Assistant state
    val aiChatHistory = MutableStateFlow<List<Pair<String, Boolean>>>(
        listOf(Pair("أهلاً بك في مساعد آفاق الذكي. أنا هنا لمساعدتك في تحليل بياناتك المالية والإجابة على أي استفسار محاسبي.", false))
    )
    val isAiLoading = MutableStateFlow(false)

    private val sharedPrefs = application.getSharedPreferences("afaq_settings", android.content.Context.MODE_PRIVATE)
    private val _geminiApiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _isFirstRunNeeded = MutableStateFlow(!sharedPrefs.getBoolean("is_first_setup_done", false))
    val isFirstRunNeeded: StateFlow<Boolean> = _isFirstRunNeeded.asStateFlow()

    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        sharedPrefs.edit().putBoolean("is_dark_mode", newMode).apply()
    }

    fun updateGeminiApiKey(key: String) {
        sharedPrefs.edit().putString("gemini_api_key", key).apply()
        _geminiApiKey.value = key
    }

    fun completeFirstRunSetup(
        storeName: String,
        adminUsername: String,
        adminPassword: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val cleanStore = storeName.trim().ifEmpty { "آفاق محاسب" }
            val cleanUser = adminUsername.trim().ifEmpty { "admin" }
            val cleanPass = adminPassword.trim()

            // Update enterprise setting name
            val currentSettings = settings.value
            repository.enterpriseSettingDao.insertSettings(currentSettings.copy(name = cleanStore))

            // Update or Create Admin user
            val allUsers = users.value
            var adminUser = allUsers.find { it.username.equals("admin", ignoreCase = true) }
            if (adminUser == null && allUsers.isNotEmpty()) {
                adminUser = allUsers.first()
            }

            val finalAdmin = if (adminUser != null) {
                val u = adminUser.copy(username = cleanUser, passwordHash = cleanPass, isSuspended = false)
                repository.userDao.updateUser(u)
                u
            } else {
                val newAdmin = User(
                    username = cleanUser,
                    passwordHash = cleanPass,
                    permSale = true, permPurchase = true, permDeleteInvoice = true,
                    permEditInvoice = true, permViewProfits = true, permViewReports = true,
                    permEditPrices = true, permBackup = true, permSettings = true, permAI = true,
                    permAccountStatement = true, permStocktake = true, permPrint = true, permShare = true
                )
                repository.userDao.insertUser(newAdmin)
                newAdmin
            }

            _currentUser.value = finalAdmin
            repository.currentUser = finalAdmin

            licenseManager.setCompanySyncCode(cleanStore)
            supabaseSyncManager.initialize(
                url = if (com.example.BuildConfig.SUPABASE_URL.isNotEmpty()) com.example.BuildConfig.SUPABASE_URL else "https://szrvoorbetreklxnbujl.supabase.co",
                key = if (com.example.BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()) com.example.BuildConfig.SUPABASE_ANON_KEY else "sb_publishable_JzvW3kKPY54uv-BB1VOYSg_oKjZRbSM",
                companyId = cleanStore
            )

            sharedPrefs.edit().putBoolean("is_first_setup_done", true).apply()
            _isFirstRunNeeded.value = false
            repository.logOperation("إعداد أولي", "system", "تم التثبيت وإعداد حساب المدير $cleanUser لـ $cleanStore")
            try {
                supabaseSyncManager.pushPendingChanges()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _currentScreen.value = AppScreen.DASHBOARD
            onComplete()
        }
    }


    init {
        viewModelScope.launch {
            repository.seedDatabase()

            // Auto restore session if Remember Me is active
            val isRememberMe = sharedPrefs.getBoolean("is_remember_me", false)
            val savedUserId = sharedPrefs.getLong("saved_user_id", -1L)
            if (isRememberMe && savedUserId != -1L) {
                val savedUser = repository.userDao.getUserById(savedUserId)
                if (savedUser != null && !savedUser.isSuspended) {
                    _currentUser.value = savedUser
                    repository.currentUser = savedUser
                    _currentScreen.value = AppScreen.DASHBOARD
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            licenseManager.checkServerLicense()
        }
    }

    fun syncLicenseCheck() = viewModelScope.launch(Dispatchers.IO) {
        licenseManager.checkServerLicense()
    }


    fun navigateTo(screen: AppScreen) {
        val user = _currentUser.value
        if (user == null && screen != AppScreen.LOGIN) {
            _currentScreen.value = AppScreen.LOGIN
            return
        }

        if (user != null) {
            val isAllowed = when (screen) {
                AppScreen.REPORTS -> user.permViewReports
                AppScreen.SETTINGS -> user.permSettings
                AppScreen.USER_MANAGEMENT -> user.username.equals("admin", ignoreCase = true) || (user.permSettings && user.permBackup)
                AppScreen.AI_ASSISTANT -> user.permAI
                AppScreen.SALES, AppScreen.SALES_RETURN -> user.permSale
                AppScreen.PURCHASES, AppScreen.PURCHASES_RETURN -> user.permPurchase
                else -> true
            }

            if (!isAllowed) {
                android.widget.Toast.makeText(getApplication<Application>(), "⚠️ عذراً، لا تملك الصلاحية للوصول إلى هذه الشاشة.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }
        _currentScreen.value = screen
    }



    // --- Authentication ---
    fun login(username: String, passwordInput: String, rememberMe: Boolean) {
        viewModelScope.launch {
            val cleanUser = username.trim()
            val cleanPass = passwordInput.trim()
            val shaPass = com.example.util.sha256(cleanPass)

            val allUsers = repository.userDao.getAllUsers().firstOrNull() ?: emptyList()
            val user = allUsers.find { 
                val userMatch = it.username.trim().equals(cleanUser, ignoreCase = true)
                val storedPass = it.passwordHash.trim()
                val passMatch = storedPass.isEmpty() ||
                        storedPass.equals(cleanPass, ignoreCase = true) ||
                        storedPass == shaPass ||
                        com.example.util.sha256(storedPass) == shaPass
                userMatch && passMatch
            }

            if (user != null) {
                if (user.isSuspended) {
                    loginError.value = "هذا الحساب موقوف حالياً من قبل مدير المنشأة 🔴"
                } else {
                    _currentUser.value = user
                    repository.currentUser = user

                    if (rememberMe) {
                        sharedPrefs.edit()
                            .putLong("saved_user_id", user.id)
                            .putBoolean("is_remember_me", true)
                            .apply()
                    } else {
                        sharedPrefs.edit()
                            .remove("saved_user_id")
                            .putBoolean("is_remember_me", false)
                            .apply()
                    }

                    // Update user lastLogin & operation count
                    repository.userDao.updateUser(user.copy(lastLogin = System.currentTimeMillis()))
                    repository.logOperation("دخول", "users", "تسجيل دخول المستخدم ${user.username}")
                    loginError.value = null
                    _currentScreen.value = AppScreen.DASHBOARD
                }
            } else {
                loginError.value = "اسم المستخدم أو كلمة المرور غير صحيحة."
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _currentUser.value?.let {
                repository.logOperation("خروج", "users", "تسجيل خروج المستخدم ${it.username}")
            }
            sharedPrefs.edit()
                .remove("saved_user_id")
                .putBoolean("is_remember_me", false)
                .apply()

            _currentUser.value = null
            repository.currentUser = null
            _currentScreen.value = AppScreen.LOGIN
        }
    }

    // --- Deletion Operations ---
    fun deleteInvoice(invoice: Invoice, onComplete: () -> Unit = {}) = viewModelScope.launch {
        repository.deleteInvoice(invoice)
        try {
            supabaseSyncManager.deleteInvoiceCloud(invoice.syncId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onComplete()
    }

    fun updateInvoice(oldInvoice: Invoice, newInvoice: Invoice, newItemsList: List<InvoiceItem>, accountId: Long? = null, onComplete: () -> Unit = {}) = viewModelScope.launch {
        val opsCount = totalOperationsCount.value
        val status = licenseManager.evaluateStatus(opsCount)
        if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
            _currentScreen.value = AppScreen.LICENSE
            return@launch
        }
        repository.updateInvoice(oldInvoice, newInvoice, newItemsList, accountId)
        onComplete()
    }

    fun deleteJournalEntry(entry: JournalEntry, onComplete: () -> Unit = {}) = viewModelScope.launch {
        repository.deleteJournalEntry(entry)
        onComplete()
    }

    fun updateJournalEntry(entry: JournalEntry, lines: List<JournalEntryLine>, onComplete: () -> Unit = {}) = viewModelScope.launch {
        repository.updateJournalEntry(entry, lines)
        onComplete()
    }

    fun updateCashTransaction(tx: CashTransaction) = viewModelScope.launch {
        repository.updateCashTransaction(tx)
    }

    fun deleteCashTransaction(tx: CashTransaction) = viewModelScope.launch {
        repository.deleteCashTransaction(tx)
    }



    fun deleteAuditLog(log: AuditLog) = viewModelScope.launch {
        repository.deleteAuditLog(log)
    }

    fun transferStock(from: Long, to: Long, itemId: Long, qty: Double, notes: String) = viewModelScope.launch {
        repository.createStockTransfer(from, to, itemId, qty, notes)
    }

    fun supplyStockMulti(entries: List<StockSupplyEntry>, warehouseId: Long, currencyCode: String, exchangeRate: Double, generalNotes: String) = viewModelScope.launch {
        repository.supplyStockMulti(entries, warehouseId, currencyCode, exchangeRate, generalNotes)
    }

    fun issueStockMulti(entries: List<StockSupplyEntry>, warehouseId: Long, currencyCode: String, exchangeRate: Double, generalNotes: String) = viewModelScope.launch {
        repository.issueStockMulti(entries, warehouseId, currencyCode, exchangeRate, generalNotes)
    }

    fun clearAllAuditLogs() = viewModelScope.launch {
        repository.clearAllAuditLogs()
    }



    // --- User Management ---
    fun addUser(user: User) = viewModelScope.launch {
        val pendingUser = user.copy(syncState = "PENDING_ADD", updatedAt = System.currentTimeMillis())
        repository.userDao.insertUser(pendingUser)
        repository.logOperation("إضافة مستخدم", "users", "تمت إضافة المستخدم ${user.username}")
        try {
            supabaseSyncManager.pushPendingChanges()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateUser(user: User) = viewModelScope.launch {
        val pendingUser = user.copy(syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis())
        repository.userDao.updateUser(pendingUser)
        if (_currentUser.value?.id == user.id) {
            _currentUser.value = pendingUser
        }
        repository.logOperation("تعديل مستخدم", "users", "تم تعديل بيانات المستخدم ${user.username}")
        try {
            supabaseSyncManager.pushPendingChanges()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteUser(user: User) = viewModelScope.launch {
        repository.userDao.deleteUser(user)
        repository.logOperation("حذف مستخدم", "users", "تم حذف المستخدم ${user.username}")
        try {
            supabaseSyncManager.deleteUserCloud(user.syncId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Change current logged-in user credentials
    fun changeMyAccountCredentials(
        oldPass: String,
        newUsername: String,
        newPass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user == null) {
                onResult(false, "لا يوجد مستخدم مسجل دخول حالياً.")
                return@launch
            }

            if (user.passwordHash.isNotEmpty() && user.passwordHash != oldPass) {
                onResult(false, "كلمة المرور الحالية غير صحيحة.")
                return@launch
            }

            val cleanUsername = newUsername.trim()
            if (cleanUsername.isEmpty()) {
                onResult(false, "يرجى كتابة اسم مستخدم صحيح.")
                return@launch
            }

            val finalPass = if (newPass.trim().isNotEmpty()) newPass.trim() else user.passwordHash
            val updatedUser = user.copy(username = cleanUsername, passwordHash = finalPass)
            
            repository.userDao.updateUser(updatedUser)
            _currentUser.value = updatedUser
            repository.logOperation("تحديث حساب شخصي", "users", "تم تغيير اسم المستخدم/كلمة المرور للحساب ${cleanUsername}")
            onResult(true, "تم تحديث بيانات الحساب وكلمة المرور بنجاح ✅")
        }
    }

    // Master Support Reset for Admin Password
    fun resetAdminPasswordWithMasterCode(
        masterCode: String,
        newAdminPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val deviceId = licenseManager.getDeviceId()
            val dynamicKey = if (deviceId.length >= 4) deviceId.takeLast(4) else "2026"
            val validCodes = listOf("990088", "770460", "736802", "AFAQ-$dynamicKey", "afaq$dynamicKey")
            
            val cleanCode = masterCode.trim()
            if (!validCodes.contains(cleanCode) && !validCodes.contains(cleanCode.uppercase())) {
                onResult(false, "رمز الدعم الفني غير صحيح. يرجى الحصول عليه من شركة آفاق الذكاء (770460003).")
                return@launch
            }

            if (newAdminPassword.trim().isEmpty()) {
                onResult(false, "يرجى إدخال كلمة مرور جديدة للمدير.")
                return@launch
            }

            val allUsers = users.value

            var adminUser = allUsers.find { it.username.equals("admin", ignoreCase = true) }
            if (adminUser == null && allUsers.isNotEmpty()) {
                adminUser = allUsers.first()
            }

            if (adminUser != null) {
                val updatedAdmin = adminUser.copy(passwordHash = newAdminPassword.trim(), isSuspended = false)
                repository.userDao.updateUser(updatedAdmin)
                repository.logOperation("تصفير ماستر للدعم الفني", "users", "تم تصفير كلمة مرور المدير بواسطة الدعم الفني")
                onResult(true, "تمت إعادة تعيين كلمة مرور المدير بنجاح ✅. يمكنك الدخول بها الآن.")
            } else {
                // If database has no users, create default admin
                val newAdmin = User(username = "admin", passwordHash = newAdminPassword.trim())
                repository.userDao.insertUser(newAdmin)
                onResult(true, "تم إنشاء وتأمين حساب المدير بنجاح ✅")
            }
        }
    }


    // --- Inventory Management ---
    fun addItem(item: Item, units: List<ItemUnit> = emptyList()) = viewModelScope.launch {
        val itemId = repository.itemDao.insertItem(item)
        val createdItem = item.copy(id = itemId)
        for (unit in units) {
            repository.itemUnitDao.insertItemUnit(unit.copy(itemId = itemId))
        }
        repository.logOperation("إضافة صنف", "items", "تمت إضافة الصنف ${item.name} بكود ${item.code} مع ${units.size} وحدات فرعية")
        firestoreSyncManager.pushItem(createdItem)
    }

    fun updateItem(item: Item) = viewModelScope.launch {
        repository.itemDao.updateItem(item)
        repository.logOperation("تعديل صنف", "items", "تم تعديل الصنف ${item.name}")
        firestoreSyncManager.pushItem(item)
    }

    fun deleteItem(item: Item) = viewModelScope.launch {
        repository.itemDao.deleteItem(item)
        repository.logOperation("حذف صنف", "items", "تم حذف الصنف ${item.name}")
        try {
            supabaseSyncManager.deleteItemCloud(item.syncId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Warehouse Management ---
    fun addWarehouse(w: Warehouse) = viewModelScope.launch {
        repository.warehouseDao.insertWarehouse(w)
        repository.logOperation("إضافة مستودع", "warehouses", "تمت إضافة المستودع ${w.name}")
    }

    fun updateWarehouse(w: Warehouse) = viewModelScope.launch {
        repository.warehouseDao.updateWarehouse(w)
        repository.logOperation("تعديل مستودع", "warehouses", "تم تعديل المستودع ${w.name}")
    }

    fun deleteWarehouse(w: Warehouse) = viewModelScope.launch {
        repository.warehouseDao.deleteWarehouse(w)
        repository.logOperation("حذف مستودع", "warehouses", "تم حذف المستودع ${w.name}")
    }


    // --- Contact Management ---
    fun addContact(c: Contact) = viewModelScope.launch {
        val contactId = repository.contactDao.insertContact(c)
        val createdContact = c.copy(id = contactId)
        repository.logOperation("إضافة جهة اتصال", "contacts", "تمت إضافة ${if (c.type == "CUSTOMER") "العميل" else "المورد"}: ${c.name}")
        firestoreSyncManager.pushContact(createdContact)
    }

    fun updateContact(c: Contact) = viewModelScope.launch {
        repository.contactDao.updateContact(c)
        repository.logOperation("تعديل جهة اتصال", "contacts", "تم تعديل جهة اتصال: ${c.name}")
        firestoreSyncManager.pushContact(c)
    }

    fun deleteContact(c: Contact) = viewModelScope.launch {
        repository.contactDao.deleteContact(c)
        repository.logOperation("حذف جهة اتصال", "contacts", "تم حذف جهة اتصال: ${c.name}")
        try {
            supabaseSyncManager.deleteContactCloud(c.syncId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Daily Auto Backup Operations ---
    fun setAutoBackupEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_daily_auto_backup_enabled", enabled).apply()
        scheduleDailyAutoBackup(enabled)
    }

    fun isAutoBackupEnabled(): Boolean {
        return sharedPrefs.getBoolean("is_daily_auto_backup_enabled", true)
    }

    fun scheduleDailyAutoBackup(enabled: Boolean) {
        val workManager = androidx.work.WorkManager.getInstance(getApplication())
        val workTag = "daily_auto_backup_work"
        if (enabled) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val backupWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.worker.DailyBackupWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            )
            .setConstraints(constraints)
            .addTag(workTag)
            .build()

            workManager.enqueueUniquePeriodicWork(
                workTag,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                backupWorkRequest
            )
        } else {
            workManager.cancelUniqueWork(workTag)
        }
    }

    fun triggerImmediateAutoBackup() = viewModelScope.launch {
        val workManager = androidx.work.WorkManager.getInstance(getApplication())
        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.util.worker.DailyBackupWorker>().build()
        workManager.enqueue(oneTimeRequest)
    }

    // --- Excel / CSV Import & Export Operations ---
    fun exportItemsToCsv(context: android.content.Context) {
        val uri = com.example.util.ExcelImportExportUtil.exportItemsToCsv(context, items.value)
        com.example.util.ExcelImportExportUtil.shareFile(context, uri, "تصدير أصناف آفاق (CSV/Excel)")
        viewModelScope.launch {
            repository.logOperation("تصدير أصناف", "items", "تم تصدير ${items.value.size} صنف إلى CSV")
        }
    }

    fun generateItemsTemplate(context: android.content.Context) {
        val uri = com.example.util.ExcelImportExportUtil.generateItemsTemplateUri(context)
        com.example.util.ExcelImportExportUtil.shareFile(context, uri, "نموذج استيراد الأصناف (CSV/Excel)")
    }

    fun importItemsFromCsv(uri: android.net.Uri, context: android.content.Context, onResult: (Int, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsedItems = com.example.util.ExcelImportExportUtil.parseItemsFromCsv(context, uri)
                if (parsedItems.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(0, "الملف فارغ أو لا يحتوي على بيانات أصناف صالحة.")
                    }
                    return@launch
                }
                var count = 0
                val existingItems = items.value
                parsedItems.forEach { newItem ->
                    val existing = existingItems.find { (newItem.code.isNotEmpty() && it.code == newItem.code) || (newItem.barcode.isNotEmpty() && it.barcode == newItem.barcode) }
                    if (existing != null) {
                        repository.itemDao.updateItem(newItem.copy(id = existing.id))
                    } else {
                        repository.itemDao.insertItem(newItem)
                    }
                    count++
                }
                repository.logOperation("استيراد أصناف", "items", "تم استيراد/تحديث $count صنف من CSV")
                withContext(Dispatchers.Main) {
                    onResult(count, "تم استيراد وتحديث $count صنف بنجاح ✅")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(0, "حدث خطأ أثناء الاستيراد: ${e.message}")
                }
            }
        }
    }

    fun exportContactsToCsv(context: android.content.Context, typeFilter: String? = null) {
        val uri = com.example.util.ExcelImportExportUtil.exportContactsToCsv(context, contacts.value, typeFilter)
        val title = when (typeFilter) {
            "CUSTOMER" -> "تصدير بيانات العملاء (CSV/Excel)"
            "SUPPLIER" -> "تصدير بيانات الموردين (CSV/Excel)"
            else -> "تصدير جهات الاتصال (CSV/Excel)"
        }
        com.example.util.ExcelImportExportUtil.shareFile(context, uri, title)
        viewModelScope.launch {
            repository.logOperation("تصدير جهات اتصال", "contacts", "تم تصدير جهات الاتصال إلى CSV")
        }
    }

    fun generateContactsTemplate(context: android.content.Context, defaultType: String = "CUSTOMER") {
        val uri = com.example.util.ExcelImportExportUtil.generateContactsTemplateUri(context, defaultType)
        com.example.util.ExcelImportExportUtil.shareFile(context, uri, "نموذج استيراد جهات الاتصال (CSV/Excel)")
    }

    fun importContactsFromCsv(uri: android.net.Uri, context: android.content.Context, fallbackType: String = "CUSTOMER", onResult: (Int, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsedContacts = com.example.util.ExcelImportExportUtil.parseContactsFromCsv(context, uri, fallbackType)
                if (parsedContacts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(0, "الملف فارغ أو لا يحتوي على أسماء جهات اتصال صالحة.")
                    }
                    return@launch
                }
                var count = 0
                val existingContacts = contacts.value
                parsedContacts.forEach { newContact ->
                    val existing = existingContacts.find { it.name.trim().equals(newContact.name.trim(), ignoreCase = true) && it.type == newContact.type }
                    if (existing != null) {
                        repository.contactDao.updateContact(newContact.copy(id = existing.id))
                    } else {
                        repository.contactDao.insertContact(newContact)
                    }
                    count++
                }
                repository.logOperation("استيراد جهات اتصال", "contacts", "تم استيراد/تحديث $count جهة اتصال من CSV")
                withContext(Dispatchers.Main) {
                    onResult(count, "تم استيراد وتحديث $count جهة اتصال بنجاح ✅")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(0, "حدث خطأ أثناء الاستيراد: ${e.message}")
                }
            }
        }
    }

    // --- Invoice Creation ---
    fun createInvoice(invoice: Invoice, itemsList: List<InvoiceItem>, accountId: Long? = null) {
        viewModelScope.launch {
            val opsCount = totalOperationsCount.value
            val status = licenseManager.evaluateStatus(opsCount)
            if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
                _currentScreen.value = AppScreen.LICENSE
                return@launch
            }
            repository.createInvoice(invoice, itemsList, accountId)
        }
    }

    // --- Remittance & Exchange ---
    fun createRemittance(rem: Remittance, onSuccess: (Long) -> Unit = {}) = viewModelScope.launch {
        val opsCount = totalOperationsCount.value
        val status = licenseManager.evaluateStatus(opsCount)
        if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
            _currentScreen.value = AppScreen.LICENSE
            return@launch
        }
        val id = repository.createRemittance(rem)
        onSuccess(id)
    }

    fun createCurrencyExchange(exchange: CurrencyExchange) = viewModelScope.launch {
        val opsCount = totalOperationsCount.value
        val status = licenseManager.evaluateStatus(opsCount)
        if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
            _currentScreen.value = AppScreen.LICENSE
            return@launch
        }
        repository.createCurrencyExchange(exchange)
    }



    // --- Item Units Operations ---
    fun addItemUnit(unit: ItemUnit) = viewModelScope.launch {
        repository.itemUnitDao.insertItemUnit(unit)
        repository.logOperation("إضافة وحدة صنف", "item_units", "تمت إضافة وحدة ${unit.unitName} للصنف ID ${unit.itemId}")
    }

    fun updateItemUnit(unit: ItemUnit) = viewModelScope.launch {
        repository.itemUnitDao.updateItemUnit(unit)
        repository.logOperation("تعديل وحدة صنف", "item_units", "تم تعديل وحدة ${unit.unitName} للصنف ID ${unit.itemId}")
    }

    fun deleteItemUnit(unit: ItemUnit) = viewModelScope.launch {
        repository.itemUnitDao.deleteItemUnit(unit)
        repository.logOperation("حذف وحدة صنف", "item_units", "تم حذف وحدة ${unit.unitName} للصنف ID ${unit.itemId}")
    }

    // --- Treasury Transactions ---
    fun createCashTransaction(tx: CashTransaction) {
        viewModelScope.launch {
            val opsCount = totalOperationsCount.value
            val status = licenseManager.evaluateStatus(opsCount)
            if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
                _currentScreen.value = AppScreen.LICENSE
                return@launch
            }
            repository.createCashTransaction(tx)
        }
    }

    // --- Manual Journal Entry Operations ---
    fun createManualJournalEntry(entry: JournalEntry, lines: List<JournalEntryLine>) = viewModelScope.launch {
        val opsCount = totalOperationsCount.value
        val status = licenseManager.evaluateStatus(opsCount)
        if (status is com.example.util.LicenseStatus.LimitReached || status is com.example.util.LicenseStatus.Expired) {
            _currentScreen.value = AppScreen.LICENSE
            return@launch
        }
        repository.createManualJournalEntry(entry, lines)
    }


    // --- Financial Account Operations ---
    fun addAccount(account: Account) = viewModelScope.launch {
        repository.accountDao.insertAccount(account)
        repository.logOperation("إضافة حساب", "accounts", "تمت إضافة حساب ${account.name} بكود ${account.code}")
    }

    fun updateAccount(account: Account) = viewModelScope.launch {
        repository.accountDao.updateAccount(account)
        repository.logOperation("تعديل حساب", "accounts", "تم تعديل الحساب ${account.name}")
    }

    fun deleteAccount(account: Account) = viewModelScope.launch {
        repository.accountDao.deleteAccount(account)
        repository.logOperation("حذف حساب", "accounts", "تم حذف الحساب ${account.name}")
    }

    // --- Enterprise Settings ---
    fun updateSettings(s: EnterpriseSetting) = viewModelScope.launch {
        repository.enterpriseSettingDao.insertSettings(s)
        repository.logOperation("تعديل الإعدادات", "enterprise_settings", "تم تعديل إعدادات المؤسسة")
    }

    // --- Currency Management ---
    fun addCurrency(c: com.example.data.model.Currency) = viewModelScope.launch {
        repository.currencyDao.insertCurrency(c)
        repository.logOperation("إضافة عملة", "currencies", "تمت إضافة عملة ${c.name} (${c.code})")
    }

    fun updateCurrency(c: com.example.data.model.Currency) = viewModelScope.launch {
        repository.currencyDao.updateCurrency(c)
        repository.logOperation("تعديل عملة", "currencies", "تم تعديل عملة ${c.name} (${c.code})")
    }

    fun deleteCurrency(c: com.example.data.model.Currency) = viewModelScope.launch {
        if (!c.isDefault) {
            repository.currencyDao.deleteCurrency(c)
            repository.logOperation("حذف عملة", "currencies", "تم حذف عملة ${c.name} (${c.code})")
        }
    }

    fun setDefaultCurrency(selectedCurrency: com.example.data.model.Currency) = viewModelScope.launch {
        val currentList = currencies.value
        for (curr in currentList) {
            if (curr.id == selectedCurrency.id) {
                repository.currencyDao.updateCurrency(curr.copy(isDefault = true))
            } else if (curr.isDefault) {
                repository.currencyDao.updateCurrency(curr.copy(isDefault = false))
            }
        }
        val currentSetting = settings.value
        val currSymbol = if (selectedCurrency.symbol.isNotEmpty()) selectedCurrency.symbol else selectedCurrency.code
        repository.enterpriseSettingDao.insertSettings(currentSetting.copy(currency = currSymbol))
        repository.logOperation("تعيين العملة الرئيسية", "currencies", "تم تعيين ${selectedCurrency.name} كعملة رئيسية للمنشأة")
    }



    // --- Audit Logs ---
    fun clearLogs() = viewModelScope.launch {
        repository.auditLogDao.clearLogs()
        repository.logOperation("تصفير السجل", "audit_logs", "تم مسح سجل العمليات بالكامل")
    }

    // --- Smart AI Assistant logic (Dynamically injected local database stats!) ---
    fun sendAiMessage(messageText: String) {
        val currentHistory = aiChatHistory.value.toMutableList()
        currentHistory.add(Pair(messageText, true))
        aiChatHistory.value = currentHistory
        isAiLoading.value = true

        viewModelScope.launch {
            // Build dynamic data context
            val rawSales = repository.invoiceDao.sumInvoicesByTypes(listOf("SALE_CASH", "SALE_CREDIT")) ?: 0.0
            val rawSalesReturn = repository.invoiceDao.sumInvoicesByTypes(listOf("SALE_RETURN")) ?: 0.0
            val totalSales = rawSales - rawSalesReturn

            val rawPurchases = repository.invoiceDao.sumInvoicesByTypes(listOf("PURCHASE_CASH", "PURCHASE_CREDIT")) ?: 0.0
            val rawPurchasesReturn = repository.invoiceDao.sumInvoicesByTypes(listOf("PURCHASE_RETURN")) ?: 0.0
            val totalPurchases = rawPurchases - rawPurchasesReturn

            val totalExpenses = repository.cashTransactionDao.sumCashTransactionsByType("PAYMENT") ?: 0.0
            val totalRevenues = repository.cashTransactionDao.sumCashTransactionsByType("RECEIPT") ?: 0.0
            
            val clientsCount = repository.contactDao.countContactsByType("CUSTOMER")
            val suppliersCount = repository.contactDao.countContactsByType("SUPPLIER")
            
            val itemCount = repository.itemDao.countItems()
            val stockValue = repository.itemDao.getStockValue() ?: 0.0
            val lowStockCount = repository.itemDao.countLowStockItems()
            
            val boxBalance = repository.accountDao.getAccountByCode("1101")?.balance ?: 0.0
            val bankBalance = repository.accountDao.getAccountByCode("1102")?.balance ?: 0.0

            val topSalesItem = repository.itemDao.getTopSalesItemName() ?: "لا يوجد"
            val bestCustomer = repository.contactDao.getBestCustomerName() ?: "لا يوجد"

            val systemContextPrompt = """
                أنت مساعد الذكاء الاصطناعي "آفاق" المدمج في نظام "آفاق محاسب" المحاسبي للأندرويد.
                المستخدم يسألك باللغة العربية وتجيبه بلغة عربية محاسبية واضحة ومبسطة.
                تنبيه هام: يُمنع استخدام الإيموجي أو الرموز والنجمات مثل (**) أو (*)، واكتب الإجابة بنص عربي شفاف ونظيف مقسم بأسطر عادية وبنقاط (-) فقط.
                
                إليك البيانات المالية والمحاسبية الحالية المستخرجة مباشرة من قاعدة بيانات النظام المحلية (أوفلاين) لمساعدة العميل في اتخاذ القرارات:
                
                - إجمالي المبيعات الحالية: $totalSales
                - إجمالي المشتريات الحالية: $totalPurchases
                - إجمالي المصروفات الحالية: $totalExpenses
                - إجمالي الإيرادات الإضافية: $totalRevenues
                - صافي الأرباح التقريبية: ${totalSales - totalPurchases - totalExpenses}
                - رصيد الصندوق: $boxBalance
                - رصيد البنك: $bankBalance
                - عدد العملاء: $clientsCount
                - أفضل عميل: $bestCustomer
                - عدد الموردين: $suppliersCount
                - عدد الأصناف بالمخزن: $itemCount
                - القيمة الإجمالية للمخزون الحالي: $stockValue
                - عدد الأصناف القليلة بالمخزن: $lowStockCount
                - الصنف الأوفر في المخزن: $topSalesItem
                
                سؤال المستخدم الحالي هو: "$messageText"
            """.trimIndent()

            val aiResponse = GeminiHelper.generateContent(systemContextPrompt)
            
            val finalResponse = if (aiResponse.contains("حدث خطأ") || aiResponse.contains("فشل الاتصال") || aiResponse.contains("عذراً")) {
                val netProfit = totalSales - totalPurchases - totalExpenses
                when {
                    messageText.contains("هلا") || messageText.contains("مرحبا") || messageText.contains("سلام") -> {
                        "مرحباً بك! أنا مساعد آفاق الذكي. (الاتصال السحابي بالذكاء الاصطناعي غير متوفر حالياً)، لكن يمكنني تزويدك بتقارير سريعة من حساباتك المحفوظة. تفضل؟"
                    }
                    messageText.contains("ملخص") || messageText.contains("نشاط") || messageText.contains("اليومي") -> {
                        "ملخص النشاط المحاسبي والمالي الحالي (مساعد آفاق الأوفلاين):\n\n" +
                        "- إجمالي المبيعات: $totalSales ر.ي\n" +
                        "- إجمالي المشتريات: $totalPurchases ر.ي\n" +
                        "- المصروفات التشغيلية: $totalExpenses ر.ي\n" +
                        "- صافي الأرباح المقدرة: $netProfit ر.ي\n" +
                        "- رصيد الخزينة والصندوق: $boxBalance ر.ي\n" +
                        "- رصيد البنك: $bankBalance ر.ي\n" +
                        "- قيمة المخزون الحالي: $stockValue ر.ي ($itemCount صنف)"
                    }
                    messageText.contains("شراء") || messageText.contains("اصناف") || messageText.contains("أصناف") || messageText.contains("نواقص") || messageText.contains("مخزون") -> {
                        "تقرير الجرد والنواقص بالمخزن:\n\n" +
                        "- إجمالي قيمة البضاعة: $stockValue ر.ي\n" +
                        "- الأصناف القليلة (تحت حد الأمان): $lowStockCount صنف\n" +
                        "- عدد كل الأصناف: $itemCount صنف\n\n" +
                        if (lowStockCount > 0) "تنبيه: ينصح بطلب وتوريد الأصناف القليلة لتجنب نفاد المخزون." else "جميع الأصناف بالمخزن متوفرة وتتجاوز الحد الأدنى."
                    }
                    messageText.contains("ربح") || messageText.contains("ارباح") || messageText.contains("أرباح") || messageText.contains("خسار") || messageText.contains("فائدة") -> {
                        "تقرير صافي الأرباح والسيولة:\n\n" +
                        "- إجمالي الإيرادات والمبيعات: $totalSales ر.ي\n" +
                        "- التكلفة والمصروفات: ${totalPurchases + totalExpenses} ر.ي\n" +
                        "- صافي الأرباح: $netProfit ر.ي\n" +
                        "- إجمالي السيولة المتاحة: ${boxBalance + bankBalance} ر.ي"
                    }
                    messageText.contains("عميل") || messageText.contains("أفضل") || messageText.contains("زبون") || messageText.contains("مورد") -> {
                        "بيانات العملاء والموردين:\n\n" +
                        "- عدد العملاء المسجلين: $clientsCount عميل\n" +
                        "- أفضل عميل تعاملاً: $bestCustomer\n" +
                        "- عدد الموردين المسجلين: $suppliersCount مورد"
                    }
                    else -> {
                        "عذراً، (الذكاء الاصطناعي السحابي غير متصل لتقديم إجابة مفصلة)، ولكني هنا لمساعدتك محلياً. إليك التحليل المالي السريع:\n\n" +
                        "- المبيعات: $totalSales ر.ي | المشتريات: $totalPurchases ر.ي\n" +
                        "- صافي الأرباح: $netProfit ر.ي\n" +
                        "- السيولة (صندوق + بنك): ${boxBalance + bankBalance} ر.ي\n" +
                        "- قيمة المخزون: $stockValue ر.ي ($itemCount صنف)"
                    }
                }

            } else {
                aiResponse
            }

            val updatedHistory = aiChatHistory.value.toMutableList()
            updatedHistory.add(Pair(finalResponse, false))
            aiChatHistory.value = updatedHistory
            isAiLoading.value = false
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _selectedReportCurrency = kotlinx.coroutines.flow.MutableStateFlow("")
    val selectedReportCurrency: kotlinx.coroutines.flow.StateFlow<String> = _selectedReportCurrency.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currencyReportBalances: kotlinx.coroutines.flow.StateFlow<List<AccountCurrencyBalance>> = _selectedReportCurrency
        .flatMapLatest { currency ->
            if (currency.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.journalDao.getAccountBalancesByCurrencyFlow(currency)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Auto-initialize selected currency from system default
        viewModelScope.launch {
            currencies.collect { list ->
                if (_selectedReportCurrency.value.isEmpty()) {
                    val defaultCurr = list.firstOrNull { it.isDefault }
                    _selectedReportCurrency.value = defaultCurr?.let { it.symbol.ifEmpty { it.code } }
                        ?: list.firstOrNull()?.let { it.symbol.ifEmpty { it.code } }
                        ?: "ر.ي"
                }
            }        }
    }

    fun setReportCurrency(currency: String) {
        _selectedReportCurrency.value = currency
    }

    fun createPartner(name: String, percentage: Double, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.createPartner(name, percentage, notes)
            onComplete()
        }
    }

    private val _availableProfitToDistribute = MutableStateFlow(0.0)
    val availableProfitToDistribute: StateFlow<Double> = _availableProfitToDistribute.asStateFlow()

    fun calculateAvailableProfit() {
        viewModelScope.launch(Dispatchers.IO) {
            val rawSales = repository.invoiceDao.sumInvoicesByTypes(listOf("SALE_CASH", "SALE_CREDIT")) ?: 0.0
            val rawSalesReturn = repository.invoiceDao.sumInvoicesByTypes(listOf("SALE_RETURN")) ?: 0.0
            val totalSales = rawSales - rawSalesReturn

            val rawPurchases = repository.invoiceDao.sumInvoicesByTypes(listOf("PURCHASE_CASH", "PURCHASE_CREDIT")) ?: 0.0
            val rawPurchasesReturn = repository.invoiceDao.sumInvoicesByTypes(listOf("PURCHASE_RETURN")) ?: 0.0
            val totalPurchases = rawPurchases - rawPurchasesReturn

            val totalExpenses = repository.cashTransactionDao.sumCashTransactionsByType("PAYMENT") ?: 0.0
            
            val netProfit = totalSales - totalPurchases - totalExpenses
            
            val distributed = repository.getAlreadyDistributedProfits()
            _availableProfitToDistribute.value = (netProfit - distributed).coerceAtLeast(0.0)
        }
    }

    fun distributeProfits(amount: Double, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.distributeProfits(amount, notes)
            calculateAvailableProfit()
            onComplete()
        }
    }
}

