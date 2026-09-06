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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashTransaction
import com.example.data.model.Contact
import com.example.util.PhoneUtils
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val contacts by viewModel.contacts.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val cashTransactions by viewModel.cashTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("العملاء", "الموردين")

    var searchText by remember { mutableStateOf("") }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var selectedContactForEdit by remember { mutableStateOf<Contact?>(null) }
    
    LaunchedEffect(viewModel.pendingDialogToOpen) {
        val pending = viewModel.pendingDialogToOpen
        if (pending != null) {
            when (pending) {
                "NEW_CUSTOMER" -> {
                    selectedTab = 0
                    showAddContactDialog = true
                }
                "NEW_SUPPLIER" -> {
                    selectedTab = 1
                    showAddContactDialog = true
                }
            }
            viewModel.pendingDialogToOpen = null
        }
    }
    var selectedContactForStatement by remember { mutableStateOf<Contact?>(null) }
    var selectedContactForVoucher by remember { mutableStateOf<Contact?>(null) }

    // Dialog inputs
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var creditLimitInput by remember { mutableStateOf("0") }
    var notesInput by remember { mutableStateOf("") }
    var initialBalanceInput by remember { mutableStateOf("0") }
    var isBalanceDebit by remember { mutableStateOf(true) } // true: عليه (مدين), false: له (دائن)

    // Voucher inputs
    var voucherAmount by remember { mutableStateOf("") }
    var voucherNotes by remember { mutableStateOf("") }

    // Metrics calculations
    val totalCustomerDebts = remember(contacts) {
        contacts.filter { it.type == "CUSTOMER" }.sumOf { it.balance }
    }
    val totalSupplierPayables = remember(contacts) {
        contacts.filter { it.type == "SUPPLIER" }.sumOf { it.balance }
    }

    var showExcelMenu by remember { mutableStateOf(false) }

    val contactsCsvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val targetType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER"
            viewModel.importContactsFromCsv(it, context, fallbackType = targetType) { count, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

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
                            phoneInput = it.getString(numberIndex) ?: ""
                        }
                        if (nameIndex != -1 && nameInput.isBlank()) {
                            nameInput = it.getString(nameIndex) ?: ""
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة العملاء والموردين الحسابية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showExcelMenu = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Excel استيراد وتصدير", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded = showExcelMenu,
                        onDismissRequest = { showExcelMenu = false }
                    ) {
                        val currentType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER"
                        val label = if (selectedTab == 0) "العملاء" else "الموردين"
                        DropdownMenuItem(
                            text = { Text("📊 تصدير $label إلى Excel (CSV)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                viewModel.exportContactsToCsv(context, typeFilter = currentType)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📥 استيراد $label من Excel (CSV)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                contactsCsvPickerLauncher.launch("*/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📄 تحميل نموذج استيراد فارغ") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                viewModel.generateContactsTemplate(context, defaultType = currentType)
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    nameInput = ""
                    phoneInput = ""
                    addressInput = ""
                    creditLimitInput = "0"
                    notesInput = ""
                    initialBalanceInput = "0"
                    isBalanceDebit = true
                    showAddContactDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة جهة")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }


            val currentType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER"
            val filteredContacts = contacts.filter { contact ->
                contact.type == currentType &&
                        (contact.name.contains(searchText, ignoreCase = true) ||
                                contact.phone.contains(searchText, ignoreCase = true) ||
                                contact.address.contains(searchText, ignoreCase = true))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Financial Summary Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (selectedTab == 0) "إجمالي مديونيات العملاء (ديون مستحقة)" else "إجمالي مستحقات الموردين (واجبة الدفع)",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${String.format("%.2f", if (selectedTab == 0) totalCustomerDebts else totalSupplierPayables)} ر.ي",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("العدد الكلي", fontSize = 11.sp, color = Color.Gray)
                            Text("${filteredContacts.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text(if (selectedTab == 0) "ابحث عن عميل بالاسم أو الهاتف..." else "ابحث عن مورد بالاسم أو الهاتف...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Excel Quick Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val label = if (selectedTab == 0) "العملاء" else "الموردين"
                    val currentType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER"

                    OutlinedButton(
                        onClick = { viewModel.exportContactsToCsv(context, typeFilter = currentType) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير $label", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { contactsCsvPickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استيراد $label", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { viewModel.generateContactsTemplate(context, defaultType = currentType) }
                    ) {
                        Text("نموذج", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                if (filteredContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد نتائج مطابقة للبحث", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            ContactRow(
                                contact = contact,
                                onEdit = {
                                    selectedContactForEdit = contact
                                    nameInput = contact.name
                                    phoneInput = contact.phone
                                    addressInput = contact.address
                                    creditLimitInput = contact.creditLimit.toString()
                                    notesInput = contact.notes
                                },
                                onDelete = { viewModel.deleteContact(contact) },
                                onVoucher = { selectedContactForVoucher = contact },
                                onStatement = { selectedContactForStatement = contact }
                            )
                        }
                    }
                }
            }
        }

        // Add Contact Dialog
        if (showAddContactDialog) {
            val contactType = if (selectedTab == 0) "CUSTOMER" else "SUPPLIER"
            AlertDialog(
                onDismissRequest = { showAddContactDialog = false },
                title = { Text("إضافة ${if (contactType == "CUSTOMER") "عميل" else "مورد"} جديد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("الاسم التجاري أو الشخصي *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(
                            value = phoneInput, 
                            onValueChange = { phoneInput = it }, 
                            label = { Text("رقم الجوال / الواتساب") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                    contactPickerLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختر من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        OutlinedTextField(value = addressInput, onValueChange = { addressInput = it }, label = { Text("العنوان / الفرع") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = creditLimitInput, onValueChange = { creditLimitInput = it }, label = { Text("مبلغ التأمين / الحد الائتماني (اختياري - ر.ي)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("الرصيد الافتتاحي السابـق", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = initialBalanceInput,
                            onValueChange = { initialBalanceInput = it },
                            label = { Text("مبلغ الرصيد الافتتاحي (ر.ي)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("طبيعة الرصيد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FilterChip(
                                selected = isBalanceDebit,
                                onClick = { isBalanceDebit = true },
                                label = { Text("🔴 عليه (مدين)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = !isBalanceDebit,
                                onClick = { isBalanceDebit = false },
                                label = { Text("🟢 له (دائن)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        OutlinedTextField(value = notesInput, onValueChange = { notesInput = it }, label = { Text("ملاحظات أخرى") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nameInput.trim().isNotEmpty()) {
                                val rawBal = initialBalanceInput.toDoubleOrNull() ?: 0.0
                                val finalBal = if (isBalanceDebit) rawBal else -rawBal
                                viewModel.addContact(
                                    Contact(
                                        type = contactType,
                                        name = nameInput.trim(),
                                        phone = phoneInput.trim(),
                                        address = addressInput.trim(),
                                        balance = finalBal,
                                        creditLimit = creditLimitInput.toDoubleOrNull() ?: 0.0,
                                        notes = notesInput.trim()
                                    )
                                )
                                showAddContactDialog = false
                            }
                        }
                    ) {
                        Text("حفظ جهة الاتصال", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddContactDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // Edit Contact Dialog
        if (selectedContactForEdit != null) {
            val contact = selectedContactForEdit!!
            AlertDialog(
                onDismissRequest = { selectedContactForEdit = null },
                title = { Text("تعديل بيانات: ${contact.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(
                            value = phoneInput, 
                            onValueChange = { phoneInput = it }, 
                            label = { Text("رقم الهاتف") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                    contactPickerLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختر من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        OutlinedTextField(value = addressInput, onValueChange = { addressInput = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = creditLimitInput, onValueChange = { creditLimitInput = it }, label = { Text("مبلغ التأمين / الحد الائتماني (اختياري - ر.ي)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = notesInput, onValueChange = { notesInput = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nameInput.trim().isNotEmpty()) {
                                viewModel.updateContact(
                                    contact.copy(
                                        name = nameInput.trim(),
                                        phone = phoneInput.trim(),
                                        address = addressInput.trim(),
                                        creditLimit = creditLimitInput.toDoubleOrNull() ?: 0.0,
                                        notes = notesInput.trim()
                                    )
                                )
                                selectedContactForEdit = null
                            }
                        }
                    ) {
                        Text("تحديث البيانات", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedContactForEdit = null }) { Text("إلغاء") }
                }
            )
        }

        // Voucher (Receipt / Payment) Dialog
        if (selectedContactForVoucher != null) {
            val contact = selectedContactForVoucher!!
            val isCustomer = contact.type == "CUSTOMER"
            val cashAndBankAccounts = remember(accounts) {
                accounts.filter {
                    it.type == "ASSETS" && (it.name.contains("الصندوق") || it.name.contains("البنك") || it.name.contains("خزينة") || it.code.startsWith("11"))
                }
            }
            var selectedCashAcc by remember { mutableStateOf<com.example.data.model.Account?>(null) }
            var voucherErrorMsg by remember { mutableStateOf<String?>(null) }
            var expandedCashAccDropdown by remember { mutableStateOf(false) }

            LaunchedEffect(cashAndBankAccounts, currentUser) {
                if (cashAndBankAccounts.isNotEmpty()) {
                    if (currentUser?.defaultSafeAccountId != null) {
                        selectedCashAcc = cashAndBankAccounts.find { it.id == currentUser?.defaultSafeAccountId }
                    } else if (selectedCashAcc == null) {
                        selectedCashAcc = cashAndBankAccounts.firstOrNull { it.name.contains("الصندوق") } ?: cashAndBankAccounts.first()
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { selectedContactForVoucher = null },
                title = { Text(if (isCustomer) "سند قبض مالي من عميل 🟢" else "سند صرف مالي إلى مورد 🔴", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الجهة: ${contact.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("الرصيد الدفتري الحالي: ${contact.balance} ر.ي", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Cash/Bank Account Selector
                        Text("حساب الخزينة / الصندوق / البنك المسدد/المستلم منه *", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { 
                                    if (currentUser?.defaultSafeAccountId == null) {
                                        expandedCashAccDropdown = true
                                    } else {
                                        Toast.makeText(context, "لا يمكنك تغيير الصندوق، أنت مقيد بصندوق محدد مسبقاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedCashAcc?.let { "${it.name} (${it.code}) - [رصيد: ${it.balance} ر.ي]" } ?: "اختر حساب الخزينة/البنك", fontSize = 12.sp)
                                    if (currentUser?.defaultSafeAccountId == null) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                                    } else {
                                        Icon(Icons.Default.Lock, contentDescription = "مقفل")
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = expandedCashAccDropdown,
                                onDismissRequest = { expandedCashAccDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                cashAndBankAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${acc.code}) - [رصيد: ${acc.balance} ر.ي]") },
                                        onClick = {
                                            selectedCashAcc = acc
                                            expandedCashAccDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = voucherAmount,
                            onValueChange = { voucherAmount = it },
                            label = { Text("مبلغ السند (ر.ي) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(value = voucherNotes, onValueChange = { voucherNotes = it }, label = { Text("البيان والوصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                        if (voucherErrorMsg != null) {
                            Text(voucherErrorMsg!!, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = voucherAmount.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                voucherErrorMsg = "يرجى كتابة مبلغ صحيح أكبر من الصفر"
                                return@Button
                            }
                            if (selectedCashAcc == null) {
                                voucherErrorMsg = "يرجى اختيار حساب الخزينة أو البنك"
                                return@Button
                            }

                            voucherErrorMsg = null
                            val txType = if (isCustomer) "RECEIPT" else "PAYMENT"
                            viewModel.createCashTransaction(
                                CashTransaction(
                                    type = txType,
                                    accountId = selectedCashAcc!!.id,
                                    amount = amount,
                                    notes = voucherNotes.ifEmpty { "سند مالي متعلق بجهة ${contact.name}" },
                                    referenceType = "CONTACT",
                                    referenceId = contact.id,
                                    currencyCode = "ر.ي",
                                    exchangeRate = 1.0
                                )
                            )

                            selectedContactForVoucher = null
                            voucherAmount = ""
                            voucherNotes = ""
                        }
                    ) {
                        Text("حفظ وترحيل السند", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedContactForVoucher = null }) { Text("إلغاء") }
                }
            )
        }

        // Account Statement Dialog
        if (selectedContactForStatement != null) {
            val context = LocalContext.current
            val contact = selectedContactForStatement!!
            val contactInvoices = invoices.filter { it.contactId == contact.id }
            val contactCashTx = cashTransactions.filter { it.referenceType == "CONTACT" && it.referenceId == contact.id }

            val dateString = remember(contact.id) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            }

            val statementHtml = remember(contact, contactInvoices, contactCashTx) {
                val isCustomer = contact.type == "CUSTOMER"

                // Create combined transaction rows
                val allTxRows = mutableListOf<Triple<Long, String, Triple<String, Double, Double>>>() // timestamp, docNum, Pair(desc, debit, credit)

                contactInvoices.forEach { inv ->
                    val typeName = when (inv.type) {
                        "SALE_CREDIT" -> "فاتورة مبيعات آجلة"
                        "SALE_CASH" -> "فاتورة مبيعات نقدية"
                        "PURCHASE_CREDIT" -> "فاتورة مشتريات آجلة"
                        "PURCHASE_CASH" -> "فاتورة مشتريات نقدية"
                        "SALE_RETURN" -> "مرتجع مبيعات"
                        "PURCHASE_RETURN" -> "مرتجع مشتريات"
                        else -> "فاتورة"
                    }

                    val (debit, credit) = if (isCustomer) {
                        when (inv.type) {
                            "SALE_CREDIT" -> Pair(inv.total, 0.0)
                            "SALE_CASH" -> Pair(inv.total, inv.total)
                            "SALE_RETURN" -> Pair(0.0, inv.total)
                            else -> Pair(inv.total, 0.0)
                        }
                    } else {
                        when (inv.type) {
                            "PURCHASE_CREDIT" -> Pair(0.0, inv.total)
                            "PURCHASE_CASH" -> Pair(inv.total, inv.total)
                            "PURCHASE_RETURN" -> Pair(inv.total, 0.0)
                            else -> Pair(0.0, inv.total)
                        }
                    }

                    allTxRows.add(Triple(inv.timestamp, inv.invoiceNumber, Triple("$typeName ${inv.notes.ifEmpty { "" }}", debit, credit)))
                }

                contactCashTx.forEach { tx ->
                    val typeName = if (tx.type == "RECEIPT") "سند قبض مالي 🟢" else "سند صرف مالي 🔴"
                    val (debit, credit) = if (isCustomer) {
                        if (tx.type == "RECEIPT") Pair(0.0, tx.amount) else Pair(tx.amount, 0.0)
                    } else {
                        if (tx.type == "PAYMENT") Pair(tx.amount, 0.0) else Pair(0.0, tx.amount)
                    }

                    allTxRows.add(Triple(tx.timestamp, "#${tx.id}", Triple("$typeName (${tx.notes.ifEmpty { "سند خزانة/بنك" }})", debit, credit)))
                }

                // Sort chronologically ascending for running balance calculation
                allTxRows.sortBy { it.first }

                var runningBalance = 0.0
                var totalDebit = 0.0
                var totalCredit = 0.0

                val tableBodyHtml = StringBuilder()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                if (allTxRows.isEmpty()) {
                    tableBodyHtml.append("<tr><td colspan='6' style='text-align: center; color: #777; padding: 15px;'>لا توجد عمليات مسجلة لهذا الحساب حتى الآن</td></tr>")
                } else {
                    allTxRows.forEach { (ts, docNum, data) ->
                        val (desc, debit, credit) = data
                        totalDebit += debit
                        totalCredit += credit

                        if (isCustomer) {
                            runningBalance += (debit - credit)
                        } else {
                            runningBalance += (credit - debit)
                        }

                        val formattedDate = dateFormat.format(Date(ts))
                        val debitText = if (debit > 0) String.format("%.2f", debit) else "-"
                        val creditText = if (credit > 0) String.format("%.2f", credit) else "-"
                        val runningBalText = String.format("%.2f", runningBalance)

                        tableBodyHtml.append("""
                            <tr>
                                <td style='font-size: 11px; color: #555;'>$formattedDate</td>
                                <td style='font-weight: bold;'>$docNum</td>
                                <td>$desc</td>
                                <td style='text-align: center; color: #2E7D32; font-weight: bold;'>$debitText</td>
                                <td style='text-align: center; color: #C62828; font-weight: bold;'>$creditText</td>
                                <td style='text-align: left; font-weight: bold; color: #0061A4;'>$runningBalText ر.ي</td>
                            </tr>
                        """.trimIndent())
                    }
                }

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
                        .info-card { background: #f0f4f8; padding: 12px; border-radius: 8px; margin-bottom: 15px; display: flex; justify-content: space-between; }
                        .summary-box { background: #eef2f6; padding: 10px; border-radius: 8px; margin-top: 15px; font-size: 13px; }
                        .report-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
                        .report-table th { background: #0061A4; color: white; padding: 8px; text-align: right; font-size: 12px; }
                        .report-table td { padding: 8px; font-size: 12px; border-bottom: 1px solid #ddd; }
                        .report-table tr:nth-child(even) { background-color: #f9f9f9; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <div class="company-name">${settings.name}</div>
                        <div class="title">كشف حساب تفصيلي (${if (isCustomer) "عميل" else "مورد"})</div>
                        <div>تاريخ الإصدار: $dateString</div>
                    </div>
                    <div class="info-card">
                        <div>
                            <div><strong>اسم الجهة:</strong> ${contact.name}</div>
                            <div><strong>رقم الجوال:</strong> ${contact.phone.ifEmpty { "غير متوفر" }}</div>
                            <div><strong>العنوان:</strong> ${contact.address.ifEmpty { "غير متوفر" }}</div>
                        </div>
                        <div style="text-align: left;">
                            <div><strong>الرصيد المتبقي الإجمالي:</strong> <span style="font-size: 18px; font-weight: bold; color: #0061A4;">${String.format("%.2f", contact.balance)} ر.ي</span></div>
                            <div><strong>مبلغ التأمين / الحد الائتماني:</strong> ${contact.creditLimit} ر.ي</div>
                        </div>
                    </div>

                    <h4 style="color: #0061A4; margin-bottom: 5px;">سجل دفتر الاستاد لكافة العمليات التراكمية (${allTxRows.size}):</h4>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>التاريخ والوقت</th>
                                <th>رقم المستند</th>
                                <th>نوع العملية والبيان</th>
                                <th style="text-align: center;">مدين (+)</th>
                                <th style="text-align: center;">دائن (-)</th>
                                <th style="text-align: left;">الرصيد المتبقي التراكمي</th>
                            </tr>
                        </thead>
                        <tbody>
                            $tableBodyHtml
                        </tbody>
                    </table>

                    <div class="summary-box">
                        <div style="display: flex; justify-content: space-between;">
                            <span>إجمالي حركة المدين (+): <strong>${String.format("%.2f", totalDebit)} ر.ي</strong></span>
                            <span>إجمالي حركة الدائن (-): <strong>${String.format("%.2f", totalCredit)} ر.ي</strong></span>
                            <span>صافي الرصيد النهائي المتبقي: <strong style="color: #0061A4; font-size: 14px;">${String.format("%.2f", contact.balance)} ر.ي</strong></span>
                        </div>
                    </div>
                </body>
                </html>
                """.trimIndent()
            }

            AlertDialog(
                onDismissRequest = { selectedContactForStatement = null },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = { Text("معاينة كشف حساب: ${contact.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("الرصيد الدفتري الحالي: ${contact.balance} ر.ي", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("السقف الائتماني: ${contact.creditLimit} ر.ي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                        }

                        Text("الفواتير التابعة (${contactInvoices.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        contactInvoices.forEach { inv ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.invoiceNumber} - ${inv.type}", fontSize = 12.sp)
                                Text("${inv.total} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("السندات المالية (${contactCashTx.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        contactCashTx.forEach { tx ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سند #${tx.id} (${tx.notes})", fontSize = 12.sp)
                                Text("${tx.amount} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            printStatement(context, statementHtml, contact.name)
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة / تصدير PDF", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedContactForStatement = null }) { Text("إغلاق") }
                }
            )
        }
    }
}

@Composable
fun ContactRow(
    contact: Contact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onVoucher: () -> Unit,
    onStatement: () -> Unit
) {
    val context = LocalContext.current
    val isCustomer = contact.type == "CUSTOMER"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("الهاتف: ${contact.phone.ifEmpty { "غير متوفر" }}", fontSize = 12.sp, color = Color.Gray)
                    if (contact.address.isNotEmpty()) {
                        Text("العنوان: ${contact.address}", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (contact.phone.isNotEmpty()) {
                        IconButton(onClick = {
                            try {
                                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                context.startActivity(callIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل إجراء الاتصال", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary) }

                        IconButton(onClick = {
                            try {
                                val msg = "عزيزنا ${contact.name}،\nتحية طيبة وبعد...\nرصيد حسابكم الحالي هو: ${contact.balance} ر.ي\nشكراً لتعاملكم معنا."
                                val formattedWa = PhoneUtils.formatForWhatsApp(contact.phone)
                                val phoneParam = if (formattedWa.isNotEmpty()) "phone=${formattedWa}&" else ""
                                val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?${phoneParam}text=${Uri.encode(msg)}"))
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل فتح واتساب", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color(0xFF25D366)) }

                        IconButton(onClick = {
                            try {
                                val msg = "عزيزنا ${contact.name}،\nرصيد حسابكم الحالي هو: ${contact.balance} ر.ي\nشكراً لتعاملكم معنا."
                                val formattedSms = PhoneUtils.formatForSms(contact.phone)
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${formattedSms}")
                                    putExtra("sms_body", msg)
                                }
                                context.startActivity(smsIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Sms, contentDescription = "رسالة SMS", tint = Color(0xFF00ACC1)) }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red) }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الرصيد المالي الحالي", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = "${contact.balance} ر.ي",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCustomer) {
                            if (contact.balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                        } else {
                            if (contact.balance > 0) Color(0xFF00796B) else Color(0xFFC62828)
                        }
                    )
                    if (isCustomer && contact.creditLimit > 0) {
                        Text(
                            text = "السقف الائتماني: ${contact.creditLimit} ر.ي",
                            fontSize = 10.sp,
                            color = if (contact.balance >= contact.creditLimit) Color.Red else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onStatement, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Assessment, contentDescription = "كشف حساب", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("كشف حساب", fontSize = 11.sp)
                    }

                    Button(onClick = onVoucher, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "سند مالي", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سند مالي", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

fun printStatement(context: Context, htmlContent: String, contactName: String) {
    try {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = view.createPrintDocumentAdapter("Statement_$contactName")
                    printManager.print("Statement_$contactName", printAdapter, PrintAttributes.Builder().build())
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
