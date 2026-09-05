package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val isSuspended: Boolean = false,
    val lastLogin: Long = 0,
    val operationCount: Int = 0,
    // Permissions
    val permSale: Boolean = true,
    val permPurchase: Boolean = true,
    val permDeleteInvoice: Boolean = true,
    val permEditInvoice: Boolean = true,
    val permViewProfits: Boolean = true,
    val permViewReports: Boolean = true,
    val permEditPrices: Boolean = true,
    val permBackup: Boolean = true,
    val permSettings: Boolean = true,
    val permAI: Boolean = true,
    val permAccountStatement: Boolean = true,
    val permStocktake: Boolean = true,
    val permPrint: Boolean = true,
    val permShare: Boolean = true,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val code: String,
    val barcode: String,
    val name: String,
    val category: String = "",
    val unit: String = "حبة",
    val brand: String = "",
    val color: String = "",
    val size: String = "",
    val location: String = "",
    val minLimit: Double = 0.0,
    val maxLimit: Double = 9999.0,
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val specialPrice: Double = 0.0,
    val currentQuantity: Double = 0.0,
    val notes: String = "",
    val imagePath: String? = null,
    val expiryDate: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "item_units")
data class ItemUnit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val itemId: Long,
    val unitName: String,
    val conversionFactor: Double,
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val barcode: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "warehouses")
data class Warehouse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val location: String = "",
    val notes: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "remittances")
data class Remittance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val type: String,
    val accountId: Long,
    val amount: Double,
    val currencyCode: String,
    val commissionAmount: Double,
    val commissionCurrency: String,
    val senderName: String,
    val receiverName: String,
    val transferCompany: String,
    val transferNumber: String,
    val notes: String = "",
    val safeAccountId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "currency_exchanges")
data class CurrencyExchange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val safeAccountId: Long,
    val fromCurrency: String,
    val fromAmount: Double,
    val fromExchangeRate: Double,
    val toCurrency: String,
    val toAmount: Double,
    val toExchangeRate: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "stock_transfers")
data class StockTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val itemId: Long,
    val fromWarehouseId: Long,
    val toWarehouseId: Long,
    val quantity: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val type: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val balance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val notes: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val type: String,
    val contactId: Long?,
    val timestamp: Long = System.currentTimeMillis(),
    val subTotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val paymentMethod: String = "نقدي",
    val notes: String = "",
    val userId: Long,
    val currencyCode: String = "ر.ي",
    val exchangeRate: Double = 1.0,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "invoice_items")
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val invoiceId: Long,
    val itemId: Long,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double = 0.0,
    val total: Double,
    val unitName: String = "حبة",
    val conversionFactor: Double = 1.0,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val type: String,
    val parentId: Long? = null,
    val balance: Double = 0.0,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "account_balances",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("accountId")]
)
data class AccountBalance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val accountId: Long,
    val currencyCode: String,
    val balance: Double,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class AccountCurrencyBalance(
    val accountId: Long,
    val code: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val balance: Double
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val entryNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val isPosted: Boolean = true,
    val referenceId: Long? = null,
    val referenceType: String? = null,
    val currencyCode: String = "ر.ي",
    val exchangeRate: Double = 1.0,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "journal_entry_lines")
data class JournalEntryLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val journalEntryId: Long,
    val accountId: Long,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val description: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "cash_transactions")
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val type: String,
    val accountId: Long,
    val counterpartAccountId: Long? = null,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val mainAccountNotes: String = "",
    val counterpartAccountNotes: String = "",
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val currencyCode: String = "ر.ي",
    val exchangeRate: Double = 1.0,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "bank_transactions")
data class BankTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val type: String,
    val bankAccount: String = "",
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val userId: Long,
    val username: String,
    val operationType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceName: String = "أندرويد",
    val tableName: String = "",
    val details: String = "",
    val originalData: String = "",
    val updatedData: String = "",
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "enterprise_settings")
data class EnterpriseSetting(
    @PrimaryKey val id: Int = 1,
    val name: String = "آفاق محاسب",
    val activity: String = "تجارة عامة",
    val address: String = "الرياض، المملكة العربية السعودية",
    val phone: String = "0112345678",
    val whatsapp: String = "966500000000",
    val email: String = "info@afaq.com",
    val website: String = "www.afaq.com",
    val taxId: String = "123456789012345",
    val crId: String = "1010101010",
    val currency: String = "ر.ي",
    val decimalPlaces: Int = 2,
    val invoiceFooter: String = "شكراً لتعاملكم معنا!",
    val allowSellBelowCost: Boolean = false,
    val allowNegativeStock: Boolean = false,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class DailySum(
    val dayDate: String,
    val total: Double
)

@Entity(tableName = "currencies")
data class Currency(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val symbol: String = "",
    val exchangeRate: Double = 1.0,
    val isDefault: Boolean = false,
    
    val syncState: String = "PENDING_ADD",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

fun getArabicAccountType(type: String): String {
    return when (type) {
        "ASSETS" -> "أصول"
        "LIABILITIES" -> "خصوم"
        "EQUITY" -> "حقوق ملكية"
        "REVENUE" -> "إيرادات"
        "EXPENSES" -> "مصروفات"
        else -> type
    }
}
