package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

class AppRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)

    // DAOs
    val userDao = db.userDao()
    val itemDao = db.itemDao()
    val itemUnitDao = db.itemUnitDao()
    val warehouseDao = db.warehouseDao()
    val itemStockDao = db.itemStockDao()
    val stockTransferDao = db.stockTransferDao()
    val contactDao = db.contactDao()
    val invoiceDao = db.invoiceDao()
    val accountDao = db.accountDao()
    val journalDao = db.journalDao()
    val cashTransactionDao = db.cashTransactionDao()
    val bankTransactionDao = db.bankTransactionDao()
    val auditLogDao = db.auditLogDao()
    val enterpriseSettingDao = db.enterpriseSettingDao()
    val currencyDao = db.currencyDao()
    val remittanceDao = db.remittanceDao()
    val currencyExchangeDao = db.currencyExchangeDao()
    val partnerDao = db.partnerDao()

    // Active User State
    var currentUser: User? = null

    suspend fun seedDatabase() = withContext(Dispatchers.IO) {
        try {
            // 1. Seed Users
            val users = userDao.getAllUsers().firstOrNull()
            if (users.isNullOrEmpty()) {
                val adminUser = User(
                    username = "admin",
                    passwordHash = "123456", // Direct match for prototype simplicity
                    permSale = true, permPurchase = true, permDeleteInvoice = true,
                    permEditInvoice = true, permViewProfits = true, permViewReports = true,
                    permEditPrices = true, permBackup = true, permSettings = true, permAI = true,
                    permAccountStatement = true, permStocktake = true, permPrint = true, permShare = true
                )
                userDao.insertUser(adminUser)
            }

            // 2. Seed Accounts Tree
            val accounts = accountDao.getAllAccounts().firstOrNull()
            if (accounts.isNullOrEmpty()) {
                val defaultAccounts = listOf(
                    Account(code = "1", name = "الأصول", type = "ASSETS"),
                    Account(code = "11", name = "الأصول المتداولة", type = "ASSETS", parentId = 1),
                    Account(code = "1101", name = "الصندوق", type = "ASSETS", parentId = 2),
                    Account(code = "1102", name = "البنك", type = "ASSETS", parentId = 2),
                    Account(code = "1103", name = "المخزون", type = "ASSETS", parentId = 2),
                    Account(code = "1201", name = "العملاء (الذمم المدينة)", type = "ASSETS", parentId = 2),

                    Account(code = "2", name = "الخصوم", type = "LIABILITIES"),
                    Account(code = "21", name = "الخصوم المتداولة", type = "LIABILITIES", parentId = 7),
                    Account(code = "2101", name = "الموردين (الذمم الدائنة)", type = "LIABILITIES", parentId = 8),

                    Account(code = "3", name = "حقوق الملكية", type = "EQUITY"),
                    Account(code = "3101", name = "رأس المال", type = "EQUITY", parentId = 10),

                    Account(code = "4", name = "الإيرادات", type = "REVENUE"),
                    Account(code = "4101", name = "مبيعات بضائع", type = "REVENUE", parentId = 12),
                    Account(code = "4102", name = "إيرادات العمولات", type = "REVENUE", parentId = 12),

                    Account(code = "5", name = "المصروفات", type = "EXPENSES"),
                    Account(code = "5101", name = "تكلفة البضاعة المباعة", type = "EXPENSES", parentId = 14),
                    Account(code = "5102", name = "المشتريات", type = "EXPENSES", parentId = 14),
                    Account(code = "5103", name = "مصروفات عمومية وإدارية", type = "EXPENSES", parentId = 14)
                )
                for (acc in defaultAccounts) {
                    accountDao.insertAccount(acc)
                }
            }

            // 3. Seed Warehouse
            val warehouses = warehouseDao.getAllWarehouses().firstOrNull()
            if (warehouses.isNullOrEmpty()) {
                warehouseDao.insertWarehouse(Warehouse(name = "المستودع الرئيسي", location = "المعرض الرئيسي"))
            }

            // 4. Seed Settings
            val settings = enterpriseSettingDao.getSettingsDirect()
            if (settings == null) {
                enterpriseSettingDao.insertSettings(EnterpriseSetting())
            }

            // 5. Seed Currencies
            val currencies = currencyDao.getAllCurrencies().firstOrNull()
            if (currencies.isNullOrEmpty()) {
                currencyDao.insertCurrency(Currency(code = "YER", name = "ريال يمني", symbol = "ر.ي", exchangeRate = 1.0, isDefault = true))
                currencyDao.insertCurrency(Currency(code = "SAR", name = "ريال سعودي", symbol = "ر.س", exchangeRate = 1.0, isDefault = false))
                currencyDao.insertCurrency(Currency(code = "USD", name = "دولار أمريكي", symbol = "$", exchangeRate = 1.0, isDefault = false))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    val licenseManager = com.example.util.LicenseManager(context)

    // Helper to log user operation
    suspend fun logOperation(operation: String, table: String, details: String, oldData: String = "", newData: String = "") {
        licenseManager.incrementOperationCount()
        val user = currentUser ?: User(id = 1, username = "النظام", passwordHash = "")
        auditLogDao.insertLog(
            AuditLog(
                userId = user.id,
                username = user.username,
                operationType = operation,
                tableName = table,
                details = details,
                originalData = oldData,
                updatedData = newData
            )
        )
    }

    // --- Business Transactions with Automatic Journal Entries ---

    suspend fun createInvoice(invoice: Invoice, itemsList: List<InvoiceItem>, accountId: Long? = null) = withContext(Dispatchers.IO) {
        // 1. Insert Invoice
        val invoiceId = invoiceDao.insertInvoice(invoice)

        // 2. Insert items and update stock & account balances
        var totalCost = 0.0
        for (invItem in itemsList) {
            val item = itemDao.getItemById(invItem.itemId) ?: continue
            val savedInvItem = invItem.copy(invoiceId = invoiceId)
            invoiceDao.insertInvoiceItem(savedInvItem)

            // Warehouse Stock Logic
            val whId = invoice.warehouseId ?: 1L
            var stock = itemStockDao.getStockForItemAndWarehouse(item.id, whId)
            if (stock == null) {
                val newStock = ItemStock(itemId = item.id, warehouseId = whId, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                val stockId = itemStockDao.insertItemStock(newStock)
                stock = newStock.copy(id = stockId)
            }
            
            val qtyInBaseUnit = invItem.quantity * invItem.conversionFactor
            
            if (invoice.type == "SALE_CASH" || invoice.type == "SALE_CREDIT") {
                if (stock.quantity < qtyInBaseUnit) {
                    throw Exception("عذراً، الكمية المتوفرة للصنف (${item.name}) في المستودع المحدد غير كافية. المتوفر: ${stock.quantity}")
                }
            }

            val newStockQty = when (invoice.type) {
                "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> stock.quantity - qtyInBaseUnit
                "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> stock.quantity + qtyInBaseUnit
                else -> stock.quantity
            }
            itemStockDao.updateItemStock(stock.copy(quantity = newStockQty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))

            // Total Item Quantity (Backward Compatibility)
            val newTotalQty = when (invoice.type) {
                "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> item.currentQuantity - qtyInBaseUnit
                "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> item.currentQuantity + qtyInBaseUnit
                else -> item.currentQuantity
            }
            itemDao.updateItem(item.copy(currentQuantity = newTotalQty, syncState = "PENDING_UPDATE"))
            
            // Cost calculation is based on base unit purchase price
            totalCost += item.purchasePrice * qtyInBaseUnit
        }

        // 3. Create Journal Entry automatically (القيود اليومية التلقائية)
        val entryNum = "JV-" + System.currentTimeMillis() / 1000
        val jEntryId = journalDao.insertEntry(
            JournalEntry(
                entryNumber = entryNum,
                description = "قيد تلقائي لفاتورة ${getInvoiceTypeArabic(invoice.type)} رقم $invoiceId",
                referenceId = invoiceId,
                referenceType = "INVOICE"
            )
        )

        // Fetch accounts
        val selectedFinAcc = if (accountId != null) accountDao.getAccountById(accountId) else null
        val cashAcc = selectedFinAcc ?: accountDao.getAccountByCode("1101")
        val bankAcc = accountDao.getAccountByCode("1102")
        val invAcc = accountDao.getAccountByCode("1103")
        val recvAcc = accountDao.getAccountByCode("1201")
        val payAcc = accountDao.getAccountByCode("2101")
        val salesAcc = accountDao.getAccountByCode("4101")
        val cogsAcc = accountDao.getAccountByCode("5101")
        val purchAcc = accountDao.getAccountByCode("5102")
        
        val localTotal = invoice.total * invoice.exchangeRate

        when (invoice.type) {
            "SALE_CASH" -> {
                // Debit Cash, Credit Sales
                cashAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "المبيعات النقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                }
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "المبيعات النقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal)) // Revenue increases by credit
                }
                // COGS: Debit COGS, Credit Inventory
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "المخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "SALE_CREDIT" -> {
                // Debit Receivables, Credit Sales
                recvAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مبيعات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                }
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مبيعات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                }
                // Update Customer balance in Contacts
                invoice.contactId?.let { cid ->
                    contactDao.getContactById(cid)?.let { contact ->
                        contactDao.updateContact(contact.copy(balance = contact.balance + invoice.total))
                    }
                }
                // COGS: Debit COGS, Credit Inventory
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "المخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "PURCHASE_CASH" -> {
                // Debit Inventory, Credit Cash
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مشتريات نقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                }
                cashAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مشتريات نقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance - localTotal))
                }
            }
            "PURCHASE_CREDIT" -> {
                // Debit Inventory, Credit Payables
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مشتريات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                }
                payAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مشتريات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + localTotal)) // Liabilities increase by Credit
                }
                // Update Supplier balance in Contacts (Credit balance increase)
                invoice.contactId?.let { cid ->
                    contactDao.getContactById(cid)?.let { contact ->
                        contactDao.updateContact(contact.copy(balance = contact.balance + invoice.total))
                    }
                }
            }
            "SALE_RETURN" -> {
                // Debit Sales, Credit Cash (or Credit Receivables)
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مرتجع مبيعات"))
                    accountDao.updateAccount(it.copy(balance = it.balance - localTotal)) // Revenue decreases by Debit
                }
                if (invoice.paymentMethod == "آجل") {
                    recvAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مرتجع مبيعات آجل"))
                        accountDao.updateAccount(it.copy(balance = it.balance - localTotal))
                    }
                    // Update Customer balance in Contacts
                    invoice.contactId?.let { cid ->
                        contactDao.getContactById(cid)?.let { contact ->
                            contactDao.updateContact(contact.copy(balance = contact.balance - invoice.total))
                        }
                    }
                } else {
                    cashAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مرتجع مبيعات نقدي"))
                        accountDao.updateAccount(it.copy(balance = it.balance - localTotal))
                    }
                }
                // Reverse COGS: Debit Inventory, Credit COGS
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "إرجاع للمخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "تخفيض تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "PURCHASE_RETURN" -> {
                // Debit Cash (or Debit Payables), Credit Inventory
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = localTotal, description = "مرتجع مشتريات"))
                    accountDao.updateAccount(it.copy(balance = it.balance - localTotal)) // Assets decrease by Credit
                }
                if (invoice.paymentMethod == "آجل") {
                    payAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مرتجع مشتريات آجل"))
                        accountDao.updateAccount(it.copy(balance = it.balance - localTotal)) // Liabilities decrease by Debit
                    }
                    // Update Supplier balance in Contacts (Credit balance decrease)
                    invoice.contactId?.let { cid ->
                        contactDao.getContactById(cid)?.let { contact ->
                            contactDao.updateContact(contact.copy(balance = contact.balance - invoice.total))
                        }
                    }
                } else {
                    cashAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = localTotal, credit = 0.0, description = "مرتجع مشتريات نقدي"))
                        accountDao.updateAccount(it.copy(balance = it.balance + localTotal))
                    }
                }
            }
        }

        logOperation("إضافة فاتورة", "invoices", "إنشاء فاتورة مبيعات/مشتريات بقيمة ${invoice.total}")
        invoiceId
    }

    suspend fun createCashTransaction(tx: CashTransaction) = withContext(Dispatchers.IO) {
        // 1. Insert Cash Transaction
        val txId = cashTransactionDao.insertCashTransaction(tx)

        // 2. Create Journal Entry & Update Accounts
        val entryNum = "JV-CASH-" + System.currentTimeMillis() / 1000
        val jEntryId = journalDao.insertEntry(
            JournalEntry(
                entryNumber = entryNum,
                description = "قيد تلقائي لسند ${if (tx.type == "RECEIPT") "القبض" else "الصرف"} رقم $txId",
                referenceId = txId,
                referenceType = "CASH_TX"
            )
        )

        val mainCashAccount = accountDao.getAccountById(tx.accountId) ?: accountDao.getAccountByCode("1101")
        val counterpartAccount = tx.counterpartAccountId?.let { accountDao.getAccountById(it) }
            ?: (if (tx.type == "RECEIPT") accountDao.getAccountByCode("1201") else accountDao.getAccountByCode("5103"))

        val mainDesc = tx.mainAccountNotes.ifEmpty { tx.notes.ifEmpty { "سند ${if (tx.type == "RECEIPT") "قبض" else "صرف"} - طرف الخزينة/البنك" } }
        val counterpartDesc = tx.counterpartAccountNotes.ifEmpty { tx.notes.ifEmpty { "سند ${if (tx.type == "RECEIPT") "قبض" else "صرف"} - الطرف المقابل" } }

        val localAmount = tx.amount * tx.exchangeRate

        if (tx.type == "RECEIPT") {
            // Receipt: Debit Main Cash/Bank (+), Credit Counterpart (-)
            mainCashAccount?.let { acc ->
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = acc.id, debit = localAmount, credit = 0.0, description = mainDesc))
                accountDao.updateAccount(acc.copy(balance = acc.balance + localAmount))
            }
            counterpartAccount?.let { acc ->
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = acc.id, debit = 0.0, credit = localAmount, description = counterpartDesc))
                val newBal = when (acc.type) {
                    "ASSETS" -> acc.balance - localAmount
                    "LIABILITIES", "EQUITY", "REVENUE" -> acc.balance + localAmount
                    else -> acc.balance - localAmount
                }
                accountDao.updateAccount(acc.copy(balance = newBal))
            }
            if (tx.referenceType == "CONTACT" && tx.referenceId != null) {
                contactDao.getContactById(tx.referenceId)?.let { contact ->
                    contactDao.updateContact(contact.copy(balance = contact.balance - localAmount))
                }
            }
        } else {
            // Payment: Debit Counterpart (+), Credit Main Cash/Bank (-)
            counterpartAccount?.let { acc ->
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = acc.id, debit = localAmount, credit = 0.0, description = counterpartDesc))
                val newBal = when (acc.type) {
                    "ASSETS", "EXPENSES" -> acc.balance + localAmount
                    "LIABILITIES", "EQUITY", "REVENUE" -> acc.balance - localAmount
                    else -> acc.balance + localAmount
                }
                accountDao.updateAccount(acc.copy(balance = newBal))
            }
            mainCashAccount?.let { acc ->
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = acc.id, debit = 0.0, credit = localAmount, description = mainDesc))
                accountDao.updateAccount(acc.copy(balance = acc.balance - localAmount))
            }

            if (tx.referenceType == "CONTACT" && tx.referenceId != null) {
                contactDao.getContactById(tx.referenceId)?.let { contact ->
                    contactDao.updateContact(contact.copy(balance = contact.balance - localAmount))
                }
            }
        }

        logOperation("سند مالي", "cash_transactions", "سند ${if (tx.type == "RECEIPT") "قبض" else "صرف"} بقيمة ${tx.amount}")
        txId
    }

    suspend fun createManualJournalEntry(entry: JournalEntry, lines: List<JournalEntryLine>) = withContext(Dispatchers.IO) {
        val entryId = journalDao.insertEntry(entry)
        for (line in lines) {
            val lineWithId = line.copy(journalEntryId = entryId)
            journalDao.insertEntryLine(lineWithId)
            
            // Update account balance
            val account = accountDao.getAccountById(line.accountId)
            account?.let { acc ->
                val debitDiff = line.debit
                val creditDiff = line.credit
                val change = when (acc.type) {
                    "ASSETS", "EXPENSES" -> debitDiff - creditDiff
                    "LIABILITIES", "EQUITY", "REVENUE" -> creditDiff - debitDiff
                    else -> debitDiff - creditDiff
                }
                accountDao.updateAccount(acc.copy(balance = acc.balance + change))
            }
        }
        logOperation("قيد يدوي", "journal_entries", "إنشاء قيد يدوي برقم ${entry.entryNumber}")
        entryId
    }

    private fun getInvoiceTypeArabic(type: String): String {
        return when (type) {
            "SALE_CASH" -> "مبيعات نقدية"
            "SALE_CREDIT" -> "مبيعات آجلة"
            "PURCHASE_CASH" -> "مشتريات نقدية"
            "PURCHASE_CREDIT" -> "مشتريات آجلة"
            "SALE_RETURN" -> "مرتجع مبيعات"
            "PURCHASE_RETURN" -> "مرتجع مشتريات"
            else -> type
        }
    }

    suspend fun deleteInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        // 1. Reverse Inventory (Stock)
        val itemsList = invoiceDao.getItemsForInvoice(invoice.id)
        for (invItem in itemsList) {
            val qtyInBaseUnit = if (invItem.conversionFactor > 0) invItem.quantity * invItem.conversionFactor else invItem.quantity
            val item = itemDao.getItemById(invItem.itemId)
            if (item != null) {
                // Reverse Warehouse Stock
                val whId = invoice.warehouseId ?: 1L
                var stock = itemStockDao.getStockForItemAndWarehouse(item.id, whId)
                if (stock == null) {
                    val newStock = ItemStock(itemId = item.id, warehouseId = whId, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                    val stockId = itemStockDao.insertItemStock(newStock)
                    stock = newStock.copy(id = stockId)
                }
                val newStockQty = when (invoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> stock.quantity + qtyInBaseUnit // reverse deduction
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> stock.quantity - qtyInBaseUnit // reverse addition
                    else -> stock.quantity
                }
                itemStockDao.updateItemStock(stock.copy(quantity = newStockQty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))

                // Reverse Total Item Quantity
                val newTotalQty = when (invoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> item.currentQuantity + qtyInBaseUnit
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> item.currentQuantity - qtyInBaseUnit
                    else -> item.currentQuantity
                }
                itemDao.updateItem(item.copy(currentQuantity = newTotalQty, syncState = "PENDING_UPDATE"))
            }
        }

        // 2. Reverse Contact Balance
        invoice.contactId?.let { cid ->
            contactDao.getContactById(cid)?.let { contact ->
                val balanceChange = when (invoice.type) {
                    "SALE_CREDIT" -> -invoice.total
                    "PURCHASE_CREDIT" -> -invoice.total
                    "SALE_RETURN" -> if (invoice.paymentMethod == "آجل") invoice.total else 0.0
                    "PURCHASE_RETURN" -> if (invoice.paymentMethod == "آجل") invoice.total else 0.0
                    else -> 0.0
                }
                if (balanceChange != 0.0) {
                    contactDao.updateContact(contact.copy(balance = contact.balance + balanceChange))
                }
            }
        }

        // 3. Reverse Journal Entry and Account Balances
        val jEntry = journalDao.getEntryByReference(invoice.id, "INVOICE")
        if (jEntry != null) {
            val lines = journalDao.getLinesForEntry(jEntry.id)
            for (line in lines) {
                val account = accountDao.getAccountById(line.accountId)
                if (account != null) {
                    // Reverse the impact of this line on the account balance
                    val change = when (account.type) {
                        "ASSETS", "EXPENSES" -> line.credit - line.debit // reversed!
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.debit - line.credit // reversed!
                        else -> line.credit - line.debit
                    }
                    accountDao.updateAccount(account.copy(balance = account.balance + change))
                }
            }
            // Soft Delete the journal entry and lines
            val ts = System.currentTimeMillis()
            journalDao.updateLinesDeletedStatus(jEntry.id, true, ts)
            journalDao.updateEntry(jEntry.copy(isDeleted = true, syncState = "PENDING_UPDATE", updatedAt = ts))
        }

        // 4. Finally, Soft Delete Invoice and its Items
        val ts = System.currentTimeMillis()
        invoiceDao.updateInvoiceItemsDeletedStatus(invoice.id, true, ts)
        invoiceDao.updateInvoice(invoice.copy(isDeleted = true, syncState = "PENDING_UPDATE", updatedAt = ts))
        logOperation("حذف مؤقت", "invoices", "تم حذف الفاتورة رقم ${invoice.invoiceNumber} وعكس تأثيرها المحاسبي والمخزني بالكامل.")
    }

    suspend fun restoreInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        // 1. Re-apply Inventory (Stock)
        val itemsList = invoiceDao.getAllItemsForInvoice(invoice.id)
        for (invItem in itemsList) {
            val qtyInBaseUnit = if (invItem.conversionFactor > 0) invItem.quantity * invItem.conversionFactor else invItem.quantity
            val item = itemDao.getItemById(invItem.itemId)
            if (item != null) {
                val whId = invoice.warehouseId ?: 1L
                var stock = itemStockDao.getStockForItemAndWarehouse(item.id, whId)
                if (stock == null) {
                    val newStock = ItemStock(itemId = item.id, warehouseId = whId, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                    val stockId = itemStockDao.insertItemStock(newStock)
                    stock = newStock.copy(id = stockId)
                }
                val newStockQty = when (invoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> stock.quantity - qtyInBaseUnit
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> stock.quantity + qtyInBaseUnit
                    else -> stock.quantity
                }
                itemStockDao.updateItemStock(stock.copy(quantity = newStockQty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))

                val newTotalQty = when (invoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> item.currentQuantity - qtyInBaseUnit
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> item.currentQuantity + qtyInBaseUnit
                    else -> item.currentQuantity
                }
                itemDao.updateItem(item.copy(currentQuantity = newTotalQty, syncState = "PENDING_UPDATE"))
            }
        }

        // 2. Re-apply Contact Balance
        invoice.contactId?.let { cid ->
            contactDao.getContactById(cid)?.let { contact ->
                val balanceChange = when (invoice.type) {
                    "SALE_CREDIT" -> invoice.total
                    "PURCHASE_CREDIT" -> invoice.total
                    "SALE_RETURN" -> if (invoice.paymentMethod == "آجل") -invoice.total else 0.0
                    "PURCHASE_RETURN" -> if (invoice.paymentMethod == "آجل") -invoice.total else 0.0
                    else -> 0.0
                }
                if (balanceChange != 0.0) {
                    contactDao.updateContact(contact.copy(balance = contact.balance + balanceChange))
                }
            }
        }

        // 3. Re-apply Journal Entry and Account Balances
        val jEntry = journalDao.getAnyEntryByReference(invoice.id, "INVOICE")
        if (jEntry != null) {
            val lines = journalDao.getAllLinesForEntry(jEntry.id)
            for (line in lines) {
                val account = accountDao.getAccountById(line.accountId)
                if (account != null) {
                    val change = when (account.type) {
                        "ASSETS", "EXPENSES" -> line.debit - line.credit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.credit - line.debit
                        else -> line.debit - line.credit
                    }
                    accountDao.updateAccount(account.copy(balance = account.balance + change))
                }
            }
            val ts = System.currentTimeMillis()
            journalDao.updateLinesDeletedStatus(jEntry.id, false, ts)
            journalDao.updateEntry(jEntry.copy(isDeleted = false, syncState = "PENDING_UPDATE", updatedAt = ts))
        }

        // 4. Set Invoice and Items to not deleted
        val ts = System.currentTimeMillis()
        invoiceDao.updateInvoiceItemsDeletedStatus(invoice.id, false, ts)
        invoiceDao.updateInvoice(invoice.copy(isDeleted = false, syncState = "PENDING_UPDATE", updatedAt = ts))
        logOperation("استعادة", "invoices", "تم استعادة الفاتورة رقم ${invoice.invoiceNumber} وإعادة تأثيرها المحاسبي والمخزني.")
    }

    suspend fun permanentDeleteInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        val jEntry = journalDao.getAnyEntryByReference(invoice.id, "INVOICE")
        if (jEntry != null) {
            journalDao.deleteLinesForEntry(jEntry.id)
            journalDao.deleteEntry(jEntry)
        }
        invoiceDao.deleteInvoiceItems(invoice.id)
        invoiceDao.deleteInvoice(invoice)
        logOperation("حذف نهائي", "invoices", "تم حذف الفاتورة رقم ${invoice.invoiceNumber} بشكل نهائي.")
    }

    suspend fun updateInvoice(oldInvoice: Invoice, newInvoice: Invoice, newItemsList: List<InvoiceItem>, accountId: Long? = null) = withContext(Dispatchers.IO) {
        // To safely update an invoice and maintain double-entry and inventory integrity:
        // 1. Reverse the entire impact of the OLD invoice.
        // We do not delete the invoice row itself, we just reverse its effects.

        // Reverse Stock
        val oldItemsList = invoiceDao.getItemsForInvoice(oldInvoice.id)
        for (invItem in oldItemsList) {
            val item = itemDao.getItemById(invItem.itemId)
            if (item != null) {
                // Reverse Warehouse Stock
                val whId = oldInvoice.warehouseId ?: 1L
                var stock = itemStockDao.getStockForItemAndWarehouse(item.id, whId)
                if (stock == null) {
                    val newStock = ItemStock(itemId = item.id, warehouseId = whId, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                    val stockId = itemStockDao.insertItemStock(newStock)
                    stock = newStock.copy(id = stockId)
                }
                
                val qtyInBaseUnit = invItem.quantity * invItem.conversionFactor
                
                val newStockQty = when (oldInvoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> stock.quantity + qtyInBaseUnit
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> stock.quantity - qtyInBaseUnit
                    else -> stock.quantity
                }
                itemStockDao.updateItemStock(stock.copy(quantity = newStockQty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))

                val newTotalQty = when (oldInvoice.type) {
                    "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> item.currentQuantity + qtyInBaseUnit
                    "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> item.currentQuantity - qtyInBaseUnit
                    else -> item.currentQuantity
                }
                itemDao.updateItem(item.copy(currentQuantity = newTotalQty, syncState = "PENDING_UPDATE"))
            }
        }

        // Reverse Contact Balance
        oldInvoice.contactId?.let { cid ->
            contactDao.getContactById(cid)?.let { contact ->
                val balanceChange = when (oldInvoice.type) {
                    "SALE_CREDIT" -> -oldInvoice.total
                    "PURCHASE_CREDIT" -> -oldInvoice.total
                    "SALE_RETURN" -> if (oldInvoice.paymentMethod == "آجل") oldInvoice.total else 0.0
                    "PURCHASE_RETURN" -> if (oldInvoice.paymentMethod == "آجل") oldInvoice.total else 0.0
                    else -> 0.0
                }
                if (balanceChange != 0.0) {
                    contactDao.updateContact(contact.copy(balance = contact.balance + balanceChange))
                }
            }
        }

        // Reverse Journal Entry
        val jEntry = journalDao.getEntryByReference(oldInvoice.id, "INVOICE")
        if (jEntry != null) {
            val lines = journalDao.getLinesForEntry(jEntry.id)
            for (line in lines) {
                val account = accountDao.getAccountById(line.accountId)
                if (account != null) {
                    val change = when (account.type) {
                        "ASSETS", "EXPENSES" -> line.credit - line.debit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.debit - line.credit
                        else -> line.credit - line.debit
                    }
                    accountDao.updateAccount(account.copy(balance = account.balance + change))
                }
            }
            journalDao.deleteLinesForEntry(jEntry.id)
            journalDao.deleteEntry(jEntry)
        }

        // Delete old items
        invoiceDao.deleteInvoiceItems(oldInvoice.id)

        // 2. Apply the NEW invoice details
        invoiceDao.updateInvoice(newInvoice)
        
        // Re-run the creation logic (which inserts new items, updates stock, creates new journal entry, updates contact)
        // Note: we can't just call createInvoice because createInvoice calls insertInvoice which might generate a new ID.
        // Actually, if we pass an Invoice with id > 0 to insertInvoice(OnConflictStrategy.REPLACE), it replaces and returns the same ID.
        // But to be perfectly safe without duplicating code, I'll extract the "apply effects" logic or just duplicate it here cleanly.

        var totalCost = 0.0
        for (invItem in newItemsList) {
            val item = itemDao.getItemById(invItem.itemId) ?: continue
            val savedInvItem = invItem.copy(invoiceId = newInvoice.id)
            invoiceDao.insertInvoiceItem(savedInvItem)

            val qtyInBaseUnit = invItem.quantity * invItem.conversionFactor
            
            // Apply Warehouse Stock
            val whId = newInvoice.warehouseId ?: 1L
            var stock = itemStockDao.getStockForItemAndWarehouse(item.id, whId)
            if (stock == null) {
                val newStock = ItemStock(itemId = item.id, warehouseId = whId, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                val stockId = itemStockDao.insertItemStock(newStock)
                stock = newStock.copy(id = stockId)
            }
            
            if (newInvoice.type == "SALE_CASH" || newInvoice.type == "SALE_CREDIT") {
                if (stock.quantity < qtyInBaseUnit) {
                    throw Exception("عذراً، الكمية المتوفرة للصنف (${item.name}) في المستودع المحدد غير كافية بعد التعديل. المتوفر: ${stock.quantity}")
                }
            }

            val newStockQty = when (newInvoice.type) {
                "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> stock.quantity - qtyInBaseUnit
                "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> stock.quantity + qtyInBaseUnit
                else -> stock.quantity
            }
            itemStockDao.updateItemStock(stock.copy(quantity = newStockQty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))

            val newTotalQty = when (newInvoice.type) {
                "SALE_CASH", "SALE_CREDIT", "PURCHASE_RETURN" -> item.currentQuantity - qtyInBaseUnit
                "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_RETURN" -> item.currentQuantity + qtyInBaseUnit
                else -> item.currentQuantity
            }
            itemDao.updateItem(item.copy(currentQuantity = newTotalQty, syncState = "PENDING_UPDATE"))
            totalCost += item.purchasePrice * qtyInBaseUnit
        }

        val entryNum = "JV-" + System.currentTimeMillis() / 1000
        val newJEntryId = journalDao.insertEntry(
            JournalEntry(
                entryNumber = entryNum,
                description = "قيد تلقائي لفاتورة ${getInvoiceTypeArabic(newInvoice.type)} رقم ${newInvoice.id} (معدلة)",
                referenceId = newInvoice.id,
                referenceType = "INVOICE"
            )
        )

        val selectedFinAcc = if (accountId != null) accountDao.getAccountById(accountId) else null
        val cashAcc = selectedFinAcc ?: accountDao.getAccountByCode("1101")
        val bankAcc = accountDao.getAccountByCode("1102")
        val invAcc = accountDao.getAccountByCode("1103")
        val recvAcc = accountDao.getAccountByCode("1201")
        val payAcc = accountDao.getAccountByCode("2101")
        val salesAcc = accountDao.getAccountByCode("4101")
        val cogsAcc = accountDao.getAccountByCode("5101")
        val purchAcc = accountDao.getAccountByCode("5102")

        when (newInvoice.type) {
            "SALE_CASH" -> {
                cashAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "المبيعات النقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "المبيعات النقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "المخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "SALE_CREDIT" -> {
                recvAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مبيعات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مبيعات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                newInvoice.contactId?.let { cid ->
                    contactDao.getContactById(cid)?.let { contact ->
                        contactDao.updateContact(contact.copy(balance = contact.balance + newInvoice.total))
                    }
                }
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "المخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "PURCHASE_CASH" -> {
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مشتريات نقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                cashAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مشتريات نقدية"))
                    accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                }
            }
            "PURCHASE_CREDIT" -> {
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مشتريات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                payAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مشتريات آجلة"))
                    accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                }
                newInvoice.contactId?.let { cid ->
                    contactDao.getContactById(cid)?.let { contact ->
                        contactDao.updateContact(contact.copy(balance = contact.balance + newInvoice.total))
                    }
                }
            }
            "SALE_RETURN" -> {
                salesAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مرتجع مبيعات"))
                    accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                }
                if (newInvoice.paymentMethod == "آجل") {
                    recvAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مرتجع مبيعات آجل"))
                        accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                    }
                    newInvoice.contactId?.let { cid ->
                        contactDao.getContactById(cid)?.let { contact ->
                            contactDao.updateContact(contact.copy(balance = contact.balance - newInvoice.total))
                        }
                    }
                } else {
                    cashAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مرتجع مبيعات نقدي"))
                        accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                    }
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = totalCost, credit = 0.0, description = "إرجاع للمخزون السلعي"))
                    accountDao.updateAccount(it.copy(balance = it.balance + totalCost))
                }
                cogsAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = totalCost, description = "تخفيض تكلفة البضاعة المباعة"))
                    accountDao.updateAccount(it.copy(balance = it.balance - totalCost))
                }
            }
            "PURCHASE_RETURN" -> {
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = 0.0, credit = newInvoice.total, description = "مرتجع مشتريات"))
                    accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                }
                if (newInvoice.paymentMethod == "آجل") {
                    payAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مرتجع مشتريات آجل"))
                        accountDao.updateAccount(it.copy(balance = it.balance - newInvoice.total))
                    }
                    newInvoice.contactId?.let { cid ->
                        contactDao.getContactById(cid)?.let { contact ->
                            contactDao.updateContact(contact.copy(balance = contact.balance - newInvoice.total))
                        }
                    }
                } else {
                    cashAcc?.let {
                        journalDao.insertEntryLine(JournalEntryLine(journalEntryId = newJEntryId, accountId = it.id, debit = newInvoice.total, credit = 0.0, description = "مرتجع مشتريات نقدي"))
                        accountDao.updateAccount(it.copy(balance = it.balance + newInvoice.total))
                    }
                }
            }
        }

        logOperation("تعديل", "invoices", "تم تعديل الفاتورة رقم ${newInvoice.invoiceNumber}")
    }

    suspend fun deleteJournalEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val lines = journalDao.getAllLinesForEntry(entry.id)
            for (line in lines) {
                val account = accountDao.getAccountById(line.accountId)
                account?.let { acc ->
                    val change = when (acc.type) {
                        "ASSETS", "EXPENSES" -> line.credit - line.debit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.debit - line.credit
                        else -> line.credit - line.debit
                    }
                    accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                }
            }
            val ts = System.currentTimeMillis()
            journalDao.updateLinesDeletedStatus(entry.id, true, ts)
            journalDao.updateEntry(entry.copy(isDeleted = true, syncState = "PENDING_UPDATE", updatedAt = ts))
        }
        logOperation("حذف مؤقت", "journal_entries", "تم حذف القيد رقم ${entry.id}")
    }

    suspend fun restoreJournalEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val lines = journalDao.getAllLinesForEntry(entry.id)
            for (line in lines) {
                val account = accountDao.getAccountById(line.accountId)
                account?.let { acc ->
                    val change = when (acc.type) {
                        "ASSETS", "EXPENSES" -> line.debit - line.credit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.credit - line.debit
                        else -> line.debit - line.credit
                    }
                    accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                }
            }
            val ts = System.currentTimeMillis()
            journalDao.updateLinesDeletedStatus(entry.id, false, ts)
            journalDao.updateEntry(entry.copy(isDeleted = false, syncState = "PENDING_UPDATE", updatedAt = ts))
        }
        logOperation("استعادة", "journal_entries", "تم استعادة القيد رقم ${entry.id}")
    }

    suspend fun permanentDeleteJournalEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        db.withTransaction {
            journalDao.deleteLinesForEntry(entry.id)
            journalDao.deleteEntry(entry)
        }
        logOperation("حذف نهائي", "journal_entries", "تم حذف القيد رقم ${entry.id} بشكل نهائي")
    }

    suspend fun updateJournalEntry(entry: JournalEntry, newLines: List<JournalEntryLine>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Reverse old balances
            val oldLines = journalDao.getAllLinesForEntry(entry.id)
            for (line in oldLines) {
                val account = accountDao.getAccountById(line.accountId)
                account?.let { acc ->
                    val change = when (acc.type) {
                        "ASSETS", "EXPENSES" -> line.credit - line.debit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.debit - line.credit
                        else -> line.credit - line.debit
                    }
                    accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                }
            }
            
            // Delete old lines
            journalDao.deleteLinesForEntry(entry.id)
            
            // Insert new lines and apply balances
            newLines.forEach { line ->
                val lineWithId = line.copy(journalEntryId = entry.id)
                journalDao.insertEntryLine(lineWithId)
                
                val account = accountDao.getAccountById(line.accountId)
                account?.let { acc ->
                    val change = when (acc.type) {
                        "ASSETS", "EXPENSES" -> line.debit - line.credit
                        "LIABILITIES", "EQUITY", "REVENUE" -> line.credit - line.debit
                        else -> line.debit - line.credit
                    }
                    accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                }
            }
            journalDao.updateEntry(entry.copy(syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
        }
        logOperation("تعديل", "journal_entries", "تم تعديل القيد رقم ${entry.entryNumber}")
    }


    suspend fun updateCashTransaction(tx: CashTransaction) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val oldTx = cashTransactionDao.getCashTransactionById(tx.id)
            if (oldTx != null) {
                // Reverse old transaction accounting
                val journalEntry = journalDao.getEntryByReference(oldTx.id, "CASH_TX")
                if (journalEntry != null) {
                    val lines = journalDao.getLinesForEntry(journalEntry.id)
                    for (line in lines) {
                        val account = accountDao.getAccountById(line.accountId)
                        account?.let { acc ->
                            val debitDiff = -line.debit
                            val creditDiff = -line.credit
                            val change = when (acc.type) {
                                "ASSETS", "EXPENSES" -> debitDiff - creditDiff
                                "LIABILITIES", "EQUITY", "REVENUE" -> creditDiff - debitDiff
                                else -> debitDiff - creditDiff
                            }
                            accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                        }
                        journalDao.deleteEntryLine(line)
                    }
                    journalDao.deleteEntry(journalEntry)
                }
                if (oldTx.referenceType == "CONTACT" && oldTx.referenceId != null) {
                    val oldLocalAmount = oldTx.amount * oldTx.exchangeRate
                    contactDao.getContactById(oldTx.referenceId)?.let { contact ->
                        contactDao.updateContact(contact.copy(balance = contact.balance + oldLocalAmount))
                    }
                }
            }
            
            // Delete old transaction and create new one (which re-adds journals)
            cashTransactionDao.deleteCashTransaction(tx) // Deletes based on ID
            createCashTransaction(tx)
        }
        logOperation("تعديل", "cash_transactions", "تم تعديل السند رقم ${tx.id}")
    }

    suspend fun deleteCashTransaction(tx: CashTransaction) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val journalEntry = journalDao.getEntryByReference(tx.id, "CASH_TX")
            if (journalEntry != null) {
                val lines = journalDao.getLinesForEntry(journalEntry.id)
                for (line in lines) {
                    val account = accountDao.getAccountById(line.accountId)
                    account?.let { acc ->
                        val debitDiff = -line.debit
                        val creditDiff = -line.credit
                        val change = when (acc.type) {
                            "ASSETS", "EXPENSES" -> debitDiff - creditDiff
                            "LIABILITIES", "EQUITY", "REVENUE" -> creditDiff - debitDiff
                            else -> debitDiff - creditDiff
                        }
                        accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                    }
                    val ts = System.currentTimeMillis()
                    journalDao.updateLinesDeletedStatus(journalEntry.id, true, ts)
                }
                val ts = System.currentTimeMillis()
                journalDao.updateEntry(journalEntry.copy(isDeleted = true, syncState = "PENDING_UPDATE", updatedAt = ts))
            }
            
            if (tx.referenceType == "CONTACT" && tx.referenceId != null) {
                val localAmount = tx.amount * tx.exchangeRate
                contactDao.getContactById(tx.referenceId)?.let { contact ->
                    contactDao.updateContact(contact.copy(balance = contact.balance + localAmount))
                }
            }

            val ts = System.currentTimeMillis()
            cashTransactionDao.updateCashTransaction(tx.copy(isDeleted = true, syncState = "PENDING_UPDATE", updatedAt = ts))
        }
        logOperation("حذف مؤقت", "cash_transactions", "تم حذف السند رقم ${tx.id}")
    }

    suspend fun restoreCashTransaction(tx: CashTransaction) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val journalEntry = journalDao.getAnyEntryByReference(tx.id, "CASH_TX")
            if (journalEntry != null) {
                val lines = journalDao.getAllLinesForEntry(journalEntry.id)
                for (line in lines) {
                    val account = accountDao.getAccountById(line.accountId)
                    account?.let { acc ->
                        val change = when (acc.type) {
                            "ASSETS", "EXPENSES" -> line.debit - line.credit
                            "LIABILITIES", "EQUITY", "REVENUE" -> line.credit - line.debit
                            else -> line.debit - line.credit
                        }
                        accountDao.updateAccount(acc.copy(balance = acc.balance + change))
                    }
                }
                val ts = System.currentTimeMillis()
                journalDao.updateLinesDeletedStatus(journalEntry.id, false, ts)
                journalDao.updateEntry(journalEntry.copy(isDeleted = false, syncState = "PENDING_UPDATE", updatedAt = ts))
            }
            
            if (tx.referenceType == "CONTACT" && tx.referenceId != null) {
                val localAmount = tx.amount * tx.exchangeRate
                contactDao.getContactById(tx.referenceId)?.let { contact ->
                    contactDao.updateContact(contact.copy(balance = contact.balance - localAmount)) // Receipt and Payment both subtracted originally
                }
            }

            val ts = System.currentTimeMillis()
            cashTransactionDao.updateCashTransaction(tx.copy(isDeleted = false, syncState = "PENDING_UPDATE", updatedAt = ts))
        }
        logOperation("استعادة", "cash_transactions", "تم استعادة السند رقم ${tx.id}")
    }

    suspend fun permanentDeleteCashTransaction(tx: CashTransaction) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val journalEntry = journalDao.getAnyEntryByReference(tx.id, "CASH_TX")
            if (journalEntry != null) {
                journalDao.deleteLinesForEntry(journalEntry.id)
                journalDao.deleteEntry(journalEntry)
            }
            cashTransactionDao.deleteCashTransaction(tx)
        }
        logOperation("حذف نهائي", "cash_transactions", "تم حذف السند رقم ${tx.id} بشكل نهائي")
    }

    suspend fun createRemittance(rem: Remittance) = withContext(Dispatchers.IO) {
        val remId = remittanceDao.insert(rem)

        val entryNum = "JV-REM-" + System.currentTimeMillis() / 1000
        val jEntryId = journalDao.insertEntry(
            JournalEntry(
                entryNumber = entryNum,
                description = "قيد تلقائي لـ ${if (rem.type == "OUTGOING") "حوالة صادرة" else "حوالة واردة"} رقم $remId",
                referenceId = remId,
                referenceType = "REMITTANCE",
                currencyCode = rem.currencyCode
            )
        )

        val safeAcc = accountDao.getAccountById(rem.safeAccountId) ?: accountDao.getAccountByCode("1101")
        val transferCompanyAcc = accountDao.getAccountById(rem.accountId) ?: accountDao.getAccountByCode("2101")
        
        var commAcc = accountDao.getAccountByCode("4102")
        if (commAcc == null) {
            val revenueParent = accountDao.getAccountByCode("4")
            var newCommAcc = Account(code = "4102", name = "إيرادات العمولات", type = "REVENUE", parentId = revenueParent?.id)
            val insertedId = accountDao.insertAccount(newCommAcc)
            commAcc = newCommAcc.copy(id = insertedId)
        }

        if (rem.type == "OUTGOING") {
            val totalReceived = rem.amount + rem.commissionAmount
            safeAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = totalReceived, credit = 0.0, description = "استلام نقدية حوالة صادرة"))
                accountDao.updateAccount(it.copy(balance = it.balance + totalReceived))
            }
            transferCompanyAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = rem.amount, description = "إرسال حوالة صادرة لشركة التحويل"))
                val newBal = when(it.type) {
                    "ASSETS" -> it.balance - rem.amount
                    else -> it.balance + rem.amount
                }
                accountDao.updateAccount(it.copy(balance = newBal))
            }
            commAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = rem.commissionAmount, description = "عمولة حوالة صادرة"))
                accountDao.updateAccount(it.copy(balance = it.balance + rem.commissionAmount))
            }
        } else {
            val payout = rem.amount - rem.commissionAmount
            safeAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = payout, description = "صرف حوالة واردة"))
                accountDao.updateAccount(it.copy(balance = it.balance - payout))
            }
            transferCompanyAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = rem.amount, credit = 0.0, description = "استلام حوالة من شركة التحويل"))
                val newBal = when(it.type) {
                    "ASSETS" -> it.balance + rem.amount
                    else -> it.balance - rem.amount
                }
                accountDao.updateAccount(it.copy(balance = newBal))
            }
            commAcc?.let {
                journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = rem.commissionAmount, description = "عمولة حوالة واردة"))
                accountDao.updateAccount(it.copy(balance = it.balance + rem.commissionAmount))
            }
        }
        logOperation("إضافة", "remittances", "إضافة حوالة ${if (rem.type == "OUTGOING") "صادرة" else "واردة"} بقيمة ${rem.amount}")
        remId
    }

    suspend fun createCurrencyExchange(exchange: CurrencyExchange) = withContext(Dispatchers.IO) {
        val exId = currencyExchangeDao.insert(exchange)
        val entryNum = "JV-EXCH-" + System.currentTimeMillis() / 1000
        val jEntryId = journalDao.insertEntry(
            JournalEntry(
                entryNumber = entryNum,
                description = "قيد مصارفة عملات رقم $exId - ${exchange.fromCurrency} → ${exchange.toCurrency}",
                referenceId = exId,
                referenceType = "EXCHANGE",
                currencyCode = exchange.toCurrency
            )
        )
        val safeAcc = accountDao.getAccountById(exchange.safeAccountId) ?: accountDao.getAccountByCode("1101")
        safeAcc?.let {
            journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = exchange.toAmount, credit = 0.0, description = "دخول عملة ${exchange.toCurrency} (مصارفة)"))
            journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = exchange.fromAmount, description = "خروج عملة ${exchange.fromCurrency} (مصارفة)"))
            // As accounting balances are typically unified, the net effect is balanced by rates. No manual account balance update needed if only doing currency exchange.
        }
        logOperation("إضافة", "currency_exchanges", "مصارفة من ${exchange.fromCurrency} إلى ${exchange.toCurrency}")
        exId
    }

    suspend fun deleteAuditLog(log: AuditLog) = withContext(Dispatchers.IO) {
        auditLogDao.deleteLog(log)
    }

    suspend fun clearAllAuditLogs() = withContext(Dispatchers.IO) {
        auditLogDao.clearLogs()
    }
    suspend fun createStockTransfer(from: Long, to: Long, itemId: Long, qty: Double, notes: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            var fromStock = itemStockDao.getStockForItemAndWarehouse(itemId, from)
            if (fromStock == null) {
                val newStock = ItemStock(itemId = itemId, warehouseId = from, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                val id = itemStockDao.insertItemStock(newStock)
                fromStock = newStock.copy(id = id)
            }
            
            var toStock = itemStockDao.getStockForItemAndWarehouse(itemId, to)
            if (toStock == null) {
                val newStock = ItemStock(itemId = itemId, warehouseId = to, quantity = 0.0, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                val id = itemStockDao.insertItemStock(newStock)
                toStock = newStock.copy(id = id)
            }

            itemStockDao.updateItemStock(fromStock.copy(quantity = fromStock.quantity - qty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
            itemStockDao.updateItemStock(toStock.copy(quantity = toStock.quantity + qty, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
            
            logOperation("تحويل مخزني", "stock_transfers", "تحويل $qty من صنف $itemId من $from إلى $to. $notes")
        }
    }

    suspend fun supplyStockMulti(entries: List<com.example.ui.viewmodel.StockSupplyEntry>, warehouseId: Long, currencyCode: String, exchangeRate: Double, generalNotes: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invoiceNum = "SUP-" + (10000000..99999999).random()
            var total = 0.0
            
            val invoice = Invoice(
                invoiceNumber = invoiceNum,
                type = "STOCK_SUPPLY",
                contactId = null,
                subTotal = 0.0,
                discount = 0.0,
                tax = 0.0,
                total = 0.0,
                paidAmount = 0.0,
                remainingAmount = 0.0,
                paymentMethod = "None",
                notes = generalNotes,
                userId = 1,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                warehouseId = warehouseId
            )
            val invId = invoiceDao.insertInvoice(invoice)
            
            for (entry in entries) {
                val itemSubTotalLocal = (entry.quantity * entry.unitCostInCurrency) * exchangeRate
                total += itemSubTotalLocal
                
                val invItem = InvoiceItem(
                    invoiceId = invId,
                    itemId = entry.itemId,
                    quantity = entry.quantity,
                    unitPrice = entry.unitCostInCurrency * exchangeRate,
                    total = itemSubTotalLocal,
                    unitName = "",
                    conversionFactor = 1.0
                )
                invoiceDao.insertInvoiceItem(invItem)
                
                // Update warehouse stock
                var stock = itemStockDao.getStockForItemAndWarehouse(entry.itemId, warehouseId)
                if (stock == null) {
                    val newStock = ItemStock(itemId = entry.itemId, warehouseId = warehouseId, quantity = entry.quantity, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                    itemStockDao.insertItemStock(newStock)
                } else {
                    itemStockDao.updateItemStock(stock.copy(quantity = stock.quantity + entry.quantity, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
                }
                
                // Update global item quantity
                val item = itemDao.getItemById(entry.itemId)
                if (item != null) {
                    itemDao.updateItem(item.copy(
                        currentQuantity = item.currentQuantity + entry.quantity,
                        purchasePrice = if (entry.unitCostInCurrency > 0) entry.unitCostInCurrency * exchangeRate else item.purchasePrice,
                        expiryDate = if (entry.expiryDate.isNotBlank()) entry.expiryDate else item.expiryDate
                    ))
                }
            }
            
            invoiceDao.updateInvoice(invoice.copy(id = invId, subTotal = total, total = total))
            
            // Create Journal Entry for Stock Supply
            if (total > 0) {
                val entryNum = "JV-SUP-" + System.currentTimeMillis() / 1000
                val jEntryId = journalDao.insertEntry(
                    JournalEntry(
                        entryNumber = entryNum,
                        description = "قيد تلقائي لتوريد مخزني رقم $invoiceNum",
                        referenceId = invId,
                        referenceType = "INVOICE",
                        currencyCode = "ر.ي" // local currency
                    )
                )
                
                val invAcc = accountDao.getAccountByCode("1103")
                var adjAcc = accountDao.getAccountByCode("3102") // Stock Adjustments / Opening Balance
                if (adjAcc == null) {
                    val equityParent = accountDao.getAccountByCode("3")
                    val insertedId = accountDao.insertAccount(Account(code = "3102", name = "تسويات مخزنية ورأس مال", type = "EQUITY", parentId = equityParent?.id))
                    adjAcc = accountDao.getAccountById(insertedId)
                }

                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = total, credit = 0.0, description = "توريد مخزني"))
                    accountDao.updateAccount(it.copy(balance = it.balance + total))
                }
                adjAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = total, description = "توريد مخزني مقابل تسويات"))
                    accountDao.updateAccount(it.copy(balance = it.balance + total)) // EQUITY increases by credit
                }
            }

            logOperation("توريد مخزني", "stock_supply", "توريد ${entries.size} أصناف للمستودع $warehouseId بقيمة $total. $generalNotes")
        }
    }

    suspend fun issueStockMulti(entries: List<com.example.ui.viewmodel.StockSupplyEntry>, warehouseId: Long, currencyCode: String, exchangeRate: Double, generalNotes: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invoiceNum = "ISS-" + (10000000..99999999).random()
            var total = 0.0
            
            val invoice = Invoice(
                invoiceNumber = invoiceNum,
                type = "STOCK_ISSUE",
                contactId = null,
                subTotal = 0.0,
                discount = 0.0,
                tax = 0.0,
                total = 0.0,
                paidAmount = 0.0,
                remainingAmount = 0.0,
                paymentMethod = "None",
                notes = generalNotes,
                userId = 1,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                warehouseId = warehouseId
            )
            val invId = invoiceDao.insertInvoice(invoice)
            
            for (entry in entries) {
                val itemSubTotalLocal = (entry.quantity * entry.unitCostInCurrency) * exchangeRate
                total += itemSubTotalLocal
                
                val invItem = InvoiceItem(
                    invoiceId = invId,
                    itemId = entry.itemId,
                    quantity = entry.quantity, // Positive in the line item, but implies deduction due to invoice type
                    unitPrice = entry.unitCostInCurrency * exchangeRate,
                    total = itemSubTotalLocal,
                    unitName = "",
                    conversionFactor = 1.0
                )
                invoiceDao.insertInvoiceItem(invItem)
                
                // Update warehouse stock (deduct)
                var stock = itemStockDao.getStockForItemAndWarehouse(entry.itemId, warehouseId)
                if (stock == null) {
                    val newStock = ItemStock(itemId = entry.itemId, warehouseId = warehouseId, quantity = -entry.quantity, syncState = "PENDING_ADD", syncId = java.util.UUID.randomUUID().toString())
                    itemStockDao.insertItemStock(newStock)
                } else {
                    itemStockDao.updateItemStock(stock.copy(quantity = stock.quantity - entry.quantity, syncState = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
                }
                
                // Update global item quantity (deduct)
                val item = itemDao.getItemById(entry.itemId)
                if (item != null) {
                    itemDao.updateItem(item.copy(
                        currentQuantity = item.currentQuantity - entry.quantity
                    ))
                }
            }
            
            invoiceDao.updateInvoice(invoice.copy(id = invId, subTotal = total, total = total))
            
            // Create Journal Entry for Stock Issue
            if (total > 0) {
                val entryNum = "JV-ISS-" + System.currentTimeMillis() / 1000
                val jEntryId = journalDao.insertEntry(
                    JournalEntry(
                        entryNumber = entryNum,
                        description = "قيد تلقائي لصرف مخزني رقم $invoiceNum",
                        referenceId = invId,
                        referenceType = "INVOICE",
                        currencyCode = "ر.ي" // local currency
                    )
                )
                
                val invAcc = accountDao.getAccountByCode("1103")
                var adjAcc = accountDao.getAccountByCode("5104") // Stock Adjustments Expenses
                if (adjAcc == null) {
                    val expensesParent = accountDao.getAccountByCode("5")
                    val insertedId = accountDao.insertAccount(Account(code = "5104", name = "خسائر وتسويات مخزنية", type = "EXPENSES", parentId = expensesParent?.id))
                    adjAcc = accountDao.getAccountById(insertedId)
                }

                adjAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = total, credit = 0.0, description = "صرف مخزني مقابل تسويات"))
                    accountDao.updateAccount(it.copy(balance = it.balance + total)) // EXPENSES increases by debit
                }
                invAcc?.let {
                    journalDao.insertEntryLine(JournalEntryLine(journalEntryId = jEntryId, accountId = it.id, debit = 0.0, credit = total, description = "صرف مخزني"))
                    accountDao.updateAccount(it.copy(balance = it.balance - total)) // ASSETS decreases by credit
                }
            }

            logOperation("صرف مخزني", "stock_issue", "صرف ${entries.size} أصناف من المستودع $warehouseId بقيمة $total. $generalNotes")
        }
    }

    suspend fun createPartner(name: String, percentage: Double, notes: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Find parent accounts under EQUITY (حقوق الملكية)
            // First find "حقوق الملكية" which is typically code "3"
            val equityAcc = accountDao.getAccountByCode("3")
            if (equityAcc != null) {
                // Ensure we have a parent for "رأس المال" (31) and "جاري الشركاء" (32) if they don't exist, we can just attach to EQUITY
                // Let's create capital account
                val capitalAcc = Account(
                    code = "31" + System.currentTimeMillis().toString().takeLast(4),
                    name = "رأس مال الشريك - $name",
                    type = "EQUITY",
                    parentId = equityAcc.id
                )
                val capitalAccId = accountDao.insertAccount(capitalAcc)

                // Create current account
                val currentAcc = Account(
                    code = "32" + System.currentTimeMillis().toString().takeLast(4),
                    name = "جاري الشريك - $name",
                    type = "EQUITY",
                    parentId = equityAcc.id
                )
                val currentAccId = accountDao.insertAccount(currentAcc)

                val partner = Partner(
                    name = name,
                    percentage = percentage,
                    notes = notes,
                    capitalAccountId = capitalAccId,
                    currentAccountId = currentAccId
                )
                partnerDao.insertPartner(partner)
            } else {
                // Fallback if EQUITY account is missing (should not happen due to seed)
                val partner = Partner(
                    name = name,
                    percentage = percentage,
                    notes = notes
                )
                partnerDao.insertPartner(partner)
            }
        }
    }

    suspend fun getAlreadyDistributedProfits(): Double = withContext(Dispatchers.IO) {
        return@withContext journalDao.getTotalDistributedProfits() ?: 0.0
    }

    suspend fun distributeProfits(amount: Double, notes: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val partners = partnerDao.getAllPartners().first()
            if (partners.isEmpty()) return@withTransaction

            // Get or create "الأرباح المبقاة" (Retained Earnings) account
            var retainedEarningsAcc = accountDao.getAccountByCode("33") // Assuming 33 is Retained Earnings code
            if (retainedEarningsAcc == null) {
                val equityAcc = accountDao.getAccountByCode("3")
                if (equityAcc != null) {
                    val newAcc = Account(
                        code = "33",
                        name = "الأرباح المبقاة وتوزيعات الأرباح",
                        type = "EQUITY",
                        parentId = equityAcc.id
                    )
                    accountDao.insertAccount(newAcc)
                    retainedEarningsAcc = accountDao.getAccountByCode("33")
                }
            }

            if (retainedEarningsAcc == null) return@withTransaction

            // Create Journal Entry
            val entryNumber = "PROF-" + System.currentTimeMillis().toString().takeLast(6)
            val entry = JournalEntry(
                entryNumber = entryNumber,
                description = notes.ifEmpty { "توزيع أرباح على الشركاء" },
                referenceType = "PROFIT_DISTRIBUTION",
                referenceId = System.currentTimeMillis()
            )
            val entryId = journalDao.insertEntry(entry)

            // Debit Retained Earnings
            journalDao.insertEntryLine(
                JournalEntryLine(
                    journalEntryId = entryId,
                    accountId = retainedEarningsAcc.id,
                    debit = amount,
                    credit = 0.0,
                    description = "إقفال الأرباح الموزعة"
                )
            )

            // Credit each partner's current account
            var totalPercentage = partners.sumOf { it.percentage }
            if (totalPercentage <= 0.0) totalPercentage = 100.0 // prevent division by zero

            for (partner in partners) {
                if (partner.currentAccountId != null) {
                    val partnerShare = (partner.percentage / totalPercentage) * amount
                    journalDao.insertEntryLine(
                        JournalEntryLine(
                            journalEntryId = entryId,
                            accountId = partner.currentAccountId,
                            debit = 0.0,
                            credit = partnerShare,
                            description = "توزيع أرباح: ${partner.name} (${String.format("%.1f", partner.percentage)}%)"
                        )
                    )
                }
            }
        }
    }
}


