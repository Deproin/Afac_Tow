package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.Account
import com.example.data.model.Currency
import com.example.data.model.CurrencyExchange
import com.example.data.model.Remittance
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemittanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val currencies by viewModel.currencies.collectAsState()

    var type by remember { mutableStateOf("OUTGOING") } // OUTGOING or INCOMING
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedSafeId by remember { mutableStateOf<Long?>(null) }
    val defaultCurrency = currencies.firstOrNull { it.isDefault }?.let { it.symbol.ifEmpty { it.code } } ?: "ر.ي"
    
    var amount by remember { mutableStateOf("") }
    var currencyCode by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }
    
    var commissionAmount by remember { mutableStateOf("") }
    var commissionCurrency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }
    
    var senderName by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var transferCompany by remember { mutableStateOf("") }
    var transferNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val safeAccounts = accounts.filter { it.code.startsWith("1101") || it.code.startsWith("1102") }
    val contactsAccounts = accounts.filter { it.code.startsWith("1201") || it.code.startsWith("2101") }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var showQuickAddContactDialog by remember { mutableStateOf(false) }
    var savedRemittanceId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    if (showQuickAddContactDialog) {
        QuickAddContactDialog(
            onDismiss = { showQuickAddContactDialog = false },
            onContactAdded = { newContact ->
                viewModel.addContact(newContact)
                if (type == "OUTGOING") {
                    receiverName = newContact.name
                } else {
                    senderName = newContact.name
                }
                Toast.makeText(context, "✅ تمت إضافة ${newContact.name} وسحب بياناته للحوالة", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSuccessDialog) {
        val remNumber = "REM-${savedRemittanceId ?: "000"}"
        val remTotal = amount.toDoubleOrNull() ?: 0.0
        val shareText = "تم تحويل مبلغ $remTotal $currencyCode عبر $transferCompany برقم $transferNumber"
        
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onDismiss()
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "نجاح", tint = Color(0xFF2E7D32), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("✅ تم ترحيل وتأكيد المستند بنجاح", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("رقم المستند: $remNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("إجمالي المبلغ الكلي: $remTotal $currencyCode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("خيارات المشاركة والطباعة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SuccessActionItem("إنشاء PDF", Icons.Default.PictureAsPdf, Color.Red) {
                            Toast.makeText(context, "الطباعة قيد التطوير للحوالات", Toast.LENGTH_SHORT).show()
                        }
                        SuccessActionItem("طباعة", Icons.Default.Print, Color.Blue) {
                            Toast.makeText(context, "الطباعة قيد التطوير للحوالات", Toast.LENGTH_SHORT).show()
                        }
                        SuccessActionItem("مشاركة", Icons.Default.Share, Color.DarkGray) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "مستند $remNumber")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة المستند عبر"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SuccessActionItem("واتساب", Icons.Default.Chat, Color(0xFF25D366)) {
                            val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(shareText)}")
                            }
                            try {
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show()
                            }
                        }
                        SuccessActionItem("رسالة SMS", Icons.Default.Sms, Color(0xFF00ACC1)) {
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:")
                                putExtra("sms_body", shareText)
                            }
                            try {
                                context.startActivity(smsIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show()
                            }
                        }
                        SuccessActionItem("نسخ", Icons.Default.ContentCopy, Color.Black) {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Remittance", shareText)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ التفاصيل", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onDismiss()
                    }
                ) {
                    Text("إغلاق والعودة", color = Color.White)
                }
            }
        )
        return // Do not render the main dialog if success dialog is shown
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الحوالات", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Type Selection
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(
                            selected = type == "OUTGOING",
                            onClick = { type = "OUTGOING" },
                            label = { Text("صادرة (دفع)") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = type == "INCOMING",
                            onClick = { type = "INCOMING" },
                            label = { Text("واردة (استلام)") }
                        )
                    }

                    // Account & Contact Selection
                    var expandedAccount by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedAccount,
                            onExpandedChange = { expandedAccount = !expandedAccount },
                            modifier = Modifier.weight(1f)
                        ) {
                            val selectedContact = contacts.find { it.name == (if (type == "OUTGOING") receiverName else senderName) }
                            val selectedAcc = contactsAccounts.find { it.id == selectedAccountId }
                            val displayText = when {
                                selectedContact != null -> "👤 ${selectedContact.name} (${if (selectedContact.type == "CUSTOMER") "عميل" else "مورد"})"
                                selectedAcc != null -> "🏛️ ${selectedAcc.name}"
                                else -> "بحث/اختيار حساب أو عميل أو مورد..."
                            }

                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("حساب جهة التحويل / العميل") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccount) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAccount,
                                onDismissRequest = { expandedAccount = false }
                            ) {
                                if (contacts.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("--- العملاء والموردون 👤 ---", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp) },
                                        onClick = {},
                                        enabled = false
                                    )
                                    contacts.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text("👤 ${c.name} (${if (c.type == "CUSTOMER") "عميل" else "مورد"})") },
                                            onClick = {
                                                if (type == "OUTGOING") receiverName = c.name else senderName = c.name
                                                expandedAccount = false
                                            }
                                        )
                                    }
                                }

                                DropdownMenuItem(
                                    text = { Text("--- حسابات الدليل المحاسبي 🏛️ ---", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp) },
                                    onClick = {},
                                    enabled = false
                                )
                                contactsAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("🏛️ ${acc.name}") },
                                        onClick = {
                                            selectedAccountId = acc.id
                                            expandedAccount = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showQuickAddContactDialog = true },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل/مورد جديد", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("المبلغ") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        var expandedCurr by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCurr,
                            onExpandedChange = { expandedCurr = !expandedCurr },
                            modifier = Modifier.weight(0.5f)
                        ) {
                            OutlinedTextField(
                                value = currencyCode,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("العملة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurr) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCurr,
                                onDismissRequest = { expandedCurr = false }
                            ) {
                                for (c in currencies) {
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${c.symbol.ifEmpty { c.code }})") },
                                        onClick = { currencyCode = c.symbol.ifEmpty { c.code }; expandedCurr = false }
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = commissionAmount,
                            onValueChange = { commissionAmount = it },
                            label = { Text("العمولة") },
                            modifier = Modifier.weight(1f)
                        )
                        var expandedCommCurr by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCommCurr,
                            onExpandedChange = { expandedCommCurr = !expandedCommCurr },
                            modifier = Modifier.weight(0.5f)
                        ) {
                            OutlinedTextField(
                                value = commissionCurrency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("عملة العمولة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCommCurr) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCommCurr,
                                onDismissRequest = { expandedCommCurr = false }
                            ) {
                                for (c in currencies) {
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${c.symbol.ifEmpty { c.code }})") },
                                        onClick = { commissionCurrency = c.symbol.ifEmpty { c.code }; expandedCommCurr = false }
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            label = { Text("اسم المرسل") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = receiverName,
                            onValueChange = { receiverName = it },
                            label = { Text("اسم المستلم") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = transferCompany,
                            onValueChange = { transferCompany = it },
                            label = { Text("شركة التحويل") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = transferNumber,
                            onValueChange = { transferNumber = it },
                            label = { Text("رقم الحوالة") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("التفاصيل / البيان") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    var expandedSafe by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedSafe,
                        onExpandedChange = { expandedSafe = !expandedSafe }
                    ) {
                        OutlinedTextField(
                            value = safeAccounts.find { it.id == selectedSafeId }?.name ?: "حساب الصندوق / البنك",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("حساب الصندوق") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSafe) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSafe,
                            onDismissRequest = { expandedSafe = false }
                        ) {
                            safeAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedSafeId = acc.id
                                        expandedSafe = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Footer Buttons (Matches app's operations design)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إغلاق", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (selectedAccountId != null && selectedSafeId != null && amount.isNotEmpty()) {
                                val rem = Remittance(
                                    type = type,
                                    accountId = selectedAccountId!!,
                                    safeAccountId = selectedSafeId!!,
                                    amount = amount.toDoubleOrNull() ?: 0.0,
                                    currencyCode = currencyCode,
                                    commissionAmount = commissionAmount.toDoubleOrNull() ?: 0.0,
                                    commissionCurrency = commissionCurrency,
                                    senderName = senderName,
                                    receiverName = receiverName,
                                    transferCompany = transferCompany,
                                    transferNumber = transferNumber,
                                    notes = notes
                                )
                                viewModel.createRemittance(rem) { generatedId ->
                                    savedRemittanceId = generatedId
                                    showSuccessDialog = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyExchangeDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val currencies by viewModel.currencies.collectAsState()

    var selectedSafeId by remember { mutableStateOf<Long?>(null) }
    var selectedContactName by remember { mutableStateOf("") }
    var showQuickAddContactDialog by remember { mutableStateOf(false) }

    val safeAccounts = accounts.filter { it.code.startsWith("1101") || it.code.startsWith("1102") }
    val defaultCurrency = currencies.firstOrNull { it.isDefault }?.let { it.symbol.ifEmpty { it.code } } ?: "ر.ي"
    val secondCurrency = currencies.firstOrNull { !it.isDefault }?.let { it.symbol.ifEmpty { it.code } } ?: "$"

    var fromCurrency by remember(secondCurrency) { mutableStateOf(secondCurrency) }
    var fromAmount by remember { mutableStateOf("") }
    var fromRate by remember { mutableStateOf("1") }

    var toCurrency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }
    var toAmount by remember { mutableStateOf("") }
    var toRate by remember { mutableStateOf("1") }

    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (showQuickAddContactDialog) {
        QuickAddContactDialog(
            onDismiss = { showQuickAddContactDialog = false },
            onContactAdded = { newContact ->
                viewModel.addContact(newContact)
                selectedContactName = newContact.name
                Toast.makeText(context, "✅ تمت إضافة ${newContact.name} وتحديده كجهة مصارفة", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("صرف عملات", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var expandedSafe by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedSafe,
                        onExpandedChange = { expandedSafe = !expandedSafe }
                    ) {
                        OutlinedTextField(
                            value = safeAccounts.find { it.id == selectedSafeId }?.name ?: "بحث عن حساب الصندوق",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("حساب الصندوق") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSafe) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSafe,
                            onDismissRequest = { expandedSafe = false }
                        ) {
                            safeAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedSafeId = acc.id
                                        expandedSafe = false
                                    }
                                )
                            }
                        }
                    }

                    // Contact Selection for Currency Exchange with (+) Button
                    var expandedContact by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedContact,
                            onExpandedChange = { expandedContact = !expandedContact },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (selectedContactName.isNotEmpty()) "👤 $selectedContactName" else "جهة المصارفة / العميل (اختياري)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("جهة التعامل / العميل / المورد") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedContact) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedContact,
                                onDismissRequest = { expandedContact = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("بدون تخصيص (عام)") },
                                    onClick = {
                                        selectedContactName = ""
                                        expandedContact = false
                                    }
                                )
                                contacts.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("👤 ${c.name} (${if (c.type == "CUSTOMER") "عميل" else "مورد"})") },
                                        onClick = {
                                            selectedContactName = c.name
                                            expandedContact = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showQuickAddContactDialog = true },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل/مورد جديد", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    Text("مـن (العملة المراد بيعها):", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromAmount,
                            onValueChange = { fromAmount = it },
                            label = { Text("المبلغ") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fromRate,
                            onValueChange = { fromRate = it },
                            label = { Text("سعر الصرف") },
                            modifier = Modifier.weight(0.8f)
                        )
                        
                        var expandedCurr by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCurr,
                            onExpandedChange = { expandedCurr = !expandedCurr },
                            modifier = Modifier.weight(0.7f)
                        ) {
                            OutlinedTextField(
                                value = fromCurrency,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurr) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = expandedCurr, onDismissRequest = { expandedCurr = false }) {
                                for (c in currencies) {
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${c.symbol.ifEmpty { c.code }})") },
                                        onClick = { fromCurrency = c.symbol.ifEmpty { c.code }; expandedCurr = false }
                                    )
                                }
                            }
                        }
                    }

                    Text("إلـى (العملة المشتراة):", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = toAmount,
                            onValueChange = { toAmount = it },
                            label = { Text("المبلغ المقابل") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toRate,
                            onValueChange = { toRate = it },
                            label = { Text("سعر الصرف") },
                            modifier = Modifier.weight(0.8f)
                        )
                        
                        var expandedCurr by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCurr,
                            onExpandedChange = { expandedCurr = !expandedCurr },
                            modifier = Modifier.weight(0.7f)
                        ) {
                            OutlinedTextField(
                                value = toCurrency,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurr) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = expandedCurr, onDismissRequest = { expandedCurr = false }) {
                                for (c in currencies) {
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${c.symbol.ifEmpty { c.code }})") },
                                        onClick = { toCurrency = c.symbol.ifEmpty { c.code }; expandedCurr = false }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("التفاصيل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Footer Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (selectedSafeId != null && fromAmount.isNotEmpty() && toAmount.isNotEmpty()) {
                                val finalNotes = if (selectedContactName.isNotEmpty()) {
                                    if (notes.isNotEmpty()) "$notes | جهة المصارفة: $selectedContactName" else "جهة المصارفة: $selectedContactName"
                                } else notes

                                val exch = CurrencyExchange(
                                    safeAccountId = selectedSafeId!!,
                                    fromCurrency = fromCurrency,
                                    fromAmount = fromAmount.toDoubleOrNull() ?: 0.0,
                                    fromExchangeRate = fromRate.toDoubleOrNull() ?: 1.0,
                                    toCurrency = toCurrency,
                                    toAmount = toAmount.toDoubleOrNull() ?: 0.0,
                                    toExchangeRate = toRate.toDoubleOrNull() ?: 1.0,
                                    notes = finalNotes
                                )
                                viewModel.createCurrencyExchange(exch)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ المصارفة")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddContactDialog(
    onDismiss: () -> Unit,
    onContactAdded: (com.example.data.model.Contact) -> Unit
) {
    var type by remember { mutableStateOf("CUSTOMER") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numberIndex != -1) {
                            phone = it.getString(numberIndex) ?: ""
                        }
                        if (nameIndex != -1 && name.isBlank()) {
                            name = it.getString(nameIndex) ?: ""
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("إضافة عميل / مورد جديد 👤", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "CUSTOMER",
                        onClick = { type = "CUSTOMER" },
                        label = { Text("عميل جديد 👤") }
                    )
                    FilterChip(
                        selected = type == "SUPPLIER",
                        onClick = { type = "SUPPLIER" },
                        label = { Text("مورد جديد 🏭") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العميل / المورد *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Contacts, contentDescription = "اختر من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val newContact = com.example.data.model.Contact(
                            type = type,
                            name = name.trim(),
                            phone = phone.trim()
                        )
                        onContactAdded(newContact)
                        onDismiss()
                    }
                }
            ) {
                Text("حفظ وإدراج فوراً")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
