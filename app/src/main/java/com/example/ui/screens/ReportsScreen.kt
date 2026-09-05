package com.example.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.viewmodel.AppViewModel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val currencyReportBalances by viewModel.currencyReportBalances.collectAsState()
    val selectedReportCurrency by viewModel.selectedReportCurrency.collectAsState()
    val currencies by viewModel.currencies.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val journalEntries by viewModel.journalEntries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val items by viewModel.items.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("قائمة الدخل", "الميزانية العمومية", "ميزان المراجعة", "الحسابات الفرعية", "أداء المستودعات")

    // Filter state for Sub-Accounts Report (Tab 3)
    val mappedAccounts = remember(currencyReportBalances) {
        currencyReportBalances.map { 
            Account(id = it.accountId, code = it.code, name = it.name, type = it.type, balance = it.balance) 
        }
    }
    
    val subAccountsList = remember(mappedAccounts) {
        mappedAccounts.filter { it.code.length > 2 }
    }
    var selectedSubAccount by remember { mutableStateOf<Account?>(null) } // null means "جميع الحسابات الفرعية"
    var expandedSubAccDropdown by remember { mutableStateOf(false) }
    var subAccountSearchText by remember { mutableStateOf("") }


    // General calculations for statement of income (100% from Chart of Accounts)
    val totalRevenues = mappedAccounts.filter { it.type == "REVENUE" && it.code.length > 2 }.sumOf { it.balance }
    val totalExpenses = mappedAccounts.filter { it.type == "EXPENSES" && it.code.length > 2 }.sumOf { it.balance }
    val netProfit = totalRevenues - totalExpenses

    // Assets Liabilities Equity
    val totalAssets = mappedAccounts.filter { it.type == "ASSETS" && it.code.length > 2 }.sumOf { it.balance }
    val totalLiabilities = mappedAccounts.filter { it.type == "LIABILITIES" && it.code.length > 2 }.sumOf { it.balance }
    val equityAccount = mappedAccounts.find { it.code == "3101" }?.balance ?: 100000.0 // Default capital seed
    val totalEquityAndLiabilities = totalLiabilities + equityAccount + netProfit

    val dateString = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير المالية والختامية الشاملة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val currencyList = if (currencies.isEmpty()) listOf(selectedReportCurrency) else currencies.map { if (it.symbol.isNotEmpty()) it.symbol else it.code }.distinct()
            ScrollableTabRow(
                selectedTabIndex = currencyList.indexOf(selectedReportCurrency).takeIf { it >= 0 } ?: 0,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                for (cur in currencyList) {
                    Tab(
                        selected = selectedReportCurrency == cur,
                        onClick = { viewModel.setReportCurrency(cur) },
                        text = { Text("عملة ($cur)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: Income Statement (قائمة الدخل)
                        val reportHtml = remember(totalRevenues, totalExpenses, netProfit, settings, selectedReportCurrency) {
                            buildIncomeStatementHtml(settings, dateString, totalRevenues, totalExpenses, netProfit, selectedReportCurrency)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "قائمة الدخل التقديرية (الأرباح والخسائر)", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 15.sp, 
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { printReport(context, reportHtml, "Income_Statement") },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("طباعة PDF", fontSize = 11.sp, color = Color.White, maxLines = 1)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FinancialReportRow("إجمالي الإيرادات", totalRevenues, isBold = true, currency = selectedReportCurrency)
                                    Divider()
                                    FinancialReportRow("إجمالي المصروفات", -totalExpenses, currency = selectedReportCurrency)
                                    Divider()
                                    FinancialReportRow("صافي الربح النشاطي النهائي", netProfit, isBold = true, color = if (netProfit >= 0) Color(0xFF1565C0) else Color(0xFFC62828), currency = selectedReportCurrency)
                                }
                            }
                        }
                    }
                    1 -> {
                        // TAB 1: Balance Sheet (الميزانية العمومية)
                        val reportHtml = remember(totalAssets, totalLiabilities, equityAccount, netProfit, totalEquityAndLiabilities, settings, selectedReportCurrency) {
                            buildBalanceSheetHtml(settings, dateString, mappedAccounts, totalAssets, totalLiabilities, equityAccount, netProfit, totalEquityAndLiabilities, selectedReportCurrency)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "الميزانية العمومية والمركز المالي", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 15.sp, 
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { printReport(context, reportHtml, "Balance_Sheet") },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("طباعة PDF", fontSize = 11.sp, color = Color.White, maxLines = 1)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("الأصول (الممتلكات)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                                    mappedAccounts.filter { it.type == "ASSETS" && it.code.length > 2 }.forEach { acc ->
                                        FinancialReportRow(acc.name, acc.balance, currency = selectedReportCurrency)
                                    }
                                    FinancialReportRow("إجمالي الأصول الحالية والتابعة", totalAssets, isBold = true, color = MaterialTheme.colorScheme.primary, currency = selectedReportCurrency)

                                    Divider()

                                    Text("الالتزامات وحقوق الملكية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                                    mappedAccounts.filter { it.type == "LIABILITIES" && it.code.length > 2 }.forEach { acc ->
                                        FinancialReportRow(acc.name, acc.balance, currency = selectedReportCurrency)
                                    }
                                    FinancialReportRow("رأس المال المساهم", equityAccount, currency = selectedReportCurrency)
                                    FinancialReportRow("الأرباح المحتجزة للفترة", netProfit, currency = selectedReportCurrency)
                                    FinancialReportRow("إجمالي الالتزامات وحقوق الملكية الكلية", totalEquityAndLiabilities, isBold = true, color = MaterialTheme.colorScheme.primary, currency = selectedReportCurrency)
                                }
                            }
                        }
                    }
                    2 -> {
                        // TAB 2: Trial Balance (ميزان المراجعة)
                        val reportHtml = remember(mappedAccounts, settings, selectedReportCurrency) {
                            buildTrialBalanceHtml(settings, dateString, subAccountsList, selectedReportCurrency)
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ميزان المراجعة بالأرصدة النهائية", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 15.sp, 
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { printReport(context, reportHtml, "Trial_Balance") },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("طباعة PDF", fontSize = 11.sp, color = Color.White, maxLines = 1)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("اسم الحساب المالي", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), fontSize = 12.sp)
                                        Text("مدين (دفتري)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp)
                                        Text("دائن (دفتري)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp)
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(subAccountsList) { acc ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(2f)) {
                                                    Text(acc.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                    Text("الكود: ${acc.code}", fontSize = 10.sp, color = Color.Gray)
                                                }
                                                    val balanceVal = acc.balance
                                                if (acc.type == "ASSETS" || acc.type == "EXPENSES") {
                                                    Text("${String.format("%.2f", balanceVal)} ${selectedReportCurrency}", fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                    Text("0.00 ${selectedReportCurrency}", fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color.Gray)
                                                } else {
                                                    Text("0.00 ${selectedReportCurrency}", fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color.Gray)
                                                    Text("${String.format("%.2f", balanceVal)} ${selectedReportCurrency}", fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // TAB 3: SUB-ACCOUNTS REPORT & FILTER (تقرير الحسابات الفرعية)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("تقرير الحسابات الفرعية وتفاصيل الحركة المالية", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)

                            // Filter Selector: Searchable Sub-Accounts (كتابة اسم الحساب والبحث)
                            Text("فلترة وتحديد كشف الحساب الفرعي بالاسم أو الكود:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = subAccountSearchText,
                                    onValueChange = { input ->
                                        subAccountSearchText = input
                                        expandedSubAccDropdown = true
                                        if (input.isBlank()) {
                                            selectedSubAccount = null
                                        }
                                    },
                                    placeholder = { Text("ابحث باسم الحساب أو الكود (مثال: الصندوق، 1101)...", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                                    trailingIcon = {
                                        if (subAccountSearchText.isNotEmpty() || selectedSubAccount != null) {
                                            IconButton(onClick = {
                                                subAccountSearchText = ""
                                                selectedSubAccount = null
                                                expandedSubAccDropdown = false
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "مسح")
                                            }
                                        } else {
                                            IconButton(onClick = { expandedSubAccDropdown = !expandedSubAccDropdown }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "قائمة")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )

                                DropdownMenu(
                                    expanded = expandedSubAccDropdown,
                                    onDismissRequest = { expandedSubAccDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    val filteredSubAccounts = subAccountsList.filter {
                                        it.name.contains(subAccountSearchText, ignoreCase = true) ||
                                        it.code.contains(subAccountSearchText, ignoreCase = true) ||
                                        getArabicAccountType(it.type).contains(subAccountSearchText, ignoreCase = true)
                                    }

                                    DropdownMenuItem(
                                        text = { Text("🌐 جميع الحسابات الفرعية (كشف شامل)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) },
                                        onClick = {
                                            selectedSubAccount = null
                                            subAccountSearchText = ""
                                            expandedSubAccDropdown = false
                                        }
                                    )
                                    Divider()
                                    if (filteredSubAccounts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("لا توجد حسابات فرعية مطابقة للبحث", color = Color.Gray, fontSize = 12.sp) },
                                            onClick = {}
                                        )
                                    } else {
                                        filteredSubAccounts.forEach { acc ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("${acc.name} (${acc.code}) - [${getArabicAccountType(acc.type)}]", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                                        Text("رصيد: ${acc.balance} ${selectedReportCurrency}", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                },
                                                onClick = {
                                                    selectedSubAccount = acc
                                                    subAccountSearchText = "${acc.name} (${acc.code})"
                                                    expandedSubAccDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }


                            if (selectedSubAccount == null) {
                                // ALL SUB-ACCOUNTS LISTING REPORT
                                val reportHtml = remember(subAccountsList, settings, selectedReportCurrency) {
                                    buildAllSubAccountsHtml(settings, dateString, subAccountsList, selectedReportCurrency)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("عرض كافة الحسابات الفرعية المسجلة (${subAccountsList.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Button(
                                        onClick = { printReport(context, reportHtml, "All_Sub_Accounts") },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("طباعة الكشف", fontSize = 11.sp, color = Color.White)
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(subAccountsList) { acc ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                                        Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text("كود الحساب: ${acc.code} | النوع: ${getArabicAccountType(acc.type)}", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                    Text(
                                                        text = "${acc.balance} ${selectedReportCurrency}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = if (acc.balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // SPECIFIC SUB-ACCOUNT DETAILED MOVEMENT STATEMENT
                                val acc = selectedSubAccount!!
                                var accLines by remember(acc.id) { mutableStateOf<List<JournalEntryLine>>(emptyList()) }

                                LaunchedEffect(acc.id) {
                                    accLines = viewModel.repository.journalDao.getLinesForAccount(acc.id)
                                }

                                val reportHtml = remember(acc, accLines, settings, selectedReportCurrency) {
                                    buildSingleSubAccountHtml(settings, dateString, acc, accLines, journalEntries, selectedReportCurrency)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text("الكود: ${acc.code} | النوع: ${getArabicAccountType(acc.type)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${acc.balance} ${selectedReportCurrency}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            IconButton(onClick = { printReport(context, reportHtml, "Account_${acc.code}") }) {
                                                Icon(Icons.Default.Print, contentDescription = "طباعة", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                        }
                                    }
                                }

                                Text("سجل حركة دفتر الحساب اليومي (${accLines.size} حركة):", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                if (accLines.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد حركات أو قيود مضافة لهذا الحساب الفرعي بعد", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(accLines) { line ->
                                            val entry = journalEntries.firstOrNull { it.id == line.journalEntryId }
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1.5f)) {
                                                        Text(entry?.description ?: line.description.ifEmpty { "حركة حساب" }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text("مرجع القيد: ${entry?.entryNumber ?: "#${line.journalEntryId}"}", fontSize = 10.sp, color = Color.Gray)
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (line.debit > 0) {
                                                            Text("مدين: +${line.debit} ر.ي", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        if (line.credit > 0) {
                                                            Text("دائن: -${line.credit} ر.ي", color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        // TAB 4: Warehouse Performance Report
                        var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
                        
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("تقرير أداء وحركة المستودعات", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            
                            if (warehouses.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("اختر المستودع:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Box(modifier = Modifier.weight(1f)) {
                                        var expanded by remember { mutableStateOf(false) }
                                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(selectedWarehouse?.name ?: "كل المستودعات")
                                        }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text("كل المستودعات") },
                                                onClick = { selectedWarehouse = null; expanded = false }
                                            )
                                            warehouses.forEach { w ->
                                                DropdownMenuItem(
                                                    text = { Text(w.name) },
                                                    onClick = { selectedWarehouse = w; expanded = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            val filteredInvoices = if (selectedWarehouse == null) invoices else invoices.filter { it.warehouseId == selectedWarehouse!!.id }
                            
                            val salesInvoices = filteredInvoices.filter { it.type == "SALE_CASH" || it.type == "SALE_CREDIT" && it.currencyCode == selectedReportCurrency }
                            val returnsInvoices = filteredInvoices.filter { it.type == "SALE_RETURN" && it.currencyCode == selectedReportCurrency }
                            val totalSales = salesInvoices.sumOf { it.total }
                            val totalReturns = returnsInvoices.sumOf { it.total }
                            val netSales = totalSales - totalReturns
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("ملخص المبيعات (حسب الفواتير)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Divider()
                                    FinancialReportRow("إجمالي المبيعات", totalSales, currency = selectedReportCurrency)
                                    FinancialReportRow("مردودات المبيعات", totalReturns, color = Color.Red, currency = selectedReportCurrency)
                                    Divider()
                                    FinancialReportRow("صافي مبيعات المستودع", netSales, isBold = true, color = MaterialTheme.colorScheme.primary, currency = selectedReportCurrency)
                                }
                            }
                            
                            val stockIssues = filteredInvoices.filter { it.type == "STOCK_ISSUE" && it.currencyCode == selectedReportCurrency }
                            val totalIssues = stockIssues.sumOf { it.total }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("المنصرف والمستهلك داخلياً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Divider()
                                    FinancialReportRow("إجمالي المنصرف المخزني", totalIssues, color = MaterialTheme.colorScheme.error, currency = selectedReportCurrency)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialReportRow(label: String, value: Double, isBold: Boolean = false, color: Color = Color.Unspecified, currency: String = "ر.ي") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, fontSize = if (isBold) 14.sp else 13.sp)
        Text(
            text = "${String.format("%.2f", value)} $currency",
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isBold) 14.sp else 13.sp,
            color = color
        )
    }
}

fun printReport(context: Context, htmlContent: String, jobName: String) {
    try {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = view.createPrintDocumentAdapter("Report_$jobName")
                    printManager.print("Report_$jobName", printAdapter, PrintAttributes.Builder().build())
                } catch (e: Exception) {
                    Toast.makeText(context, "فشلت عملية الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        Toast.makeText(context, "فشلت تهيئة الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun buildIncomeStatementHtml(settings: EnterpriseSetting, dateStr: String, rev: Double, exp: Double, net: Double, currency: String = "ر.ي"): String {
    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
            .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
            .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
            .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
            .report-table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            .report-table td { padding: 10px; font-size: 14px; border-bottom: 1px solid #ddd; }
            .bold { font-weight: bold; }
            .total-row { background-color: #f0f4f8; font-size: 16px; font-weight: bold; color: #0061A4; }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="company-name">${settings.name}</div>
            <div>الرقم الضريبي: <strong>${settings.taxId}</strong></div>
            <div class="title">تقرير قائمة الدخل التقديرية (الأرباح والخسائر)</div>
            <div>تاريخ الإصدار: $dateStr</div>
        </div>
        <table class="report-table">
            <tr class="bold"><td>إجمالي الإيرادات</td><td style="text-align: left;">${String.format("%.2f", rev)} $currency</td></tr>
            <tr><td>إجمالي المصروفات</td><td style="text-align: left;">-${String.format("%.2f", exp)} $currency</td></tr>
            <tr class="total-row"><td>صافي الربح النهائي</td><td style="text-align: left;">${String.format("%.2f", net)} $currency</td></tr>
        </table>
    </body>
    </html>
    """.trimIndent()
}

fun buildBalanceSheetHtml(settings: EnterpriseSetting, dateStr: String, accounts: List<Account>, assets: Double, liab: Double, eq: Double, profit: Double, totalEqLiab: Double, currency: String = "ر.ي"): String {
    val assetRows = accounts.filter { it.type == "ASSETS" && it.code.length > 2 }.joinToString("") {
        "<tr><td>${it.name} (${it.code})</td><td style='text-align: left;'>${it.balance} $currency</td></tr>"
    }
    val liabRows = accounts.filter { it.type == "LIABILITIES" && it.code.length > 2 }.joinToString("") {
        "<tr><td>${it.name} (${it.code})</td><td style='text-align: left;'>${it.balance} $currency</td></tr>"
    }

    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
            .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
            .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
            .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
            .report-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
            .report-table td { padding: 8px; font-size: 13px; border-bottom: 1px solid #ddd; }
            .section-title { font-size: 15px; font-weight: bold; color: #0061A4; background: #eef2f6; padding: 6px; }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="company-name">${settings.name}</div>
            <div class="title">الميزانية العمومية والمركز المالي</div>
            <div>تاريخ الإصدار: $dateStr</div>
        </div>
        <div class="section-title">الأصول (الممتلكات)</div>
        <table class="report-table">
            $assetRows
            <tr style="font-weight: bold; background: #e8f5e9;"><td>إجمالي الأصول الحالية والتابعة</td><td style="text-align: left;">$assets $currency</td></tr>
        </table>
        <div class="section-title" style="margin-top: 20px;">الالتزامات وحقوق الملكية</div>
        <table class="report-table">
            $liabRows
            <tr><td>رأس المال المساهم</td><td style="text-align: left;">$eq $currency</td></tr>
            <tr><td>الأرباح المحتجزة للفترة</td><td style="text-align: left;">$profit $currency</td></tr>
            <tr style="font-weight: bold; background: #e8f5e9;"><td>إجمالي الالتزامات وحقوق الملكية الكلية</td><td style="text-align: left;">$totalEqLiab $currency</td></tr>
        </table>
    </body>
    </html>
    """.trimIndent()
}

fun buildTrialBalanceHtml(settings: EnterpriseSetting, dateStr: String, subAccounts: List<Account>, currency: String = "ر.ي"): String {
    val rows = subAccounts.joinToString("") { acc ->
        val isDebit = acc.type == "ASSETS" || acc.type == "EXPENSES"
        """
        <tr>
            <td>${acc.name} (${acc.code})</td>
            <td style="text-align: left;">${if (isDebit) "${acc.balance} $currency" else "0.00 $currency"}</td>
            <td style="text-align: left;">${if (!isDebit) "${acc.balance} $currency" else "0.00 $currency"}</td>
        </tr>
        """
    }

    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
            .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
            .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
            .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
            .report-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
            .report-table th { background: #f0f4f8; padding: 8px; text-align: right; border-bottom: 2px solid #ccc; }
            .report-table td { padding: 8px; font-size: 13px; border-bottom: 1px solid #ddd; }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="company-name">${settings.name}</div>
            <div class="title">ميزان المراجعة بالأرصدة الدفترية النهائية</div>
            <div>تاريخ الإصدار: $dateStr</div>
        </div>
        <table class="report-table">
            <thead>
                <tr>
                    <th>اسم الحساب المالي</th>
                    <th style="text-align: left;">مدين ($currency)</th>
                    <th style="text-align: left;">دائن ($currency)</th>
                </tr>
            </thead>
            <tbody>
                $rows
            </tbody>
        </table>
    </body>
    </html>
    """.trimIndent()
}

fun buildAllSubAccountsHtml(settings: EnterpriseSetting, dateStr: String, subAccounts: List<Account>, currency: String = "ر.ي"): String {
    val rows = subAccounts.joinToString("") { acc ->
        """
        <tr>
            <td>${acc.code}</td>
            <td>${acc.name}</td>
            <td>${getArabicAccountType(acc.type)}</td>
            <td style="text-align: left; font-weight: bold;">${acc.balance} $currency</td>
        </tr>
        """
    }

    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
            .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
            .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
            .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
            .report-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
            .report-table th { background: #f0f4f8; padding: 8px; text-align: right; border-bottom: 2px solid #ccc; }
            .report-table td { padding: 8px; font-size: 13px; border-bottom: 1px solid #ddd; }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="company-name">${settings.name}</div>
            <div class="title">تقرير كافة الحسابات الفرعية والأرصدة الدفترية</div>
            <div>تاريخ الإصدار: $dateStr</div>
        </div>
        <table class="report-table">
            <thead>
                <tr>
                    <th>كود الحساب</th>
                    <th>اسم الحساب الفرعي</th>
                    <th>النوع</th>
                    <th style="text-align: left;">الرصيد الدفتري ($currency)</th>
                </tr>
            </thead>
            <tbody>
                $rows
            </tbody>
        </table>
    </body>
    </html>
    """.trimIndent()
}

fun buildSingleSubAccountHtml(settings: EnterpriseSetting, dateStr: String, acc: Account, lines: List<JournalEntryLine>, entries: List<com.example.data.model.JournalEntry>, currency: String = "ر.ي"): String {
    val rows = lines.joinToString("") { line ->
        val entry = entries.firstOrNull { it.id == line.journalEntryId }
        """
        <tr>
            <td>${entry?.entryNumber ?: "#${line.journalEntryId}"}</td>
            <td>${entry?.description ?: line.description.ifEmpty { "حركة حساب" }}</td>
            <td style="text-align: left; color: #2E7D32;">${if (line.debit > 0) "${line.debit} $currency" else "-"}</td>
            <td style="text-align: left; color: #C62828;">${if (line.credit > 0) "${line.credit} $currency" else "-"}</td>
        </tr>
        """
    }

    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: system-ui, sans-serif; margin: 20px; color: #333; }
            .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0061A4; padding-bottom: 10px; }
            .company-name { font-size: 22px; font-weight: bold; color: #0061A4; }
            .title { font-size: 18px; font-weight: bold; margin: 5px 0; }
            .acc-card { background: #f0f4f8; padding: 12px; border-radius: 8px; margin-bottom: 20px; }
            .report-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
            .report-table th { background: #eef2f6; padding: 8px; text-align: right; border-bottom: 2px solid #ccc; }
            .report-table td { padding: 8px; font-size: 13px; border-bottom: 1px solid #ddd; }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="company-name">${settings.name}</div>
            <div class="title">كشف حركة دفتر الحساب الفرعي التفصيلي</div>
            <div>تاريخ الإصدار: $dateStr</div>
        </div>
        <div class="acc-card">
            <div><strong>اسم الحساب:</strong> ${acc.name} (${acc.code})</div>
            <div><strong>نوع الحساب:</strong> ${getArabicAccountType(acc.type)}</div>
            <div><strong>الرصيد الدفتري النهائي:</strong> <span style="font-size: 16px; font-weight: bold; color: #0061A4;">${acc.balance} $currency</span></div>
        </div>
        <table class="report-table">
            <thead>
                <tr>
                    <th>رقم مرجع القيد</th>
                    <th>البيان / الوصف</th>
                    <th style="text-align: left;">مدين (+)</th>
                    <th style="text-align: left;">دائن (-)</th>
                </tr>
            </thead>
            <tbody>
                $rows
            </tbody>
        </table>
    </body>
    </html>
    """.trimIndent()
}
