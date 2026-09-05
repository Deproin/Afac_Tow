package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RemittanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(remittance: Remittance): Long

    @Query("SELECT * FROM remittances WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllRemittances(): Flow<List<Remittance>>

    @Query("SELECT * FROM remittances WHERE id = :id AND isDeleted = 0")
    suspend fun getRemittanceById(id: Long): Remittance?
    
    @Update
    suspend fun update(remittance: Remittance)
    
    @Delete
    suspend fun delete(remittance: Remittance)
}

@Dao
interface CurrencyExchangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchange: CurrencyExchange): Long

    @Query("SELECT * FROM currency_exchanges WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllExchanges(): Flow<List<CurrencyExchange>>

    @Query("SELECT * FROM currency_exchanges WHERE id = :id AND isDeleted = 0")
    suspend fun getExchangeById(id: Long): CurrencyExchange?
    
    @Update
    suspend fun update(exchange: CurrencyExchange)
    
    @Delete
    suspend fun delete(exchange: CurrencyExchange)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isDeleted = 0 ORDER BY id ASC")
    suspend fun getAllUsersList(): List<User>

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE AND passwordHash = :passwordHash AND isDeleted = 0 LIMIT 1")
    suspend fun login(username: String, passwordHash: String): User?

    @Query("SELECT * FROM users WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items WHERE (code = :code OR barcode = :barcode) AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByCodeOrBarcode(code: String, barcode: String): Item?

    @Query("SELECT * FROM items WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): Item?

    @Query("SELECT * FROM items WHERE currentQuantity <= minLimit AND isDeleted = 0")
    fun getLowStockItems(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0")
    suspend fun countItems(): Int

    @Query("SELECT SUM(purchasePrice * currentQuantity) FROM items WHERE isDeleted = 0")
    suspend fun getStockValue(): Double?

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0")
    fun countItemsFlow(): Flow<Int>

    @Query("SELECT SUM(purchasePrice * currentQuantity) FROM items WHERE isDeleted = 0")
    fun getStockValueFlow(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM items WHERE currentQuantity <= minLimit AND isDeleted = 0")
    suspend fun countLowStockItems(): Int

    @Query("SELECT name FROM items WHERE isDeleted = 0 ORDER BY currentQuantity DESC LIMIT 1")
    suspend fun getTopSalesItemName(): String?
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllWarehouses(): Flow<List<Warehouse>>

    @Query("SELECT * FROM warehouses WHERE id = :id LIMIT 1")
    suspend fun getWarehouseById(id: Long): Warehouse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouse(warehouse: Warehouse): Long

    @Update
    suspend fun updateWarehouse(warehouse: Warehouse)

    @Delete
    suspend fun deleteWarehouse(warehouse: Warehouse)
}

@Dao
interface ItemStockDao {
    @Query("SELECT * FROM item_stocks WHERE isDeleted = 0")
    fun getAllItemStocks(): Flow<List<ItemStock>>

    @Query("SELECT * FROM item_stocks WHERE itemId = :itemId AND isDeleted = 0")
    fun getStocksForItem(itemId: Long): Flow<List<ItemStock>>

    @Query("SELECT * FROM item_stocks WHERE itemId = :itemId AND warehouseId = :warehouseId AND isDeleted = 0 LIMIT 1")
    suspend fun getStockForItemAndWarehouse(itemId: Long, warehouseId: Long): ItemStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemStock(itemStock: ItemStock): Long

    @Update
    suspend fun updateItemStock(itemStock: ItemStock)

    @Delete
    suspend fun deleteItemStock(itemStock: ItemStock)
}

@Dao
interface StockTransferDao {
    @Query("SELECT * FROM stock_transfers WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<StockTransfer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: StockTransfer): Long
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE type = :type AND isDeleted = 0 ORDER BY name ASC")
    fun getContactsByType(type: String): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getContactById(id: Long): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("SELECT COUNT(*) FROM contacts WHERE type = :type AND isDeleted = 0")
    suspend fun countContactsByType(type: String): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE type = :type AND isDeleted = 0")
    fun countContactsByTypeFlow(type: String): Flow<Int>

    @Query("SELECT name FROM contacts WHERE type = 'CUSTOMER' AND isDeleted = 0 ORDER BY balance DESC LIMIT 1")
    suspend fun getBestCustomerName(): String?
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE type IN (:types) AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getInvoicesByTypes(types: List<String>): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getInvoiceById(id: Long): Invoice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId AND isDeleted = 0")
    suspend fun getItemsForInvoice(invoiceId: Long): List<InvoiceItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItem(item: InvoiceItem): Long

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItems(invoiceId: Long)

    @Query("SELECT SUM(total) FROM invoices WHERE type IN (:types) AND isDeleted = 0")
    suspend fun sumInvoicesByTypes(types: List<String>): Double?

    @Query("SELECT SUM(total) FROM invoices WHERE type IN (:types) AND isDeleted = 0")
    fun sumInvoicesByTypesFlow(types: List<String>): Flow<Double?>

    @Query("SELECT SUM(total) FROM invoices WHERE type IN (:types) AND timestamp >= :startTime AND timestamp <= :endTime AND isDeleted = 0")
    fun sumInvoicesByDateFlow(types: List<String>, startTime: Long, endTime: Long): Flow<Double?>

    @Query("""
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') AS dayDate, SUM(total) AS total 
        FROM invoices 
        WHERE type IN ('SALE_CASH', 'SALE_CREDIT') AND timestamp >= :minTimestamp AND isDeleted = 0
        GROUP BY dayDate 
        ORDER BY dayDate ASC
    """)
    fun getWeeklySalesFlow(minTimestamp: Long): Flow<List<com.example.data.model.DailySum>>

    @Query("SELECT COUNT(*) FROM invoices WHERE isDeleted = 0")
    fun countInvoicesFlow(): Flow<Int>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY type, name")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE code = :code AND isDeleted = 0 LIMIT 1")
    suspend fun getAccountByCode(code: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE referenceId = :refId AND referenceType = :refType AND isDeleted = 0 LIMIT 1")
    suspend fun getEntryByReference(refId: Long, refType: String): JournalEntry?

    @Query("SELECT * FROM journal_entry_lines WHERE journalEntryId = :entryId AND isDeleted = 0")
    suspend fun getLinesForEntry(entryId: Long): List<JournalEntryLine>

    @Query("SELECT * FROM journal_entry_lines WHERE accountId = :accId AND isDeleted = 0")
    suspend fun getLinesForAccount(accId: Long): List<JournalEntryLine>

    @Query("""
        SELECT a.id as accountId, a.code, a.name, a.type, e.currencyCode, 
               SUM(CASE WHEN a.type IN ('ASSETS', 'EXPENSES') THEN l.debit - l.credit ELSE l.credit - l.debit END) as balance
        FROM accounts a
        JOIN journal_entry_lines l ON a.id = l.accountId
        JOIN journal_entries e ON l.journalEntryId = e.id
        WHERE e.currencyCode = :currencyCode AND a.isDeleted = 0 AND l.isDeleted = 0 AND e.isDeleted = 0
        GROUP BY a.id, e.currencyCode
    """)
    fun getAccountBalancesByCurrencyFlow(currencyCode: String): Flow<List<AccountCurrencyBalance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryLine(line: JournalEntryLine): Long

    @Query("DELETE FROM journal_entry_lines WHERE journalEntryId = :entryId")
    suspend fun deleteLinesForEntry(entryId: Long)

    @Update
    suspend fun updateEntry(entry: JournalEntry)

    @Delete
    suspend fun deleteEntry(entry: JournalEntry)

    @Query("""
        SELECT strftime('%Y-%m-%d', e.timestamp / 1000, 'unixepoch') AS dayDate,
               SUM(l.credit - l.debit) AS total
        FROM journal_entry_lines l
        JOIN journal_entries e ON l.journalEntryId = e.id
        JOIN accounts a ON l.accountId = a.id
        WHERE a.type IN ('REVENUE', 'EXPENSES') AND e.timestamp >= :minTimestamp AND l.isDeleted = 0 AND e.isDeleted = 0
        GROUP BY dayDate
        ORDER BY dayDate ASC
    """)
    fun getWeeklyProfitsFlow(minTimestamp: Long): Flow<List<com.example.data.model.DailySum>>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE isDeleted = 0")
    fun countJournalEntriesFlow(): Flow<Int>
}

@Dao
interface CashTransactionDao {
    @Query("SELECT * FROM cash_transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllCashTransactions(): Flow<List<CashTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashTransaction(transaction: CashTransaction): Long

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE type = :type AND isDeleted = 0")
    suspend fun sumCashTransactionsByType(type: String): Double?

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE type = :type AND isDeleted = 0")
    fun sumCashTransactionsByTypeFlow(type: String): Flow<Double?>

    @Update
    suspend fun updateCashTransaction(transaction: CashTransaction)

    @Delete
    suspend fun deleteCashTransaction(transaction: CashTransaction)

    @Query("SELECT COUNT(*) FROM cash_transactions WHERE isDeleted = 0")
    fun countCashTransactionsFlow(): Flow<Int>
}

@Dao
interface BankTransactionDao {
    @Query("SELECT * FROM bank_transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllBankTransactions(): Flow<List<BankTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankTransaction(transaction: BankTransaction): Long
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long

    @Delete
    suspend fun deleteLog(log: AuditLog)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface EnterpriseSettingDao {
    @Query("SELECT * FROM enterprise_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<EnterpriseSetting?>

    @Query("SELECT * FROM enterprise_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): EnterpriseSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(setting: EnterpriseSetting)
}

@Dao
interface ItemUnitDao {
    @Query("SELECT * FROM item_units WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllItemUnits(): Flow<List<ItemUnit>>

    @Query("SELECT * FROM item_units WHERE itemId = :itemId AND isDeleted = 0")
    fun getUnitsForItem(itemId: Long): Flow<List<ItemUnit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemUnit(itemUnit: ItemUnit): Long

    @Update
    suspend fun updateItemUnit(itemUnit: ItemUnit)

    @Delete
    suspend fun deleteItemUnit(itemUnit: ItemUnit)

    @Query("DELETE FROM item_units WHERE itemId = :itemId")
    suspend fun deleteUnitsForItem(itemId: Long)
}

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllCurrencies(): Flow<List<Currency>>

    @Query("SELECT * FROM currencies WHERE isDefault = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getDefaultCurrency(): Currency?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: Currency): Long

    @Update
    suspend fun updateCurrency(currency: Currency)

    @Delete
    suspend fun deleteCurrency(currency: Currency)
}

@Dao
interface AccountBalanceDao {
    @Query("SELECT * FROM account_balances WHERE accountId = :accountId AND currencyCode = :currencyCode AND isDeleted = 0 LIMIT 1")
    suspend fun getBalance(accountId: Long, currencyCode: String): AccountBalance?

    @Query("SELECT * FROM account_balances WHERE accountId = :accountId AND isDeleted = 0")
    suspend fun getBalancesForAccount(accountId: Long): List<AccountBalance>

    @Query("SELECT * FROM account_balances WHERE currencyCode = :currencyCode AND isDeleted = 0")
    suspend fun getAllBalancesByCurrency(currencyCode: String): List<AccountBalance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(accountBalance: AccountBalance): Long

    @Update
    suspend fun updateBalance(accountBalance: AccountBalance)
}
