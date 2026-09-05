package com.example.api

import com.example.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface mapping to Supabase PostgREST endpoints.
 */
interface SupabaseApiService {

    // --- Auth ---
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: AuthRequestDto,
        @Header("apikey") apiKey: String
    ): Response<AuthResponseDto>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Body request: AuthRequestDto,
        @Header("apikey") apiKey: String
    ): Response<AuthResponseDto>

    // --- Companies ---
    @GET("rest/v1/companies")
    suspend fun getCompanies(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<CompanyDto>>

    @GET("rest/v1/companies")
    suspend fun getCompany(
        @Query("company_id") companyId: String,
        @Query("select") select: String = "*",
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<CompanyDto>>

    @POST("rest/v1/companies")
    suspend fun createCompany(
        @Body company: CompanyDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Users ---
    @GET("rest/v1/users")
    suspend fun getUsers(
        @Query("company_id") companyId: String? = null,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("username") username: String? = null
    ): Response<List<UserDto>>

    @POST("rest/v1/users")
    suspend fun upsertUser(
        @Body user: UserDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE("rest/v1/users")
    suspend fun deleteUser(
        @Query("syncid") syncId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<Unit>
    
    // --- Items ---
    @GET("rest/v1/items")
    suspend fun getItems(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<ItemDto>>

    @POST("rest/v1/items")
    suspend fun upsertItem(
        @Body item: ItemDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE("rest/v1/items")
    suspend fun deleteItem(
        @Query("syncid") syncId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<Unit>

    // --- Item Units ---
    @GET("rest/v1/item_units")
    suspend fun getItemUnits(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<ItemUnitDto>>

    @POST("rest/v1/item_units")
    suspend fun upsertItemUnit(
        @Body itemUnit: ItemUnitDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Contacts ---
    @GET("rest/v1/contacts")
    suspend fun getContacts(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<ContactDto>>

    @POST("rest/v1/contacts")
    suspend fun upsertContact(
        @Body contact: ContactDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE("rest/v1/contacts")
    suspend fun deleteContact(
        @Query("syncid") syncId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<Unit>

    // --- Invoices ---
    @GET("rest/v1/invoices")
    suspend fun getInvoices(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<InvoiceDto>>

    @POST("rest/v1/invoices")
    suspend fun upsertInvoice(
        @Body invoice: InvoiceDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE("rest/v1/invoices")
    suspend fun deleteInvoice(
        @Query("syncid") syncId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<Unit>

    // --- Invoice Items ---
    @GET("rest/v1/invoice_items")
    suspend fun getInvoiceItems(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<InvoiceItemDto>>

    @POST("rest/v1/invoice_items")
    suspend fun upsertInvoiceItem(
        @Body invoiceItem: InvoiceItemDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Accounts ---
    @GET("rest/v1/accounts")
    suspend fun getAccounts(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<AccountDto>>

    @POST("rest/v1/accounts")
    suspend fun upsertAccount(
        @Body account: AccountDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>
    
    // --- Journal Entries ---
    @GET("rest/v1/journal_entries")
    suspend fun getJournalEntries(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<JournalEntryDto>>

    @POST("rest/v1/journal_entries")
    suspend fun upsertJournalEntry(
        @Body entry: JournalEntryDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Journal Entry Lines ---
    @GET("rest/v1/journal_entry_lines")
    suspend fun getJournalEntryLines(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<JournalEntryLineDto>>

    @POST("rest/v1/journal_entry_lines")
    suspend fun upsertJournalEntryLine(
        @Body line: JournalEntryLineDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Cash Transactions ---
    @GET("rest/v1/cash_transactions")
    suspend fun getCashTransactions(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<CashTransactionDto>>

    @POST("rest/v1/cash_transactions")
    suspend fun upsertCashTransaction(
        @Body tx: CashTransactionDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    // --- Enterprise Settings ---
    @GET("rest/v1/enterprise_settings")
    suspend fun getEnterpriseSettings(
        @Query("company_id") companyId: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String
    ): Response<List<EnterpriseSettingDto>>

    @POST("rest/v1/enterprise_settings")
    suspend fun upsertEnterpriseSetting(
        @Body setting: EnterpriseSettingDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>
}
