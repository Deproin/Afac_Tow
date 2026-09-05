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
        AccountBalance::class
    ],
    version = 10,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun itemUnitDao(): ItemUnitDao
    abstract fun warehouseDao(): WarehouseDao
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
                    MIGRATION_1_10
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

