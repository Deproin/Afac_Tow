package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.util.PhoneUtils
import com.example.ui.viewmodel.AppViewModel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JournalLineInput(
    val accountId: Long? = null,
    val debit: String = "",
    val credit: String = "",
    val description: String = ""
)

fun printVoucher(context: Context, htmlContent: String, txId: Long) {
    try {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = view.createPrintDocumentAdapter("Voucher_$txId")
                    printManager.print("Voucher_$txId", printAdapter, PrintAttributes.Builder().build())
                } catch (e: Exception) {
                    Toast.makeText(context, "فشلت الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        Toast.makeText(context, "فشلت تهيئة الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun AccountTreeItem(acc: Account) {
    val isHeader = acc.code.length <= 2
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ((acc.code.length - 1) * 8).dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(acc.name, fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium, fontSize = if (isHeader) 15.sp else 13.sp)
                Text("الحساب: ${acc.code} | النوع: ${getArabicAccountType(acc.type)}", fontSize = 10.sp, color = Color.Gray)
            }
            if (!isHeader) {
                Text("${acc.balance} ر.ي", fontWeight = FontWeight.Bold, color = if (acc.balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: JournalEntry, accounts: List<Account>, viewModel: AppViewModel) {
    var lines by remember { mutableStateOf<List<JournalEntryLine>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editDescription by remember(entry) { mutableStateOf(entry.description) }

    LaunchedEffect(entry.id) {
        lines = viewModel.repository.journalDao.getLinesForEntry(entry.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry.referenceType == "OPENING" || entry.description.contains("افتتاحي")) "قيد افتتاحي #${entry.id}" else "قيد يومية #${entry.id}",
                    fontWeight = FontWeight.Bold,
                    color = if (entry.referenceType == "OPENING" || entry.description.contains("افتتاحي")) Color(0xFF00796B) else MaterialTheme.colorScheme.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الرقم: ${entry.entryNumber}", fontSize = 12.sp, color = Color.Gray)
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل القيد", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.deleteJournalEntry(entry) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف القيد", tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text("الوصف: ${entry.description}", fontSize = 13.sp)
            Text("تاريخ الترحيل: ${formatTime(entry.timestamp)}", fontSize = 11.sp, color = Color.Gray)

            if (lines.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text("تفاصيل الحسابات والأطراف المتأثرة (القيد المزدوج):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                lines.forEach { line ->
                    val accountName = accounts.firstOrNull { it.id == line.accountId }?.name ?: "حساب #${line.accountId}"
                    val accountCode = accounts.firstOrNull { it.id == line.accountId }?.code ?: ""
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$accountName ($accountCode)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.5f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (line.debit > 0) {
                                Text("مدين: ${line.debit} ر.ي", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (line.credit > 0) {
                                Text("دائن: ${line.credit} ر.ي", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (line.description.isNotEmpty()) {
                        Text(
                            text = "  ← ${line.description}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("تعديل وصف وتفاصيل القيد #${entry.id}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("وصف القيد رقم ${entry.entryNumber}:", fontSize = 12.sp, color = Color.Gray)
                            OutlinedTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                label = { Text("البيان / شرح القيد المحاسبي *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (editDescription.trim().isNotEmpty()) {
                                    viewModel.updateJournalEntry(entry.copy(description = editDescription.trim()), lines)
                                    showEditDialog = false
                                }
                            }
                        ) {
                            Text("حفظ التعديل ✏️", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) { Text("إلغاء") }
                    }
                )
            }
        }
    }
}

@Composable
fun VoucherListItem(tx: CashTransaction, accounts: List<Account>, settings: EnterpriseSetting, viewModel: AppViewModel) {
    val context = LocalContext.current
    val isReceipt = tx.type == "RECEIPT"
    val mainAcc = accounts.firstOrNull { it.id == tx.accountId }
    val counterpartAcc = accounts.firstOrNull { it.id == tx.counterpartAccountId }

    var showEditDialog by remember { mutableStateOf(false) }
    var editNotes by remember(tx) { mutableStateOf(tx.notes) }
    var editMainNotes by remember(tx) { mutableStateOf(tx.mainAccountNotes) }
    var editCounterpartNotes by remember(tx) { mutableStateOf(tx.counterpartAccountNotes) }
    var editAmount by remember(tx) { mutableStateOf(tx.amount.toString()) }
    
    var editCurrency by remember(tx) {
        val mappedCurrency = when (tx.currencyCode) {
            "دولار" -> "دولار أمريكي (\$)"
            "SAR" -> "ريال سعودي (SAR)"
            "ريال" -> "ريال سعودي (SAR)"
            else -> "ر.ي (العملة المحلية)"
        }
        mutableStateOf(mappedCurrency)
    }
    var editExchangeRate by remember(tx) { mutableStateOf(tx.exchangeRate.toString()) }

    val dateString = remember(tx.id) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.timestamp))
    }

    val mainNotesHtml = if (tx.mainAccountNotes.isNotEmpty()) "<br><small style='color:#555;'>بيان: ${tx.mainAccountNotes}</small>" else ""
    val counterpartNotesHtml = if (tx.counterpartAccountNotes.isNotEmpty()) "<br><small style='color:#555;'>بيان: ${tx.counterpartAccountNotes}</small>" else ""
    val generalNotesText = if (tx.notes.isNotEmpty()) tx.notes else "سند مالي رسمي"

    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val counterpartContact = remember(tx.referenceId, contacts) {
        if (tx.referenceType == "CONTACT") contacts.find { it.id == tx.referenceId } else null
    }
    val voucherShareText = remember(tx, counterpartContact, dateString) {
        "إشعار سند ${if (isReceipt) "قبض" else "صرف"} مالي رقم #${tx.id}\nالمبلغ: ${tx.amount} ${tx.currencyCode}\nالتاريخ: $dateString\nالبيان: $generalNotesText"
    }

    val voucherHtml = remember(tx, mainAcc, counterpartAcc) {
        val title = if (isReceipt) "سند قبض مالي (استلام)" else "سند صرف مالي (دفعة)"
        """
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
                .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
                .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
                .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
                .voucher-box { border: 2px dashed #0061A4; padding: 16px; border-radius: 10px; margin-bottom: 20px; }
                .info-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                .info-table td { padding: 8px; font-size: 14px; }
                .amount-badge { font-size: 20px; font-weight: bold; color: #2E7D32; background: #e8f5e9; padding: 8px 16px; border-radius: 8px; display: inline-block; }
                .footer { text-align: center; margin-top: 40px; font-size: 12px; color: #777; border-top: 1px solid #eee; padding-top: 10px; }
            </style>
        </head>
        <body>
            <div class="header">
                <div class="company-name">${settings.name}</div>
                <div>الرقم الضريبي: <strong>${settings.taxId}</strong> | ${settings.address}</div>
                <div class="title" style="margin-top: 10px;">$title</div>
            </div>

            <div class="voucher-box">
                <table class="info-table">
                    <tr>
                        <td><strong>رقم السند:</strong> VOUCHER-#${tx.id}</td>
                        <td style="text-align: left;"><strong>تاريخ السند:</strong> $dateString</td>
                    </tr>
                    <tr>
                        <td><strong>المبلغ المحصل/المصروف:</strong></td>
                        <td style="text-align: left;"><span class="amount-badge">${tx.amount} ر.ي</span></td>
                    </tr>
                    <tr>
                        <td><strong>حساب الخزينة/البنك (طرف 1):</strong></td>
                        <td style="text-align: left;">${mainAcc?.name ?: "الصندوق الرئيسي"} (${mainAcc?.code ?: "1101"}) $mainNotesHtml</td>
                    </tr>
                    <tr>
                        <td><strong>الحساب المقابل (طرف 2):</strong></td>
                        <td style="text-align: left;">${counterpartAcc?.name ?: "حساب عام"} (${counterpartAcc?.code ?: ""}) $counterpartNotesHtml</td>
                    </tr>
                    <tr>
                        <td colspan="2"><strong>البيان العام والشامل:</strong> $generalNotesText</td>
                    </tr>
                </table>
            </div>

            <table style="width: 100%; margin-top: 40px; text-align: center; font-size: 13px;">
                <tr>
                    <td><strong>توقيع المستلم / العميل</strong><br><br>.........................</td>
                    <td><strong>توقيع أمين الصندوق / المحاسب</strong><br><br>.........................</td>
                </tr>
            </table>

            <div class="footer">
                <p>${settings.invoiceFooter}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (isReceipt) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isReceipt) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = if (isReceipt) "سند قبض مالي 🟢" else "سند صرف مالي 🔴",
                        fontWeight = FontWeight.Bold,
                        color = if (isReceipt) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${tx.amount} ر.ي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    
                    IconButton(onClick = {
                        try {
                            val formattedWa = PhoneUtils.formatForWhatsApp(counterpartContact?.phone)
                            val phoneParam = if (formattedWa.isNotEmpty()) "phone=${formattedWa}&" else ""
                            val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?${phoneParam}text=${Uri.encode(voucherShareText)}"))
                            context.startActivity(whatsappIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                    }
                    
                    IconButton(onClick = {
                        try {
                            val formattedSms = PhoneUtils.formatForSms(counterpartContact?.phone)
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:$formattedSms")
                                putExtra("sms_body", voucherShareText)
                            }
                            context.startActivity(smsIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "فشل فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Sms, contentDescription = "رسالة SMS", tint = Color(0xFF00ACC1), modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = { printVoucher(context, voucherHtml, tx.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة السند", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل السند", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.deleteCashTransaction(tx) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف السند", tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("حساب الخزينة/البنك:", fontSize = 11.sp, color = Color.Gray)
                    Text(mainAcc?.let { "${it.name} (${it.code})" } ?: "الصندوق الرئيسي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (tx.mainAccountNotes.isNotEmpty()) {
                    Text("  ← بيان الخزينة: ${tx.mainAccountNotes}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الحساب المقابل:", fontSize = 11.sp, color = Color.Gray)
                    Text(counterpartAcc?.let { "${it.name} (${it.code})" } ?: "حساب آخر", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (tx.counterpartAccountNotes.isNotEmpty()) {
                    Text("  ← بيان المقابل: ${tx.counterpartAccountNotes}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (tx.notes.isNotEmpty()) {
                Text("البيان العام: ${tx.notes}", fontSize = 11.sp, color = Color.DarkGray)
            }
            Text(dateString, fontSize = 10.sp, color = Color.Gray)

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("تعديل بيانات وبيانات طرفي السند #${tx.id}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Currency Selection
                            Text("العملة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val currencies = listOf("ر.ي (العملة المحلية)", "دولار أمريكي (\$)", "ريال سعودي (SAR)")
                                for (currency in currencies) {
                                    FilterChip(
                                        selected = editCurrency == currency,
                                        onClick = {
                                            editCurrency = currency
                                            editExchangeRate = if (currency.startsWith("ر.ي")) "1.0" else ""
                                        },
                                        label = { Text(currency.substringBefore(" "), fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (!editCurrency.startsWith("ر.ي")) {
                                OutlinedTextField(
                                    value = editExchangeRate,
                                    onValueChange = { editExchangeRate = it },
                                    label = { Text("سعر الصرف للعملة المحلية *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                label = { Text("المبلغ (${editCurrency.substringBefore(" ")}) *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = editMainNotes,
                                onValueChange = { editMainNotes = it },
                                label = { Text("بيان طرف الخزينة / البنك (الطرف الأول)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editCounterpartNotes,
                                onValueChange = { editCounterpartNotes = it },
                                label = { Text("بيان الطرف المقابل (الطرف الثاني)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("البيان العام الشامل بالسند") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parsedAmt = editAmount.toDoubleOrNull() ?: tx.amount
                                viewModel.updateCashTransaction(
                                    tx.copy(
                                        amount = parsedAmt,
                                        notes = editNotes.trim(),
                                        mainAccountNotes = editMainNotes.trim(),
                                        counterpartAccountNotes = editCounterpartNotes.trim(),
                                        currencyCode = editCurrency.substringBefore(" "),
                                        exchangeRate = editExchangeRate.toDoubleOrNull() ?: 1.0
                                    )
                                )
                                showEditDialog = false
                            }
                        ) {
                            Text("حفظ التعديل ✏️", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) { Text("إلغاء") }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(viewModel: AppViewModel, initialTab: Int = 0, onBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val journalEntries by viewModel.journalEntries.collectAsState()
    val cashTransactions by viewModel.cashTransactions.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("دليل الحسابات", "القيود اليومية", "سندات القبض والصرف")

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddVoucherDialog by remember { mutableStateOf(false) }
    var showAddJournalEntryDialog by remember { mutableStateOf(false) }

    // Add Account fields
    var accCode by remember { mutableStateOf("") }
    var accName by remember { mutableStateOf("") }
    var accType by remember { mutableStateOf("ASSETS") }

    // Add Cash Voucher fields (Double Entry Account selection)
    var voucherType by remember { mutableStateOf("RECEIPT") } // "RECEIPT" or "PAYMENT"
    var voucherAmount by remember { mutableStateOf("") }
    var voucherNotes by remember { mutableStateOf("") }
    var voucherMainAccNotes by remember { mutableStateOf("") }
    var voucherCounterpartAccNotes by remember { mutableStateOf("") }

    var voucherSelectedCurrency by remember { mutableStateOf("ر.ي (العملة المحلية)") }
    var voucherExchangeRateStr by remember { mutableStateOf("1.0") }
    val isVoucherLocalCurrency = voucherSelectedCurrency.startsWith("ر.ي")

    val cashAndBankAccounts = remember(accounts) {
        accounts.filter {
            it.type == "ASSETS" && (it.name.contains("الصندوق") || it.name.contains("البنك") || it.name.contains("خزينة") || it.code.startsWith("11"))
        }
    }
    var selectedMainCashAcc by remember { mutableStateOf<Account?>(null) }
    var selectedCounterpartAcc by remember { mutableStateOf<Account?>(null) }
    var selectedCounterpartContact by remember { mutableStateOf<Contact?>(null) }
    var voucherError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cashAndBankAccounts, currentUser) {
        if (cashAndBankAccounts.isNotEmpty()) {
            if (currentUser?.defaultSafeAccountId != null) {
                selectedMainCashAcc = cashAndBankAccounts.find { it.id == currentUser?.defaultSafeAccountId }
            } else if (selectedMainCashAcc == null) {
                selectedMainCashAcc = cashAndBankAccounts.firstOrNull { it.name.contains("الصندوق") } ?: cashAndBankAccounts.firstOrNull()
            }
        }
    }

    // Check Pending Journal Entry & Cash Voucher Edit Requests from ViewModel
    LaunchedEffect(viewModel.pendingEditingJournalEntry) {
        if (viewModel.pendingEditingJournalEntry != null) {
            viewModel.pendingEditingJournalEntry = null
            selectedTab = 1
            showAddJournalEntryDialog = true
        }
    }

    LaunchedEffect(viewModel.pendingEditingCashTx) {
        val pendingTx = viewModel.pendingEditingCashTx
        if (pendingTx != null) {
            viewModel.pendingEditingCashTx = null
            selectedTab = 2
            voucherType = pendingTx.type
            voucherAmount = pendingTx.amount.toString()
            voucherNotes = pendingTx.notes
            voucherMainAccNotes = pendingTx.mainAccountNotes
            voucherCounterpartAccNotes = pendingTx.counterpartAccountNotes
            selectedMainCashAcc = cashAndBankAccounts.find { it.id == pendingTx.accountId }
            if (pendingTx.referenceType == "CONTACT" && pendingTx.referenceId != null) {
                selectedCounterpartContact = contacts.find { it.id == pendingTx.referenceId }
                selectedCounterpartAcc = null
            } else {
                selectedCounterpartAcc = accounts.find { it.id == pendingTx.counterpartAccountId }
                selectedCounterpartContact = null
            }
            showAddVoucherDialog = true
        }
    }

    // Handle pendingDialogToOpen from Dashboard Quick Operations
    LaunchedEffect(viewModel.pendingDialogToOpen) {
        when (viewModel.pendingDialogToOpen) {
            "RECEIPT" -> {
                viewModel.pendingDialogToOpen = null
                selectedTab = 2
                voucherType = "RECEIPT"
                voucherAmount = ""
                voucherNotes = ""
                voucherError = null
                showAddVoucherDialog = true
            }
            "PAYMENT" -> {
                viewModel.pendingDialogToOpen = null
                selectedTab = 2
                voucherType = "PAYMENT"
                voucherAmount = ""
                voucherNotes = ""
                voucherError = null
                showAddVoucherDialog = true
            }
            "JOURNAL" -> {
                viewModel.pendingDialogToOpen = null
                selectedTab = 1
                showAddJournalEntryDialog = true
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المحاسبة العامة ودفتر القيود", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        accCode = ""
                        accName = ""
                        showAddAccountDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "حساب جديد")
                }
            } else if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddJournalEntryDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "قيد جديد")
                }
            } else if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = {
                        voucherAmount = ""
                        voucherNotes = ""
                        voucherError = null
                        showAddVoucherDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AttachMoney, contentDescription = "سند جديد")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("شجرة الحسابات المالية الدفترية (${accounts.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(accounts) { acc ->
                                AccountTreeItem(acc)
                            }
                        }
                    }
                    1 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("دفتر القيود اليومية التلقائية واليدوية (${journalEntries.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (journalEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد قيود يومية مسجلة بعد", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(journalEntries) { entry ->
                                    JournalEntryCard(entry, accounts, viewModel)
                                }
                            }
                        }
                    }
                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("سجل سندات القبض والصرف الخزينية (${cashTransactions.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (cashTransactions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد سندات قبض أو صرف مسجلة بعد", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(cashTransactions) { tx ->
                                    VoucherListItem(tx, accounts, settings, viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Account Dialog
        if (showAddAccountDialog) {
            AlertDialog(
                onDismissRequest = { showAddAccountDialog = false },
                title = { Text("إضافة حساب فرعي جديد بالدليل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = accCode,
                            onValueChange = { accCode = it },
                            label = { Text("رمز الحساب الدفتري (مثال: 1105)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = accName,
                            onValueChange = { accName = it },
                            label = { Text("اسم الحساب المحاسبي *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("نوع الحساب المالي:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val types = listOf("ASSETS" to "أصول", "LIABILITIES" to "خصوم", "EQUITY" to "ملكيتها", "INCOME" to "إيراد", "EXPENSES" to "مصروف")
                            types.forEach { t ->
                                FilterChip(
                                    selected = accType == t.first,
                                    onClick = { accType = t.first },
                                    label = { Text(t.second, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (accCode.trim().isNotEmpty() && accName.trim().isNotEmpty()) {
                                viewModel.addAccount(
                                    Account(
                                        code = accCode.trim(),
                                        name = accName.trim(),
                                        type = accType,
                                        balance = 0.0
                                    )
                                )
                                showAddAccountDialog = false
                            }
                        }
                    ) {
                        Text("إضافة الحساب", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAccountDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // Add Cash/Bank Voucher Dialog
        if (showAddVoucherDialog) {
            AlertDialog(
                onDismissRequest = { showAddVoucherDialog = false },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = {
                    Text(
                        text = if (voucherType == "RECEIPT") "إضافة سند قبض مالي 🟢" else "إضافة سند صرف مالي 🔴",
                        fontWeight = FontWeight.Bold,
                        color = if (voucherType == "RECEIPT") Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = voucherType == "RECEIPT",
                                onClick = { voucherType = "RECEIPT" },
                                label = { Text("سند قبض (استلام أموال)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = voucherType == "PAYMENT",
                                onClick = { voucherType = "PAYMENT" },
                                label = { Text("سند صرف (دفع أموال)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Main Treasury / Bank Account Dropdown
                        Text("1. حساب الخزينة / الصندوق / البنك (الطرف الأول):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var expandedMainDropdown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (currentUser?.defaultSafeAccountId == null) {
                                        expandedMainDropdown = true
                                    } else {
                                        Toast.makeText(context, "لا يمكنك تغيير الصندوق، أنت مقيد بصندوق محدد مسبقاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedMainCashAcc?.let { "${it.name} (${it.code})" } ?: "اختر حساب الخزينة/البنك", fontSize = 12.sp)
                                    if (currentUser?.defaultSafeAccountId == null) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                                    } else {
                                        Icon(Icons.Default.Lock, contentDescription = "مقفل")
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = expandedMainDropdown,
                                onDismissRequest = { expandedMainDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                cashAndBankAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${acc.code}) - [رصيد: ${acc.balance} ر.ي]") },
                                        onClick = {
                                            selectedMainCashAcc = acc
                                            expandedMainDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Counterpart Account Selection Dialog
                        Text("2. الحساب المقابل (عميل / مورد / مصروف / أخرى):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        var showCounterpartDialog by remember { mutableStateOf(false) }
                        var counterpartSearchQuery by remember { mutableStateOf("") }
                        var counterpartFilterCategory by remember { mutableStateOf("ALL") } // "ALL", "CUSTOMERS", "SUPPLIERS", "ACCOUNTS"

                        val counterpartButtonText = when {
                            selectedCounterpartContact != null -> "${if (selectedCounterpartContact!!.type == "CUSTOMER") "🟢 عميل: " else "🔴 مورد: "}${selectedCounterpartContact!!.name} (رصيد: ${selectedCounterpartContact!!.balance} ر.ي)"
                            selectedCounterpartAcc != null -> "📘 حساب: ${selectedCounterpartAcc!!.name} (${selectedCounterpartAcc!!.code})"
                            else -> "اختر الحساب المقابل (عميل / مورد / حساب عام)"
                        }

                        OutlinedButton(
                            onClick = {
                                counterpartSearchQuery = ""
                                counterpartFilterCategory = "ALL"
                                showCounterpartDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    counterpartButtonText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                            }
                        }

                        if (showCounterpartDialog) {
                            AlertDialog(
                                onDismissRequest = { showCounterpartDialog = false },
                                title = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "تحديد الحساب المقابل",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(onClick = { showCounterpartDialog = false }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray)
                                            }
                                        }

                                        // Search Field
                                        OutlinedTextField(
                                            value = counterpartSearchQuery,
                                            onValueChange = { counterpartSearchQuery = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("بحث باسم، كود الحساب، أو الهاتف...", fontSize = 11.sp) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            trailingIcon = {
                                                if (counterpartSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { counterpartSearchQuery = "" }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            )
                                        )

                                        // Filter Chips
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            FilterChip(
                                                selected = counterpartFilterCategory == "ALL",
                                                onClick = { counterpartFilterCategory = "ALL" },
                                                label = { Text("الكل", fontSize = 11.sp) }
                                            )
                                            FilterChip(
                                                selected = counterpartFilterCategory == "CUSTOMERS",
                                                onClick = { counterpartFilterCategory = "CUSTOMERS" },
                                                label = { Text("🟢 العملاء", fontSize = 11.sp) }
                                            )
                                            FilterChip(
                                                selected = counterpartFilterCategory == "SUPPLIERS",
                                                onClick = { counterpartFilterCategory = "SUPPLIERS" },
                                                label = { Text("🔴 الموردين", fontSize = 11.sp) }
                                            )
                                            FilterChip(
                                                selected = counterpartFilterCategory == "ACCOUNTS",
                                                onClick = { counterpartFilterCategory = "ACCOUNTS" },
                                                label = { Text("📘 حسابات عامة", fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                },
                                text = {
                                    val query = counterpartSearchQuery.trim().lowercase()

                                    val filteredContacts = remember(contacts, query, counterpartFilterCategory) {
                                        contacts.filter { c ->
                                            val categoryMatch = when (counterpartFilterCategory) {
                                                "CUSTOMERS" -> c.type == "CUSTOMER"
                                                "SUPPLIERS" -> c.type == "SUPPLIER"
                                                "ACCOUNTS" -> false
                                                else -> true
                                            }
                                            val queryMatch = query.isEmpty() || c.name.lowercase().contains(query) || c.phone.contains(query)
                                            categoryMatch && queryMatch
                                        }
                                    }

                                    val filteredAccounts = remember(accounts, selectedMainCashAcc, query, counterpartFilterCategory) {
                                        if (counterpartFilterCategory == "CUSTOMERS" || counterpartFilterCategory == "SUPPLIERS") {
                                            emptyList()
                                        } else {
                                            accounts.filter { acc ->
                                                acc.id != selectedMainCashAcc?.id &&
                                                (query.isEmpty() || acc.name.lowercase().contains(query) || acc.code.contains(query))
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 340.dp)
                                    ) {
                                        if (filteredContacts.isEmpty() && filteredAccounts.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("لا توجد نتائج مطابقة للبحث", fontSize = 12.sp, color = Color.Gray)
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (filteredContacts.isNotEmpty()) {
                                                    item {
                                                        Text(
                                                            "--- العملاء والموردين ---",
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                    items(filteredContacts) { c ->
                                                        val isSelected = selectedCounterpartContact?.id == c.id
                                                        Surface(
                                                            onClick = {
                                                                selectedCounterpartContact = c
                                                                selectedCounterpartAcc = null
                                                                showCounterpartDialog = false
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        "${if (c.type == "CUSTOMER") "🟢 عميل:" else "🔴 مورد:"} ${c.name}",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    if (c.phone.isNotEmpty()) {
                                                                        Text("هاتف: ${c.phone}", fontSize = 10.sp, color = Color.Gray)
                                                                    }
                                                                }
                                                                Text("${c.balance} ر.ي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (c.balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                                            }
                                                        }
                                                    }
                                                }

                                                if (filteredAccounts.isNotEmpty()) {
                                                    item {
                                                        Text(
                                                            "--- الحسابات العامة بالدليل ---",
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                                        )
                                                    }
                                                    items(filteredAccounts) { acc ->
                                                        val isSelected = selectedCounterpartAcc?.id == acc.id
                                                        Surface(
                                                            onClick = {
                                                                selectedCounterpartAcc = acc
                                                                selectedCounterpartContact = null
                                                                showCounterpartDialog = false
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text("📘 ${acc.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                    Text("كود: ${acc.code} | ${getArabicAccountType(acc.type)}", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Text("${acc.balance} ر.ي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showCounterpartDialog = false }) {
                                        Text("إغلاق", fontSize = 12.sp)
                                    }
                                }
                            )
                        }

                        // Currency Selection
                        Text("العملة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currencies = listOf("ر.ي (العملة المحلية)", "دولار أمريكي (\$)", "ريال سعودي (SAR)")
                            currencies.forEach { currency ->
                                FilterChip(
                                    selected = voucherSelectedCurrency == currency,
                                    onClick = {
                                        voucherSelectedCurrency = currency
                                        voucherExchangeRateStr = if (currency.startsWith("ر.ي")) "1.0" else ""
                                    },
                                    label = { Text(currency.substringBefore(" "), fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (!isVoucherLocalCurrency) {
                            OutlinedTextField(
                                value = voucherExchangeRateStr,
                                onValueChange = { voucherExchangeRateStr = it },
                                label = { Text("سعر الصرف للعملة المحلية *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        OutlinedTextField(
                            value = voucherAmount,
                            onValueChange = { voucherAmount = it },
                            label = { Text("المبلغ (${voucherSelectedCurrency.substringBefore(" ")}) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        OutlinedTextField(
                            value = voucherMainAccNotes,
                            onValueChange = { voucherMainAccNotes = it },
                            label = { Text("بيان طرف الخزينة / البنك (الطرف الأول)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = voucherCounterpartAccNotes,
                            onValueChange = { voucherCounterpartAccNotes = it },
                            label = { Text("بيان الطرف المقابل (الطرف الثاني)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = voucherNotes,
                            onValueChange = { voucherNotes = it },
                            label = { Text("البيان العام الشامل بالسند") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (voucherError != null) {
                            Text(voucherError!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = voucherAmount.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                voucherError = "يرجى كتابة مبلغ صحيح أكبر من الصفر"
                                return@Button
                            }
                            if (selectedMainCashAcc == null) {
                                voucherError = "يرجى اختيار حساب الخزينة أو البنك"
                                return@Button
                            }
                            if (selectedCounterpartAcc == null && selectedCounterpartContact == null) {
                                voucherError = "يرجى اختيار الحساب المقابل أو العميل/المورد لتطبيق القيد المزدوج"
                                return@Button
                            }

                            val counterpartAccId = if (selectedCounterpartContact != null) {
                                if (selectedCounterpartContact!!.type == "CUSTOMER") {
                                    accounts.find { it.code == "1201" }?.id ?: accounts.first().id
                                } else {
                                    accounts.find { it.code == "2101" }?.id ?: accounts.first().id
                                }
                            } else {
                                selectedCounterpartAcc!!.id
                            }

                            voucherError = null
                            viewModel.createCashTransaction(
                                CashTransaction(
                                    type = voucherType,
                                    accountId = selectedMainCashAcc!!.id,
                                    counterpartAccountId = counterpartAccId,
                                    amount = amt,
                                    notes = voucherNotes.ifEmpty { "سند ${if (voucherType == "RECEIPT") "قبض" else "صرف"} مالي ${selectedCounterpartContact?.let { "- ${it.name}" } ?: ""}" },
                                    mainAccountNotes = voucherMainAccNotes.trim(),
                                    counterpartAccountNotes = voucherCounterpartAccNotes.trim(),
                                    referenceType = if (selectedCounterpartContact != null) "CONTACT" else null,
                                    referenceId = selectedCounterpartContact?.id,
                                    currencyCode = voucherSelectedCurrency.substringBefore(" "),
                                    exchangeRate = voucherExchangeRateStr.toDoubleOrNull() ?: 1.0
                                )
                            )
                            showAddVoucherDialog = false
                        }
                    ) {
                        Text("ترحيل القيد والسند المالي", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddVoucherDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // Add Journal Entry Dialog
        if (showAddJournalEntryDialog) {
            var entryDesc by remember { mutableStateOf("") }
            var isOpeningEntry by remember { mutableStateOf(false) }
            var entryLines by remember { mutableStateOf(listOf(JournalLineInput(), JournalLineInput())) }
            var entryError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { showAddJournalEntryDialog = false },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOpeningEntry) "إضافة قيد افتتاحي جديد" else "إضافة قيد يومي يدوي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("قيد افتتاحي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Checkbox(
                                checked = isOpeningEntry,
                                onCheckedChange = { isOpeningEntry = it }
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = entryDesc,
                            onValueChange = { entryDesc = it },
                            label = { Text(if (isOpeningEntry) "وصف القيد الافتتاحي *" else "البيان / شرح القيد المحاسبي *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("سطور القيد (مدين ودائن):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            OutlinedButton(
                                onClick = { entryLines = entryLines + JournalLineInput() }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "سطر", modifier = Modifier.size(16.dp))
                                Text("إضافة سطر", fontSize = 11.sp)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(entryLines) { index, line ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("سطور #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            if (entryLines.size > 2) {
                                                IconButton(
                                                    onClick = { entryLines = entryLines.filterIndexed { i, _ -> i != index } },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف السطر", tint = Color.Red, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        var expandedAccSelect by remember { mutableStateOf(false) }
                                        val selectedAcc = accounts.firstOrNull { it.id == line.accountId }
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { expandedAccSelect = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(selectedAcc?.let { "${it.name} (${it.code})" } ?: "اختر الحساب *", fontSize = 11.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = expandedAccSelect,
                                                onDismissRequest = { expandedAccSelect = false },
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            ) {
                                                accounts.forEach { acc ->
                                                    DropdownMenuItem(
                                                        text = { Text("${acc.name} (${acc.code})") },
                                                        onClick = {
                                                            entryLines = entryLines.toMutableList().apply {
                                                                this[index] = line.copy(accountId = acc.id)
                                                            }
                                                            expandedAccSelect = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedTextField(
                                                value = line.debit,
                                                onValueChange = { valText ->
                                                    entryLines = entryLines.toMutableList().apply {
                                                        this[index] = line.copy(debit = valText, credit = if (valText.isNotEmpty()) "" else line.credit)
                                                    }
                                                },
                                                label = { Text("مدين (ر.ي)", fontSize = 10.sp) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            OutlinedTextField(
                                                value = line.credit,
                                                onValueChange = { valText ->
                                                    entryLines = entryLines.toMutableList().apply {
                                                        this[index] = line.copy(credit = valText, debit = if (valText.isNotEmpty()) "" else line.debit)
                                                    }
                                                },
                                                label = { Text("دائن (ر.ي)", fontSize = 10.sp) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = line.description,
                                            onValueChange = { valText ->
                                                entryLines = entryLines.toMutableList().apply {
                                                    this[index] = line.copy(description = valText)
                                                }
                                            },
                                            label = { Text("شرح السطر الفرعي (اختياري)", fontSize = 10.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }

                        val totalDebit = entryLines.sumOf { it.debit.toDoubleOrNull() ?: 0.0 }
                        val totalCredit = entryLines.sumOf { it.credit.toDoubleOrNull() ?: 0.0 }
                        val balanceDiff = totalDebit - totalCredit

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (balanceDiff == 0.0 && totalDebit > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("إجمالي المدين: ${totalDebit} ر.ي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("إجمالي الدائن: ${totalCredit} ر.ي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            }
                        }

                        if (entryError != null) {
                            Text(entryError!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (entryDesc.trim().isEmpty()) {
                                entryError = "يرجى كتابة بيان القيد المحاسبي"
                                return@Button
                            }
                            if (entryLines.any { it.accountId == null }) {
                                entryError = "يرجى تحديد حساب محاسبي لكل سطر من القيد"
                                return@Button
                            }
                            val totalDebit = entryLines.sumOf { it.debit.toDoubleOrNull() ?: 0.0 }
                            val totalCredit = entryLines.sumOf { it.credit.toDoubleOrNull() ?: 0.0 }
                            val balanceDiff = totalDebit - totalCredit
                            if (balanceDiff != 0.0 || totalDebit <= 0.0) {
                                entryError = "القيد غير متوازن! يجب تساوى إجمالي المدين والدائن"
                                return@Button
                            }

                            entryError = null
                            val prefix = if (isOpeningEntry) "JV-OPEN-" else "JV-MANUAL-"
                            val entryNum = prefix + (100000..999999).random()
                            val descriptionText = if (isOpeningEntry) "[قيد افتتاحي] $entryDesc" else entryDesc

                            val entryObj = JournalEntry(
                                entryNumber = entryNum,
                                description = descriptionText,
                                isPosted = true,
                                referenceType = if (isOpeningEntry) "OPENING" else "MANUAL",
                                currencyCode = "ر.ي",
                                exchangeRate = 1.0
                            )

                            val linesObj = entryLines.map { line ->
                                JournalEntryLine(
                                    journalEntryId = 0,
                                    accountId = line.accountId!!,
                                    debit = line.debit.toDoubleOrNull() ?: 0.0,
                                    credit = line.credit.toDoubleOrNull() ?: 0.0,
                                    description = line.description
                                )
                            }

                            viewModel.createManualJournalEntry(entryObj, linesObj)
                            showAddJournalEntryDialog = false
                        }
                    ) { Text("حفظ وترحيل القيد", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddJournalEntryDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}
