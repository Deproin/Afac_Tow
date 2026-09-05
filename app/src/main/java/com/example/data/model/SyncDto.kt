package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTOs) for syncing with Supabase.
 * Field names map to PostgreSQL unquoted lowercase column names in PostgREST.
 */

@JsonClass(generateAdapter = true)
data class CompanyDto(
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "created_at") val createdAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "passwordhash") val passwordHash: String = "",
    @Json(name = "issuspended") val isSuspended: Boolean = false,
    @Json(name = "lastlogin") val lastLogin: Long = 0,
    @Json(name = "operationcount") val operationCount: Int = 0,
    @Json(name = "permsale") val permSale: Boolean = true,
    @Json(name = "permpurchase") val permPurchase: Boolean = true,
    @Json(name = "permdeleteinvoice") val permDeleteInvoice: Boolean = true,
    @Json(name = "permeditinvoice") val permEditInvoice: Boolean = true,
    @Json(name = "permviewprofits") val permViewProfits: Boolean = true,
    @Json(name = "permviewreports") val permViewReports: Boolean = true,
    @Json(name = "permeditprices") val permEditPrices: Boolean = true,
    @Json(name = "permbackup") val permBackup: Boolean = true,
    @Json(name = "permsettings") val permSettings: Boolean = true,
    @Json(name = "permai") val permAI: Boolean = true,
    @Json(name = "permaccountstatement") val permAccountStatement: Boolean = true,
    @Json(name = "permstocktake") val permStocktake: Boolean = true,
    @Json(name = "permprint") val permPrint: Boolean = true,
    @Json(name = "permshare") val permShare: Boolean = true,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ItemDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "code") val code: String = "",
    @Json(name = "barcode") val barcode: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "category") val category: String = "",
    @Json(name = "unit") val unit: String = "",
    @Json(name = "brand") val brand: String = "",
    @Json(name = "color") val color: String = "",
    @Json(name = "size") val size: String = "",
    @Json(name = "location") val location: String = "",
    @Json(name = "minlimit") val minLimit: Double = 0.0,
    @Json(name = "maxlimit") val maxLimit: Double = 9999.0,
    @Json(name = "purchaseprice") val purchasePrice: Double = 0.0,
    @Json(name = "saleprice") val salePrice: Double = 0.0,
    @Json(name = "wholesaleprice") val wholesalePrice: Double = 0.0,
    @Json(name = "specialprice") val specialPrice: Double = 0.0,
    @Json(name = "currentquantity") val currentQuantity: Double = 0.0,
    @Json(name = "notes") val notes: String = "",
    @Json(name = "imagepath") val imagePath: String? = null,
    @Json(name = "expirydate") val expiryDate: String = "",
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ItemUnitDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "itemsyncid") val itemSyncId: String? = null,
    @Json(name = "unitname") val unitName: String = "",
    @Json(name = "conversionfactor") val conversionFactor: Double = 1.0,
    @Json(name = "purchaseprice") val purchasePrice: Double = 0.0,
    @Json(name = "saleprice") val salePrice: Double = 0.0,
    @Json(name = "barcode") val barcode: String = "",
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ContactDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "address") val address: String = "",
    @Json(name = "balance") val balance: Double = 0.0,
    @Json(name = "creditlimit") val creditLimit: Double = 0.0,
    @Json(name = "notes") val notes: String = "",
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class InvoiceDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "invoicenumber") val invoiceNumber: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "contactsyncid") val contactSyncId: String? = null,
    @Json(name = "timestamp") val timestamp: Long = 0,
    @Json(name = "subtotal") val subTotal: Double = 0.0,
    @Json(name = "discount") val discount: Double = 0.0,
    @Json(name = "tax") val tax: Double = 0.0,
    @Json(name = "total") val total: Double = 0.0,
    @Json(name = "paidamount") val paidAmount: Double = 0.0,
    @Json(name = "remainingamount") val remainingAmount: Double = 0.0,
    @Json(name = "paymentmethod") val paymentMethod: String = "",
    @Json(name = "notes") val notes: String = "",
    @Json(name = "usersyncid") val userSyncId: String? = null,
    @Json(name = "currencycode") val currencyCode: String = "ر.ي",
    @Json(name = "exchangerate") val exchangeRate: Double = 1.0,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class InvoiceItemDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "invoicesyncid") val invoiceSyncId: String? = null,
    @Json(name = "itemsyncid") val itemSyncId: String? = null,
    @Json(name = "quantity") val quantity: Double = 0.0,
    @Json(name = "unitprice") val unitPrice: Double = 0.0,
    @Json(name = "discount") val discount: Double = 0.0,
    @Json(name = "total") val total: Double = 0.0,
    @Json(name = "unitname") val unitName: String = "",
    @Json(name = "conversionfactor") val conversionFactor: Double = 1.0,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AccountDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "code") val code: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "parentsyncid") val parentSyncId: String? = null,
    @Json(name = "balance") val balance: Double = 0.0,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class JournalEntryDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "entrynumber") val entryNumber: String = "",
    @Json(name = "timestamp") val timestamp: Long = 0,
    @Json(name = "description") val description: String = "",
    @Json(name = "isposted") val isPosted: Boolean = false,
    @Json(name = "referencesyncid") val referenceSyncId: String? = null,
    @Json(name = "referencetype") val referenceType: String? = null,
    @Json(name = "currencycode") val currencyCode: String = "ر.ي",
    @Json(name = "exchangerate") val exchangeRate: Double = 1.0,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class JournalEntryLineDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "journalentrysyncid") val journalEntrySyncId: String? = null,
    @Json(name = "accountsyncid") val accountSyncId: String? = null,
    @Json(name = "debit") val debit: Double = 0.0,
    @Json(name = "credit") val credit: Double = 0.0,
    @Json(name = "description") val description: String = "",
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CashTransactionDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "accountsyncid") val accountSyncId: String? = null,
    @Json(name = "counterpartaccountsyncid") val counterpartAccountSyncId: String? = null,
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "timestamp") val timestamp: Long = 0,
    @Json(name = "notes") val notes: String = "",
    @Json(name = "mainaccountnotes") val mainAccountNotes: String = "",
    @Json(name = "counterpartaccountnotes") val counterpartAccountNotes: String = "",
    @Json(name = "referencetype") val referenceType: String? = null,
    @Json(name = "referencesyncid") val referenceSyncId: String? = null,
    @Json(name = "currencycode") val currencyCode: String = "ر.ي",
    @Json(name = "exchangerate") val exchangeRate: Double = 1.0,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EnterpriseSettingDto(
    @Json(name = "syncid") val syncId: String = "",
    @Json(name = "company_id") val companyId: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "activity") val activity: String = "",
    @Json(name = "address") val address: String = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "whatsapp") val whatsapp: String = "",
    @Json(name = "email") val email: String = "",
    @Json(name = "website") val website: String = "",
    @Json(name = "taxid") val taxId: String = "",
    @Json(name = "crid") val crId: String = "",
    @Json(name = "currency") val currency: String = "",
    @Json(name = "decimalplaces") val decimalPlaces: Int = 2,
    @Json(name = "invoicefooter") val invoiceFooter: String = "",
    @Json(name = "allowsellbelowcost") val allowSellBelowCost: Boolean = false,
    @Json(name = "allownegativestock") val allowNegativeStock: Boolean = false,
    @Json(name = "syncstate") val syncState: String = "SYNCED",
    @Json(name = "updatedat") val updatedAt: Long = 0,
    @Json(name = "isdeleted") val isDeleted: Boolean = false
)
