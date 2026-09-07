package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cash_transactions ADD COLUMN mainAccountNotes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cash_transactions ADD COLUMN counterpartAccountNotes TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
        db.execSQL("ALTER TABLE invoices ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
        
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
        
        db.execSQL("ALTER TABLE cash_transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
        db.execSQL("ALTER TABLE cash_transactions ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `remittances` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `type` TEXT NOT NULL, 
                `accountId` INTEGER NOT NULL, 
                `amount` REAL NOT NULL, 
                `currencyCode` TEXT NOT NULL, 
                `commissionAmount` REAL NOT NULL, 
                `commissionCurrency` TEXT NOT NULL, 
                `senderName` TEXT NOT NULL, 
                `receiverName` TEXT NOT NULL, 
                `transferCompany` TEXT NOT NULL, 
                `transferNumber` TEXT NOT NULL, 
                `notes` TEXT NOT NULL, 
                `safeAccountId` INTEGER NOT NULL, 
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `currency_exchanges` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `safeAccountId` INTEGER NOT NULL, 
                `fromCurrency` TEXT NOT NULL, 
                `fromAmount` REAL NOT NULL, 
                `fromExchangeRate` REAL NOT NULL, 
                `toCurrency` TEXT NOT NULL, 
                `toAmount` REAL NOT NULL, 
                `toExchangeRate` REAL NOT NULL, 
                `notes` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `account_balances` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `accountId` INTEGER NOT NULL, 
                `currencyCode` TEXT NOT NULL, 
                `balance` REAL NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_balances_accountId` ON `account_balances` (`accountId`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE users ADD COLUMN defaultWarehouseId INTEGER DEFAULT NULL")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE users ADD COLUMN defaultSafeAccountId INTEGER DEFAULT NULL")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE journal_entry_lines ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
            db.execSQL("ALTER TABLE journal_entry_lines ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
        } catch (e: Exception) {}
    }
}



val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `item_stocks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `syncId` TEXT NOT NULL, 
                `itemId` INTEGER NOT NULL, 
                `warehouseId` INTEGER NOT NULL, 
                `quantity` REAL NOT NULL, 
                `syncState` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                `isDeleted` INTEGER NOT NULL,
                FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`warehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_stocks_itemId` ON `item_stocks` (`itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_stocks_warehouseId` ON `item_stocks` (`warehouseId`)")
        
        try {
            db.execSQL("ALTER TABLE invoices ADD COLUMN warehouseId INTEGER DEFAULT NULL")
        } catch (e: Exception) {}
        
        // Seed initial data: move existing quantities to main warehouse (ID 1)
        db.execSQL("""
            INSERT INTO item_stocks (syncId, itemId, warehouseId, quantity, syncState, updatedAt, isDeleted)
            SELECT lower(hex(randomblob(16))), id, 1, currentQuantity, 'PENDING_ADD', strftime('%s','now') * 1000, 0
            FROM items WHERE currentQuantity > 0
        """.trimIndent())
    }
}

val MIGRATION_1_10 = object : Migration(1, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safe migration for any legacy db version 1 to 10
        try {
            db.execSQL("ALTER TABLE cash_transactions ADD COLUMN mainAccountNotes TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE cash_transactions ADD COLUMN counterpartAccountNotes TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE invoices ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
            db.execSQL("ALTER TABLE invoices ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE cash_transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
            db.execSQL("ALTER TABLE cash_transactions ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
        } catch (e: Exception) {}
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `remittances` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `type` TEXT NOT NULL, 
                    `accountId` INTEGER NOT NULL, 
                    `amount` REAL NOT NULL, 
                    `currencyCode` TEXT NOT NULL, 
                    `commissionAmount` REAL NOT NULL, 
                    `commissionCurrency` TEXT NOT NULL, 
                    `senderName` TEXT NOT NULL, 
                    `receiverName` TEXT NOT NULL, 
                    `transferCompany` TEXT NOT NULL, 
                    `transferNumber` TEXT NOT NULL, 
                    `notes` TEXT NOT NULL, 
                    `safeAccountId` INTEGER NOT NULL, 
                    `timestamp` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `currency_exchanges` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `safeAccountId` INTEGER NOT NULL, 
                    `fromCurrency` TEXT NOT NULL, 
                    `fromAmount` REAL NOT NULL, 
                    `fromExchangeRate` REAL NOT NULL, 
                    `toCurrency` TEXT NOT NULL, 
                    `toAmount` REAL NOT NULL, 
                    `toExchangeRate` REAL NOT NULL, 
                    `notes` TEXT NOT NULL, 
                    `timestamp` INTEGER NOT NULL
                )
            """.trimIndent())
        } catch (e: Exception) {}
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `account_balances` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `accountId` INTEGER NOT NULL, 
                    `currencyCode` TEXT NOT NULL, 
                    `balance` REAL NOT NULL,
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_balances_accountId` ON `account_balances` (`accountId`)")
        } catch (e: Exception) {}
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `partners` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `syncId` TEXT NOT NULL, 
                `name` TEXT NOT NULL, 
                `percentage` REAL NOT NULL, 
                `capitalAccountId` INTEGER, 
                `currentAccountId` INTEGER, 
                `notes` TEXT NOT NULL, 
                `syncState` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                `isDeleted` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE journal_entry_lines ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'ر.ي'")
        db.execSQL("ALTER TABLE journal_entry_lines ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
    }
}

@Database(
    entities = [
        User::class,
        Item::class,
        ItemUnit::class,
        Warehouse::class,
        StockTransfer::class,
        Contact::class,
        Invoice::class,
        InvoiceItem::class,
        Account::class,
        JournalEntry::class,
        JournalEntryLine::class,
        CashTransaction::class,
        BankTransaction::class,
        AuditLog::class,
        EnterpriseSetting::class,
        Currency::class,
        Remittance::class,
        CurrencyExchange::class,
        AccountBalance::class,
        ItemStock::class,
        Partner::class
    ],
    version = 14,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun itemUnitDao(): ItemUnitDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun itemStockDao(): ItemStockDao
    abstract fun stockTransferDao(): StockTransferDao
    abstract fun contactDao(): ContactDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun accountDao(): AccountDao
    abstract fun journalDao(): JournalDao
    abstract fun cashTransactionDao(): CashTransactionDao
    abstract fun bankTransactionDao(): BankTransactionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun enterpriseSettingDao(): EnterpriseSettingDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun remittanceDao(): RemittanceDao
    abstract fun currencyExchangeDao(): CurrencyExchangeDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun syncDao(): SyncDao
    abstract fun partnerDao(): PartnerDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "afaq_accounting_db"
                )
                .addMigrations(
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_1_10
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

