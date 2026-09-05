package com.example.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.data.model.*

@Dao
interface SyncDao {
    // --- Users ---
    @Query("SELECT * FROM users WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingUsers(): List<User>
    @Query("UPDATE users SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markUserSynced(syncId: String)
    @Query("SELECT * FROM users WHERE syncId = :syncId LIMIT 1")
    suspend fun getUserBySyncId(syncId: String): User?

    // --- Items ---
    @Query("SELECT * FROM items WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingItems(): List<Item>
    @Query("UPDATE items SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markItemSynced(syncId: String)
    @Query("SELECT * FROM items WHERE syncId = :syncId LIMIT 1")
    suspend fun getItemBySyncId(syncId: String): Item?

    // --- Item Stocks ---
    @Query("SELECT * FROM item_stocks WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingItemStocks(): List<ItemStock>
    @Query("UPDATE item_stocks SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markItemStockSynced(syncId: String)
    @Query("SELECT * FROM item_stocks WHERE syncId = :syncId LIMIT 1")
    suspend fun getItemStockBySyncId(syncId: String): ItemStock?

    // --- Item Units ---
    @Query("SELECT * FROM item_units WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingItemUnits(): List<ItemUnit>
    @Query("UPDATE item_units SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markItemUnitSynced(syncId: String)
    @Query("SELECT * FROM item_units WHERE syncId = :syncId LIMIT 1")
    suspend fun getItemUnitBySyncId(syncId: String): ItemUnit?

    // --- Warehouses ---
    @Query("SELECT * FROM warehouses WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingWarehouses(): List<Warehouse>
    @Query("UPDATE warehouses SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markWarehouseSynced(syncId: String)
    @Query("SELECT * FROM warehouses WHERE syncId = :syncId LIMIT 1")
    suspend fun getWarehouseBySyncId(syncId: String): Warehouse?

    // --- Contacts ---
    @Query("SELECT * FROM contacts WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingContacts(): List<Contact>
    @Query("UPDATE contacts SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markContactSynced(syncId: String)
    @Query("SELECT * FROM contacts WHERE syncId = :syncId LIMIT 1")
    suspend fun getContactBySyncId(syncId: String): Contact?

    // --- Invoices ---
    @Query("SELECT * FROM invoices WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingInvoices(): List<Invoice>
    @Query("UPDATE invoices SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markInvoiceSynced(syncId: String)
    @Query("SELECT * FROM invoices WHERE syncId = :syncId LIMIT 1")
    suspend fun getInvoiceBySyncId(syncId: String): Invoice?

    // --- Invoice Items ---
    @Query("SELECT * FROM invoice_items WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingInvoiceItems(): List<InvoiceItem>
    @Query("UPDATE invoice_items SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markInvoiceItemSynced(syncId: String)
    @Query("SELECT * FROM invoice_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getInvoiceItemBySyncId(syncId: String): InvoiceItem?

    // --- Accounts ---
    @Query("SELECT * FROM accounts WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingAccounts(): List<Account>
    @Query("UPDATE accounts SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markAccountSynced(syncId: String)
    @Query("SELECT * FROM accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getAccountBySyncId(syncId: String): Account?

    // --- Account Balances ---
    @Query("SELECT * FROM account_balances WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingAccountBalances(): List<AccountBalance>
    @Query("UPDATE account_balances SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markAccountBalanceSynced(syncId: String)
    @Query("SELECT * FROM account_balances WHERE syncId = :syncId LIMIT 1")
    suspend fun getAccountBalanceBySyncId(syncId: String): AccountBalance?

    // --- Journal Entries ---
    @Query("SELECT * FROM journal_entries WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingJournalEntries(): List<JournalEntry>
    @Query("UPDATE journal_entries SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markJournalEntrySynced(syncId: String)
    @Query("SELECT * FROM journal_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun getJournalEntryBySyncId(syncId: String): JournalEntry?

    // --- Journal Entry Lines ---
    @Query("SELECT * FROM journal_entry_lines WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingJournalEntryLines(): List<JournalEntryLine>
    @Query("UPDATE journal_entry_lines SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markJournalEntryLineSynced(syncId: String)
    @Query("SELECT * FROM journal_entry_lines WHERE syncId = :syncId LIMIT 1")
    suspend fun getJournalEntryLineBySyncId(syncId: String): JournalEntryLine?

    // --- Cash Transactions ---
    @Query("SELECT * FROM cash_transactions WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingCashTransactions(): List<CashTransaction>
    @Query("UPDATE cash_transactions SET syncState = 'SYNCED' WHERE syncId = :syncId")
    suspend fun markCashTransactionSynced(syncId: String)
    @Query("SELECT * FROM cash_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getCashTransactionBySyncId(syncId: String): CashTransaction?

    // --- Enterprise Settings ---
    @Query("SELECT * FROM enterprise_settings WHERE syncState IN ('PENDING_ADD', 'PENDING_UPDATE')")
    suspend fun getPendingEnterpriseSettings(): List<EnterpriseSetting>
    @Query("UPDATE enterprise_settings SET syncState = 'SYNCED' WHERE id = 1")
    suspend fun markEnterpriseSettingSynced()
    @Query("SELECT * FROM enterprise_settings WHERE id = 1 LIMIT 1")
    suspend fun getEnterpriseSettingDirect(): EnterpriseSetting?
}
