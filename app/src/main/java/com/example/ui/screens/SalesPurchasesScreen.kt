package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.Account
import com.example.data.model.Contact
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.Item
import com.example.data.model.ItemUnit
import com.example.util.PhoneUtils
import com.example.ui.viewmodel.AppViewModel
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesPurchasesScreen(viewModel: AppViewModel, mode: String, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val itemUnits by viewModel.itemUnits.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currencies by viewModel.currencies.collectAsState(initial = emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedAccount by remember { mutableStateOf<Account?>(null) }

    val cashAndBankAccounts = remember(accounts) {
        accounts.filter {
            it.type == "ASSETS" && (it.name.contains("الصندوق") || it.name.contains("البنك") || it.name.contains("خزينة") || it.code == "1101" || it.code == "1102")
        }
    }
    LaunchedEffect(cashAndBankAccounts) {
        if (selectedAccount == null && cashAndBankAccounts.isNotEmpty()) {
            selectedAccount = cashAndBankAccounts.firstOrNull { it.name.contains("الصندوق") } ?: cashAndBankAccounts.firstOrNull()
        }
    }

    val isSale = mode == "SALE" || mode == "SALE_RETURN"
    val isReturn = mode == "SALE_RETURN" || mode == "PURCHASE_RETURN"
    var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showInvoicesListDialog by remember { mutableStateOf(false) }

    val screenTitle = when {
        editingInvoice != null -> "تعديل مستند رقم ${editingInvoice!!.invoiceNumber}"
        mode == "SALE" -> "فاتورة مبيعات جديدة"
        mode == "PURCHASE" -> "فاتورة مشتريات جديدة"
        mode == "SALE_RETURN" -> "مرتجع مبيعات جديد"
        mode == "PURCHASE_RETURN" -> "إشعار خصم / مرتجع مشتريات للمورد"
        else -> "فاتورة جديدة"
    }

    // Invoice State
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    val cartItems = remember { mutableStateListOf<Pair<Item, InvoiceItem>>() }

    // Purchase & Return Specific States
    var supplierRefInvoiceNum by remember { mutableStateOf("") } // رقم فاتورة المورد الخارجي
    var originalInvoiceNum by remember { mutableStateOf("") } // رقم الفاتورة الأصلية للمرتجع
    var returnReason by remember { mutableStateOf(if (mode == "PURCHASE_RETURN") "عيب تصنيعي من المصنع" else "رغبة العميل / زيادة بالطلب") } // سبب المرتجع
    var returnedStockCondition by remember { mutableStateOf("صالح لإعادة البيع") } // حالة المخزون المرتجع
    var shippingExpensesInput by remember { mutableStateOf("0.0") } // مصاريف الشحن والنقل
    var updateItemCostOnPurchase by remember { mutableStateOf(true) } // خيار تحديث تكلفة الشراء بالكتالوج

    // Discount and Tax Configuration
    var discountInput by remember { mutableStateOf("0.0") }
    var discountType by remember { mutableStateOf("FIXED") } // "FIXED" (مبلغ) or "PERCENT" (%)
    var taxType by remember { mutableStateOf("EXEMPT") } // "EXEMPT" (بدون ضريبة 0% - افتراضي), "EXCLUSIVE" (غير شاملة 15%), "INCLUSIVE" (شاملة 15%)
    var paymentMethod by remember { mutableStateOf("نقدي") } // "نقدي", "شبكة", "آجل"
    var defaultPriceTier by remember { mutableStateOf("RETAIL") } // "RETAIL" (تجزئة), "WHOLESALE" (جملة), "SPECIAL" (خاص)

    // Multi-Currency Configuration
    var selectedCurrencyCode by remember { mutableStateOf(settings.currency) }
    var exchangeRateInput by remember { mutableStateOf("1.0") }

    // Check for Pending Invoice Edit Request from ViewModel
    LaunchedEffect(viewModel.pendingEditingInvoice) {
        val pendingInv = viewModel.pendingEditingInvoice
        if (pendingInv != null) {
            viewModel.pendingEditingInvoice = null
            editingInvoice = pendingInv
        }
    }

    // Load Invoice Details When Editing
    LaunchedEffect(editingInvoice) {
        editingInvoice?.let { inv ->
            selectedContact = contacts.find { it.id == inv.contactId }
            paymentMethod = inv.paymentMethod
            discountInput = inv.discount.toString()
            discountType = "FIXED" // Simplify parsing
            taxType = if (inv.tax > 0) "EXCLUSIVE" else "EXEMPT"
            selectedCurrencyCode = inv.currencyCode
            exchangeRateInput = inv.exchangeRate.toString()
            
            // Fetch items
            val invItems = viewModel.repository.invoiceDao.getItemsForInvoice(inv.id)
            cartItems.clear()
            for (invItem in invItems) {
                val item = items.find { it.id == invItem.itemId }
                if (item != null) {
                    cartItems.add(Pair(item, invItem))
                }
            }
        }
    }

    // Search and add item
    var searchItemText by remember { mutableStateOf("") }
    var showItemDropdown by remember { mutableStateOf(false) }

    // Barcode Scanner Launcher for Invoices
    val barcodeScanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents.trim()
            val matchedItem = items.find { it.barcode == scannedCode || it.code == scannedCode }
            if (matchedItem != null) {
                val itemPrice = if (isSale) {
                    when (defaultPriceTier) {
                        "WHOLESALE" -> if (matchedItem.wholesalePrice > 0) matchedItem.wholesalePrice else matchedItem.salePrice
                        "SPECIAL" -> if (matchedItem.specialPrice > 0) matchedItem.specialPrice else matchedItem.salePrice
                        else -> matchedItem.salePrice
                    }
                } else matchedItem.purchasePrice

                val existingIndex = cartItems.indexOfFirst { it.first.id == matchedItem.id }
                if (existingIndex >= 0) {
                    val existing = cartItems[existingIndex]
                    val updatedInvoiceItem = existing.second.copy(
                        quantity = existing.second.quantity + 1.0,
                        total = (existing.second.quantity + 1.0) * existing.second.unitPrice
                    )
                    cartItems[existingIndex] = Pair(existing.first, updatedInvoiceItem)
                } else {
                    val invoiceItem = InvoiceItem(
                        invoiceId = 0L,
                        itemId = matchedItem.id,
                        unitName = matchedItem.unit,
                        conversionFactor = 1.0,
                        quantity = 1.0,
                        unitPrice = itemPrice,
                        total = itemPrice
                    )
                    cartItems.add(Pair(matchedItem, invoiceItem))
                }
                Toast.makeText(context, "✅ تم إضافة ${matchedItem.name} بالفاتورة عبر الباركود", Toast.LENGTH_SHORT).show()
            } else {
                searchItemText = scannedCode
                showItemDropdown = true
                Toast.makeText(context, "لم يتم العثور على صنف بالباركود: $scannedCode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Item Configurator Panel States (صنف، وحدة، عدد، سعر)
    var selectedItemToAdd by remember { mutableStateOf<Item?>(null) }
    var selectedUnitNameToAdd by remember { mutableStateOf("") }
    var selectedConversionFactorToAdd by remember { mutableDoubleStateOf(1.0) }
    var qtyToAddInput by remember { mutableStateOf("1") }
    var priceToAddInput by remember { mutableStateOf("0.0") }
    var expandedUnitsDropdownToAdd by remember { mutableStateOf(false) }


    // Dialog & Error handling
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedInvoiceNumber by remember { mutableStateOf("") }
    var savedInvoiceTotal by remember { mutableDoubleStateOf(0.0) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Mathematical calculations
    val subTotal = cartItems.sumOf { (_, invItem) ->
        invItem.unitPrice * invItem.quantity
    }

    val rawDiscount = discountInput.toDoubleOrNull() ?: 0.0
    val discountVal = if (discountType == "PERCENT") (subTotal * rawDiscount / 100.0) else rawDiscount

    val shippingVal = if (!isSale && !isReturn) (shippingExpensesInput.toDoubleOrNull() ?: 0.0) else 0.0
    val netAfterDiscount = (subTotal - discountVal + shippingVal).coerceAtLeast(0.0)

    val (taxValue, invoiceTotal) = remember(subTotal, discountVal, shippingVal, taxType) {
        when (taxType) {
            "EXEMPT" -> Pair(0.0, netAfterDiscount)
            "INCLUSIVE" -> {
                val base = netAfterDiscount / 1.15
                val tax = netAfterDiscount - base
                Pair(tax, netAfterDiscount)
            }
            else -> { // EXCLUSIVE (غير شاملة)
                val tax = netAfterDiscount * 0.15
                Pair(tax, netAfterDiscount + tax)
            }
        }
    }

    // Credit Limit Check calculation (for sales)
    val projectedCredit = remember(selectedContact, invoiceTotal, paymentMethod) {
        if (selectedContact != null && paymentMethod == "آجل") {
            selectedContact!!.balance + invoiceTotal
        } else 0.0
    }
    val isCreditLimitExceeded = remember(selectedContact, projectedCredit) {
        isSale && !isReturn && selectedContact != null && selectedContact!!.creditLimit > 0 && projectedCredit > selectedContact!!.creditLimit
    }

    var showCalculatorDialog by remember { mutableStateOf(false) }

    if (showCalculatorDialog) {
        CalculatorDialog(onDismiss = { showCalculatorDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalculatorDialog = true }) {
                        Icon(Icons.Default.Calculate, contentDescription = "آلة حاسبة", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (editingInvoice != null) {
                        TextButton(onClick = { 
                            editingInvoice = null 
                            cartItems.clear()
                            selectedContact = null
                        }) {
                            Text("إلغاء التعديل", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = { showInvoicesListDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "بحث وتعديل الفواتير", tint = MaterialTheme.colorScheme.primary)
                        }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Contact selection card (Customer / Supplier)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSale) "العميل المشتري" else "المورد البائع / المرتجع إليه",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (selectedContact != null) {
                            Text(
                                text = "الرصيد الدفتري: ${selectedContact!!.balance} ر.ي",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedContact!!.balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    val filteredContacts = contacts.filter { it.type == (if (isSale) "CUSTOMER" else "SUPPLIER") }
                    var expandedContacts by remember { mutableStateOf(false) }
                    var contactSearchText by remember { mutableStateOf("") }

                    Box {
                        OutlinedButton(onClick = { expandedContacts = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedContact?.name ?: if (isSale) "اختر العميل أو ابحث عنه..." else "اختر المورد أو ابحث عنه *", fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                            }
                        }

                        DropdownMenu(
                            expanded = expandedContacts,
                            onDismissRequest = { expandedContacts = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            OutlinedTextField(
                                value = contactSearchText,
                                onValueChange = { contactSearchText = it },
                                placeholder = { Text("ابحث بالاسم أو رقم الهاتف...", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                                singleLine = true
                            )
                            Divider()

                            val matchingContacts = filteredContacts.filter {
                                it.name.contains(contactSearchText, ignoreCase = true) ||
                                it.phone.contains(contactSearchText, ignoreCase = true)
                            }

                            if (matchingContacts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("لا يوجد أسماء مطابقة للبحث", fontSize = 11.sp, color = Color.Gray) },
                                    onClick = {}
                                )
                            } else {
                                matchingContacts.forEach { contact ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(contact.name, fontWeight = FontWeight.Bold)
                                                Text("رصيد: ${contact.balance} ر.ي", fontSize = 11.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            selectedContact = contact
                                            expandedContacts = false
                                            contactSearchText = ""
                                        }
                                    )
                                }
                            }
                        }
                    }


                    // Credit Limit Alert Badge (Sales)
                    if (isCreditLimitExceeded) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "تحذير", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "تنبيه محاسبي: تجاوز العميل السقف الائتماني! (الحد: ${selectedContact!!.creditLimit} ر.ي | المتوقع: ${projectedCredit} ر.ي)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }

                    // Purchase Header Extra Fields
                    if (!isSale && !isReturn) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = supplierRefInvoiceNum,
                                onValueChange = { supplierRefInvoiceNum = it },
                                label = { Text("رقم فاتورة المورد الورقية (اختياري)") },
                                modifier = Modifier.weight(1.3f),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = updateItemCostOnPurchase,
                                    onCheckedChange = { updateItemCostOnPurchase = it }
                                )
                                Text("تحديث سعر التكلفة بالكتالوج", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Returns Extra Fields (Sales Return / Purchase Return)
                    if (isReturn) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = originalInvoiceNum,
                                onValueChange = { originalInvoiceNum = it },
                                label = { Text(if (mode == "PURCHASE_RETURN") "رقم فاتورة الشراء الأصلية (اختياري)" else "رقم الفاتورة الأصلية للمبيعات (اختياري)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) }
                            )

                            Text("سبب الإرجاع والارتجاع المحاسبي:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val reasons = if (mode == "PURCHASE_RETURN") {
                                listOf("عيب تصنيعي من المصنع", "تجاوز تاريخ الصلاحية", "عدم المطابقة مع أمر الشراء", "اتفاق إرجاع للمورد")
                            } else {
                                listOf("رغبة العميل / زيادة بالطلب", "عيب تصنيعي / تالف", "غير مطابق للمواصفات", "خطأ بالفاتورة الأصلية")
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                reasons.forEach { r ->
                                    FilterChip(
                                        selected = returnReason == r,
                                        onClick = { returnReason = r },
                                        label = { Text(r, fontSize = 9.sp) }
                                    )
                                }
                            }

                            if (mode == "SALE_RETURN") {
                                Text("حالة البضاعة المرتجعة للمخزون:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = returnedStockCondition == "صالح لإعادة البيع",
                                        onClick = { returnedStockCondition = "صالح لإعادة البيع" },
                                        label = { Text("🟢 صالح للمخزن", fontSize = 10.sp, maxLines = 1) }
                                    )
                                    FilterChip(
                                        selected = returnedStockCondition == "تالف / إتلاف مخزني",
                                        onClick = { returnedStockCondition = "تالف / إتلاف مخزني" },
                                        label = { Text("🔴 تالف / إتلاف", fontSize = 10.sp, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Payment Method & Account Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isReturn) (if (mode == "PURCHASE_RETURN" || mode == "SALE_RETURN") "طريقة استرداد/خصم المبلغ" else "طريقة إرجاع المبلغ") else "طريقة الدفع وقنوات السداد",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Payment Method Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val methods = listOf("نقدي", "شبكة", "آجل")
                        methods.forEach { method ->
                            val isSelected = paymentMethod == method
                            val methodText = when {
                                isReturn && mode == "PURCHASE_RETURN" && method == "آجل" -> "آجل (المورد)"
                                isReturn && mode == "SALE_RETURN" && method == "آجل" -> "آجل (العميل)"
                                else -> method
                            }
                            Button(
                                onClick = { paymentMethod = method },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(methodText, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }


                    // Price Tier Selector (for Sales)
                    if (isSale && !isReturn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("فئة تسعير الصنف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = defaultPriceTier == "RETAIL",
                                    onClick = { defaultPriceTier = "RETAIL" },
                                    label = { Text("تجزئة", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = defaultPriceTier == "WHOLESALE",
                                    onClick = { defaultPriceTier = "WHOLESALE" },
                                    label = { Text("جملة", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = defaultPriceTier == "SPECIAL",
                                    onClick = { defaultPriceTier = "SPECIAL" },
                                    label = { Text("خاص", fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Account Selector (الصندوق / البنك)
                    if (paymentMethod == "نقدي" || paymentMethod == "شبكة") {
                        var expandedAccountsDropdown by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isReturn) "خزينة استلام/إرجاع النقدية " else "الحساب المالي المتأثر ", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("*", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                                if (selectedAccount != null) {
                                    Text("الرصيد: ${selectedAccount!!.balance} ر.ي", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { expandedAccountsDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    border = BorderStroke(1.dp, if (selectedAccount == null) Color.Red else MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedAccount?.name ?: "اختر حساب الصندوق أو البنك")
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم")
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedAccountsDropdown,
                                    onDismissRequest = { expandedAccountsDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    cashAndBankAccounts.forEach { account ->
                                        DropdownMenuItem(
                                            text = { Text("${account.name} (رصيد: ${account.balance} ر.ي)") },
                                            onClick = {
                                                selectedAccount = account
                                                expandedAccountsDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search item and quick add
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (mode) {
                            "PURCHASE_RETURN" -> "إضافة بضائع مرجعة للمورد"
                            "SALE_RETURN" -> "إضافة سلع مرجعة من العميل"
                            "PURCHASE" -> "إضافة بضائع للتوريد"
                            else -> "إضافة سلع للفاتورة"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = searchItemText,
                        onValueChange = {
                            searchItemText = it
                            showItemDropdown = true
                        },
                        placeholder = { Text("اختر من القائمة أو ابحث بالاسم/الكود...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val options = com.journeyapps.barcodescanner.ScanOptions()
                                    options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.ALL_CODE_TYPES)
                                    options.setPrompt("امسح باركود الصنف للإضافة الفورية للفاتورة")
                                    options.setBeepEnabled(true)
                                    options.setBarcodeImageEnabled(true)
                                    barcodeScanLauncher.launch(options)
                                }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح باركود الصنف", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { showItemDropdown = !showItemDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "قائمة الأصناف")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                val exactMatch = items.find { it.barcode == searchItemText.trim() || it.code == searchItemText.trim() }
                                if (exactMatch != null) {
                                    val itemPrice = if (isSale) {
                                        when (defaultPriceTier) {
                                            "WHOLESALE" -> if (exactMatch.wholesalePrice > 0) exactMatch.wholesalePrice else exactMatch.salePrice
                                            "SPECIAL" -> if (exactMatch.specialPrice > 0) exactMatch.specialPrice else exactMatch.salePrice
                                            else -> exactMatch.salePrice
                                        }
                                    } else exactMatch.purchasePrice

                                    val existingIndex = cartItems.indexOfFirst { it.first.id == exactMatch.id }
                                    if (existingIndex >= 0) {
                                        val existing = cartItems[existingIndex]
                                        val updated = existing.second.copy(
                                            quantity = existing.second.quantity + 1.0,
                                            total = (existing.second.quantity + 1.0) * existing.second.unitPrice
                                        )
                                        cartItems[existingIndex] = Pair(existing.first, updated)
                                    } else {
                                        cartItems.add(Pair(exactMatch, InvoiceItem(
                                            invoiceId = 0L, itemId = exactMatch.id, unitName = exactMatch.unit, conversionFactor = 1.0,
                                            quantity = 1.0, unitPrice = itemPrice, total = itemPrice
                                        )))
                                    }
                                    Toast.makeText(context, "✅ تم إضافة ${exactMatch.name}", Toast.LENGTH_SHORT).show()
                                    searchItemText = ""
                                    showItemDropdown = false
                                } else {
                                    Toast.makeText(context, "لم يتم العثور على صنف مطابق تماماً للبحث", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    )

                    if (showItemDropdown) {
                        val matchingItems = if (searchItemText.isBlank()) {
                            items
                        } else {
                            items.filter {
                                it.name.contains(searchItemText, ignoreCase = true) ||
                                it.code.contains(searchItemText, ignoreCase = true) ||
                                it.barcode.contains(searchItemText, ignoreCase = true)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الأصناف المتاحة للانتخاب (${matchingItems.size}):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", modifier = Modifier.size(16.dp).clickable { showItemDropdown = false })
                            }
                            Divider()
                            if (matchingItems.isEmpty()) {
                                Text("لا توجد أصناف مطابقة للبحث", modifier = Modifier.padding(8.dp), color = Color.Gray, fontSize = 11.sp)
                            } else {
                                matchingItems.take(15).forEach { item ->

                                    val itemPrice = if (isSale) {
                                        when (defaultPriceTier) {
                                            "WHOLESALE" -> if (item.wholesalePrice > 0) item.wholesalePrice else item.salePrice
                                            "SPECIAL" -> if (item.specialPrice > 0) item.specialPrice else item.salePrice
                                            else -> item.salePrice
                                        }
                                    } else item.purchasePrice

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedItemToAdd = item
                                                selectedUnitNameToAdd = item.unit
                                                selectedConversionFactorToAdd = 1.0
                                                qtyToAddInput = "1"
                                                priceToAddInput = itemPrice.toString()
                                                searchItemText = item.name
                                                showItemDropdown = false
                                            }
                                            .padding(8.dp),

                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold)
                                            Text("متوفر بالدفتر: ${item.currentQuantity} ${item.unit}", fontSize = 10.sp, color = if (item.currentQuantity <= item.minLimit) Color.Red else Color.Gray)
                                        }
                                        Text("${itemPrice} ر.ي", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Item Configurator Panel (اختيار الصنف + الوحدة + العدد + السعر)
                    if (selectedItemToAdd != null) {
                        val selItem = selectedItemToAdd!!
                        val availableSubUnits = itemUnits.filter { it.itemId == selItem.id }
                        val allSelectableUnits = listOf(
                            ItemUnit(itemId = selItem.id, unitName = selItem.unit, conversionFactor = 1.0, purchasePrice = selItem.purchasePrice, salePrice = selItem.salePrice)
                        ) + availableSubUnits

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Header: Item Title & Dismiss
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Category, contentDescription = "صنف", tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text(selItem.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("المخزون المتوفر: ${selItem.currentQuantity} ${selItem.unit}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedItemToAdd = null
                                            searchItemText = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = Color.Gray)
                                    }
                                }

                                // Row 1: Unit Dropdown & Price Input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Unit Dropdown
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { expandedUnitsDropdownToAdd = true },
                                            modifier = Modifier.fillMaxWidth().height(60.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("الوحدة: $selectedUnitNameToAdd", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "وحدة")
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = expandedUnitsDropdownToAdd,
                                            onDismissRequest = { expandedUnitsDropdownToAdd = false }
                                        ) {
                                            allSelectableUnits.forEach { unitObj ->
                                                val unitPrice = if (isSale) {
                                                    when (defaultPriceTier) {
                                                        "WHOLESALE" -> if (unitObj.salePrice > 0) unitObj.salePrice else selItem.wholesalePrice
                                                        "SPECIAL" -> if (unitObj.salePrice > 0) unitObj.salePrice else selItem.specialPrice
                                                        else -> if (unitObj.salePrice > 0) unitObj.salePrice else selItem.salePrice
                                                    }
                                                } else {
                                                    if (unitObj.purchasePrice > 0) unitObj.purchasePrice else selItem.purchasePrice
                                                }

                                                DropdownMenuItem(
                                                    text = { Text("${unitObj.unitName} (${unitPrice} ر.ي) [معامل: ${unitObj.conversionFactor}]", fontSize = 12.sp) },
                                                    onClick = {
                                                        selectedUnitNameToAdd = unitObj.unitName
                                                        selectedConversionFactorToAdd = unitObj.conversionFactor
                                                        priceToAddInput = unitPrice.toString()
                                                        expandedUnitsDropdownToAdd = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Price Input
                                    OutlinedTextField(
                                        value = priceToAddInput,
                                        onValueChange = { priceToAddInput = it },
                                        label = { Text("السعر (ر.ي)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f).height(60.dp),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }

                                if (selectedConversionFactorToAdd > 1.0) {
                                    val currentEnteredPrice = priceToAddInput.toDoubleOrNull() ?: 0.0
                                    val pieceCostCalculated = if (selectedConversionFactorToAdd > 0) currentEnteredPrice / selectedConversionFactorToAdd else currentEnteredPrice
                                    Text(
                                        text = "💡 تكلفة الحبة الواحدة الأساسية: ${String.format("%.2f", pieceCostCalculated)} ر.ي",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                // Row 2: Quantity Input & Total & Add Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quantity Selector (+/- & TextField)
                                    Row(
                                        modifier = Modifier.weight(1.2f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val current = qtyToAddInput.toDoubleOrNull() ?: 1.0
                                                if (current > 1) {
                                                    qtyToAddInput = (current - 1).toInt().toString()
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "ناقص", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        OutlinedTextField(
                                            value = qtyToAddInput,
                                            onValueChange = { qtyToAddInput = it },
                                            label = { Text("العدد", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.weight(1f).height(60.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )

                                        IconButton(
                                            onClick = {
                                                val current = qtyToAddInput.toDoubleOrNull() ?: 0.0
                                                qtyToAddInput = (current + 1).toInt().toString()
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "زائد", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    // Add Button
                                    val parsedQty = qtyToAddInput.toDoubleOrNull() ?: 1.0
                                    val parsedPrice = priceToAddInput.toDoubleOrNull() ?: 0.0
                                    val itemSubtotal = parsedQty * parsedPrice

                                    Button(
                                        onClick = {
                                            // 1. Validation: Sell Below Cost Check
                                            if (isSale && !isReturn && !settings.allowSellBelowCost) {
                                                val unitCostPrice = selItem.purchasePrice * selectedConversionFactorToAdd
                                                if (parsedPrice < unitCostPrice) {
                                                    Toast.makeText(
                                                        context,
                                                        "⚠️ النظام يمنع البيع بأقل من التكلفة! (التكلفة: $unitCostPrice ر.ي | السعر المدخل: $parsedPrice ر.ي)",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    return@Button
                                                }
                                            }

                                            // 2. Validation: Negative Stock Check
                                            if (isSale && !isReturn && !settings.allowNegativeStock) {
                                                val requestedTotalQtyInBaseUnits = parsedQty * selectedConversionFactorToAdd
                                                val currentCartQtyForThisItemInBaseUnits = cartItems
                                                    .filter { it.first.id == selItem.id }
                                                    .sumOf { it.second.quantity * it.second.conversionFactor }

                                                val projectedTotalInBaseUnits = currentCartQtyForThisItemInBaseUnits + requestedTotalQtyInBaseUnits

                                                if (projectedTotalInBaseUnits > selItem.currentQuantity) {
                                                    Toast.makeText(
                                                        context,
                                                        "⚠️ الكمية المطلوبة تسبب رصيداً سالباً بالمخزون! (المتاح: ${selItem.currentQuantity} ${selItem.unit})",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    return@Button
                                                }
                                            }

                                            val newItem = InvoiceItem(
                                                invoiceId = 0L,
                                                itemId = selItem.id,
                                                quantity = parsedQty,
                                                unitPrice = parsedPrice,
                                                total = itemSubtotal,
                                                unitName = selectedUnitNameToAdd,
                                                conversionFactor = selectedConversionFactorToAdd
                                            )

                                            val existingIndex = cartItems.indexOfFirst { 
                                                it.first.id == selItem.id && it.second.unitName == selectedUnitNameToAdd 
                                            }

                                            if (existingIndex >= 0) {
                                                val existing = cartItems[existingIndex]
                                                val newQty = existing.second.quantity + parsedQty
                                                cartItems[existingIndex] = existing.copy(
                                                    second = existing.second.copy(
                                                        quantity = newQty,
                                                        total = newQty * parsedPrice
                                                    )
                                                )
                                            } else {
                                                cartItems.add(Pair(selItem, newItem))
                                            }

                                            // Reset panel for next item addition
                                            selectedItemToAdd = null
                                            searchItemText = ""
                                            qtyToAddInput = "1"
                                            priceToAddInput = "0.0"
                                        },
                                        modifier = Modifier.weight(1.3f).height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = "إضافة", modifier = Modifier.size(18.dp))
                                            Text("إضافة (${String.format("%.1f", itemSubtotal)} ر.ي)", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Invoice Cart items
            Text(if (isReturn) "السلع المرتجعة (${cartItems.size})" else "السلع المضافة للفاتورة (${cartItems.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Box(modifier = Modifier.weight(1f)) {
                if (cartItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("السلة فارغة. ابحث عن صنف وأضفه للبدء.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cartItems) { (item, invItem) ->
                            var expandedUnitDropdown by remember { mutableStateOf(false) }
                            val availableUnits = itemUnits.filter { it.itemId == item.id }
                            val selectableUnits = listOf(
                                ItemUnit(itemId = item.id, unitName = item.unit, conversionFactor = 1.0, purchasePrice = item.purchasePrice, salePrice = item.salePrice)
                            ) + availableUnits

                            val isStockInsufficient = isSale && !isReturn && (invItem.quantity > item.currentQuantity)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isStockInsufficient) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isStockInsufficient) Color(0xFFFCA5A5) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        if (isStockInsufficient) {
                                            Text("(المخزون المتوفر: ${item.currentQuantity})", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Purchase cost comparison indicator
                                    if (!isSale && !isReturn) {
                                        val costDiff = invItem.unitPrice - item.purchasePrice
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("التكلفة السابقة: ${item.purchasePrice} ر.ي", fontSize = 10.sp, color = Color.Gray)
                                            if (costDiff > 0) {
                                                Text("▲ (+${String.format("%.2f", costDiff)})", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                            } else if (costDiff < 0) {
                                                Text("▼ (${String.format("%.2f", costDiff)})", fontSize = 9.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box {
                                        Surface(
                                            modifier = Modifier.clickable { expandedUnitDropdown = true },
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${invItem.unitPrice} ر.ي / ${invItem.unitName}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = "تغيير الوحدة",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = expandedUnitDropdown,
                                            onDismissRequest = { expandedUnitDropdown = false }
                                        ) {
                                            selectableUnits.forEach { selectableUnit ->
                                                val unitPrice = if (isSale) selectableUnit.salePrice else selectableUnit.purchasePrice
                                                DropdownMenuItem(
                                                    text = {
                                                        Text("${selectableUnit.unitName} (${unitPrice} ر.ي) [تحويل: ${selectableUnit.conversionFactor}]")
                                                    },
                                                    onClick = {
                                                        val index = cartItems.indexOfFirst { it.first.id == item.id && it.second.unitName == invItem.unitName }
                                                        if (index >= 0) {
                                                            val updatedInvItem = invItem.copy(
                                                                unitName = selectableUnit.unitName,
                                                                conversionFactor = selectableUnit.conversionFactor,
                                                                unitPrice = unitPrice,
                                                                total = invItem.quantity * unitPrice
                                                            )
                                                            cartItems[index] = Pair(item, updatedInvItem)
                                                        }
                                                        expandedUnitDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    var priceInputState by remember(invItem.unitName) { mutableStateOf(if (invItem.unitPrice == 0.0) "" else invItem.unitPrice.toString()) }
                                    OutlinedTextField(
                                        value = priceInputState,
                                        onValueChange = { inputVal ->
                                            priceInputState = inputVal
                                            val newPrice = inputVal.toDoubleOrNull() ?: 0.0
                                            val index = cartItems.indexOfFirst { it.first.id == item.id && it.second.unitName == invItem.unitName }
                                            if (index >= 0) {
                                                cartItems[index] = Pair(item, invItem.copy(unitPrice = newPrice, total = invItem.quantity * newPrice))
                                            }
                                        },
                                        label = { Text(if (isSale) "سعر المرتجع/البيع" else "سعر التكلفة/الشراء", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.width(150.dp).height(58.dp),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val index = cartItems.indexOfFirst { it.first.id == item.id && it.second.unitName == invItem.unitName }
                                            if (index >= 0) {
                                                if (invItem.quantity > 1) {
                                                    val newQty = invItem.quantity - 1
                                                    cartItems[index] = Pair(item, invItem.copy(quantity = newQty, total = newQty * invItem.unitPrice))
                                                } else {
                                                    cartItems.removeAt(index)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = "ناقص") }

                                    var qtyInputState by remember(invItem.unitName) { mutableStateOf(if (invItem.quantity == 0.0) "" else if (invItem.quantity % 1.0 == 0.0) invItem.quantity.toInt().toString() else invItem.quantity.toString()) }
                                    OutlinedTextField(
                                        value = qtyInputState,
                                        onValueChange = { inputVal ->
                                            qtyInputState = inputVal
                                            val newQty = inputVal.toDoubleOrNull() ?: 0.0
                                            val index = cartItems.indexOfFirst { it.first.id == item.id && it.second.unitName == invItem.unitName }
                                            if (index >= 0) {
                                                cartItems[index] = Pair(item, invItem.copy(quantity = newQty, total = newQty * invItem.unitPrice))
                                            }
                                        },
                                        modifier = Modifier.width(60.dp).height(48.dp),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                    IconButton(
                                        onClick = {
                                            val index = cartItems.indexOfFirst { it.first.id == item.id && it.second.unitName == invItem.unitName }
                                            if (index >= 0) {
                                                val newQty = invItem.quantity + 1
                                                cartItems[index] = Pair(item, invItem.copy(quantity = newQty, total = newQty * invItem.unitPrice))
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.AddCircleOutline, contentDescription = "زائد") }
                                }
                            }
                        }
                    }
                }
            }

            // Calculations and summary box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المجموع الفرعي:", fontSize = 12.sp)
                        Text("$subTotal ر.ي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Freight & Shipping expenses (for Purchases)
                    if (!isSale && !isReturn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مصاريف النقل والشحن (+):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = shippingExpensesInput,
                                onValueChange = { shippingExpensesInput = it },
                                modifier = Modifier.width(110.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }

                    // Discount row (Fixed vs Percent)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الخصم:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            FilterChip(
                                selected = discountType == "FIXED",
                                onClick = { discountType = "FIXED" },
                                label = { Text("مبلغ", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = discountType == "PERCENT",
                                onClick = { discountType = "PERCENT" },
                                label = { Text("%", fontSize = 10.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { discountInput = it },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }


                    // Tax Configuration row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الضريبة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FilterChip(
                                selected = taxType == "EXEMPT",
                                onClick = { taxType = "EXEMPT" },
                                label = { Text("بدون ضريبة (0%)", fontSize = 9.sp) }
                            )
                            FilterChip(
                                selected = taxType == "EXCLUSIVE",
                                onClick = { taxType = "EXCLUSIVE" },
                                label = { Text("غير شاملة (15%)", fontSize = 9.sp) }
                            )
                            FilterChip(
                                selected = taxType == "INCLUSIVE",
                                onClick = { taxType = "INCLUSIVE" },
                                label = { Text("شاملة (15%)", fontSize = 9.sp) }
                            )
                        }

                        Text(String.format("%.2f", taxValue) + " $selectedCurrencyCode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Currency & Exchange Rate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("عملة المستند وسعر الصرف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            var expandedCurrDropdown by remember { mutableStateOf(false) }
                            val availableCurrencies = listOf("ر.ي", "ر.س", "$")
                            Box {
                                OutlinedButton(
                                    onClick = { expandedCurrDropdown = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(selectedCurrencyCode, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "عملة", modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = expandedCurrDropdown,
                                    onDismissRequest = { expandedCurrDropdown = false }
                                ) {
                                    availableCurrencies.forEach { currCode ->
                                        DropdownMenuItem(
                                            text = { Text(when (currCode) { "ر.ي" -> "الريال اليمني (ر.ي)" "ر.س" -> "الريال السعودي (ر.س)" else -> "الدولار الأمريكي ($)" }) },
                                            onClick = {
                                                selectedCurrencyCode = currCode
                                                if (currCode == "ر.ي") exchangeRateInput = "1.0"
                                                expandedCurrDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (selectedCurrencyCode != "ر.ي") {
                                OutlinedTextField(
                                    value = exchangeRateInput,
                                    onValueChange = { exchangeRateInput = it },
                                    label = { Text("سعر الصرف", fontSize = 9.sp) },
                                    modifier = Modifier.width(85.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isReturn) "إجمالي مبلغ المرتجع النهائي:" else "الإجمالي الكلي للفاتورة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${String.format("%.2f", invoiceTotal)} $selectedCurrencyCode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Save Invoice Button
            Button(
                onClick = {
                    if (cartItems.isEmpty()) {
                        validationError = "لا يمكن حفظ مستند فارغ. يرجى إضافة أصناف أولاً."
                        return@Button
                    }
                    val hasInvalidPrice = cartItems.any { it.second.unitPrice <= 0.0 }
                    if (hasInvalidPrice) {
                        validationError = "يمنع حفظ المستند دون تحديد سعر أكبر من الصفر للأصناف."
                        return@Button
                    }
                    if (!isSale) { // Purchase invoice / Purchase return
                        if (selectedContact == null) {
                            validationError = if (isReturn) "تحديد حساب المورد الزامي لحفظ مرتجع المشتريات (إشعار مدين)" else "تحديد حساب المورد الزامي لحفظ فاتورة المشتريات"
                            return@Button
                        }
                        if ((paymentMethod == "نقدي" || paymentMethod == "شبكة") && selectedAccount == null) {
                            validationError = "تحديد حساب الصندوق/الخزينة الزامي لحفظ العملية النقدية"
                            return@Button
                        }
                    } else { // Sales invoice / Sales return
                        if (paymentMethod == "آجل" && selectedContact == null) {
                            validationError = if (isReturn) "يرجى تحديد العميل للمرتجع الآجل" else "يرجى تحديد العميل للفاتورة الآجلة"
                            return@Button
                        }
                        if (isCreditLimitExceeded) {
                            validationError = "⛔ عذراً: تجاوز العميل السقف المالي الائتماني المعتمد (السقف: ${selectedContact?.creditLimit} ر.ي | المديونية المتوقعة: ${projectedCredit} ر.ي). لا يمكن إتمام عملية البيع الآجل قبل تعديل السقف المالي أو سداد الديون."
                            return@Button
                        }
                        if ((paymentMethod == "نقدي" || paymentMethod == "شبكة") && selectedAccount == null) {
                            validationError = "تحديد حساب الصندوق/الخزينة الزامي لحفظ العملية النقدية"
                            return@Button
                        }

                        // Check Below Cost policy restriction across all cart items
                        if (isSale && !isReturn && !settings.allowSellBelowCost) {
                            val belowCostItem = cartItems.find { (item, invItem) ->
                                val unitCostPrice = item.purchasePrice * invItem.conversionFactor
                                invItem.unitPrice < unitCostPrice
                            }
                            if (belowCostItem != null) {
                                val (item, invItem) = belowCostItem
                                val unitCostPrice = item.purchasePrice * invItem.conversionFactor
                                validationError = "⛔ عذراً: يحتوي المستند على صنف بسعر أقل من التكلفة (${item.name}: التكلفة $unitCostPrice ر.ي | سعر البيع ${invItem.unitPrice} ر.ي)، وسياسة المنشأة تمنع ذلك."
                                return@Button
                            }
                        }

                        // Check Negative Stock policy restriction across all cart items
                        if (isSale && !isReturn && !settings.allowNegativeStock) {
                            val negativeStockItem = cartItems.find { (item, _) ->
                                val totalRequestedQtyInBaseUnits = cartItems
                                    .filter { it.first.id == item.id }
                                    .sumOf { it.second.quantity * it.second.conversionFactor }
                                totalRequestedQtyInBaseUnits > item.currentQuantity
                            }
                            if (negativeStockItem != null) {
                                val (item, _) = negativeStockItem
                                validationError = "⛔ عذراً: الصنف (${item.name}) غير متوفر منه كمية كافية بالمخزن (المتوفر حالياً: ${item.currentQuantity} ${item.unit})، وسياسة المنشأة تمنع البيع بالكميات السالبة."
                                return@Button
                            }
                        }
                    }



                    validationError = null // Clear any validation error on success
                    val prefix = when (mode) {
                        "SALE_RETURN" -> "RET-S-"
                        "PURCHASE_RETURN" -> "RET-P-"
                        "PURCHASE" -> "PUR-"
                        else -> "INV-"
                    }
                    val invoiceNum = prefix + (10000000..99999999).random()

                    val notesText = buildString {
                        if (!isSale && !isReturn && supplierRefInvoiceNum.isNotEmpty()) {
                            append("[مرجع المورد: $supplierRefInvoiceNum] ")
                        }
                        if (isReturn && originalInvoiceNum.isNotEmpty()) {
                            append("[الفاتورة الأصلية: $originalInvoiceNum] ")
                        }
                        if (isReturn) {
                            append("[سبب المرتجع: $returnReason] ")
                            if (mode == "SALE_RETURN") {
                                append("[حالة المخزون: $returnedStockCondition] ")
                            }
                        }
                        if (shippingVal > 0) {
                            append("[شامل مصاريف شحن $shippingVal ر.ي] ")
                        }
                    }

                    val invoice = Invoice(
                        invoiceNumber = invoiceNum,
                        type = when (mode) {
                            "SALE_RETURN" -> "SALE_RETURN"
                            "PURCHASE_RETURN" -> "PURCHASE_RETURN"
                            "SALE" -> if (paymentMethod == "آجل") "SALE_CREDIT" else "SALE_CASH"
                            else -> if (paymentMethod == "آجل") "PURCHASE_CREDIT" else "PURCHASE_CASH"
                        },
                        contactId = selectedContact?.id,
                        subTotal = subTotal,
                        discount = discountVal,
                        tax = taxValue,
                        total = invoiceTotal,
                        paidAmount = if (paymentMethod == "آجل") 0.0 else invoiceTotal,
                        remainingAmount = if (paymentMethod == "آجل") invoiceTotal else 0.0,
                        paymentMethod = paymentMethod,
                        notes = notesText,
                        userId = 1,
                        currencyCode = selectedCurrencyCode,
                        exchangeRate = exchangeRateInput.toDoubleOrNull() ?: 1.0
                    )
                    if (editingInvoice != null) {
                        val updatedInvoiceObj = invoice.copy(
                            id = editingInvoice!!.id,
                            invoiceNumber = editingInvoice!!.invoiceNumber
                        )
                        viewModel.updateInvoice(editingInvoice!!, updatedInvoiceObj, cartItems.map { it.second }, selectedAccount?.id)
                        savedInvoiceNumber = editingInvoice!!.invoiceNumber
                    } else {
                        viewModel.createInvoice(invoice, cartItems.map { it.second }, selectedAccount?.id)
                        savedInvoiceNumber = invoiceNum
                    }

                    // Automatically update item cost in catalog if option is checked for purchases
                    if (!isSale && !isReturn && updateItemCostOnPurchase) {
                        cartItems.forEach { (item, invItem) ->
                            val pieceCost = if (invItem.conversionFactor > 0) invItem.unitPrice / invItem.conversionFactor else invItem.unitPrice
                            if (pieceCost > 0 && pieceCost != item.purchasePrice) {
                                viewModel.updateItem(item.copy(purchasePrice = pieceCost))
                            }
                        }
                    }

                    savedInvoiceTotal = invoiceTotal
                    showSuccessDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                val buttonText = when {
                    editingInvoice != null -> "تأكيد وتحديث الفاتورة المعدلة"
                    isReturn -> "ترحيل وتأكيد الإشعار المرتجع"
                    else -> "ترحيل وحفظ الفاتورة"
                }
                Text(buttonText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Post-save actions modal popup
        if (showSuccessDialog) {
            val context = LocalContext.current
            val dateString = remember(savedInvoiceNumber) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            }
            val invoiceShareText = remember(savedInvoiceNumber, savedInvoiceTotal) {
                val actionType = when (mode) {
                    "SALE_RETURN" -> "مرتجع مبيعات"
                    "PURCHASE_RETURN" -> "إشعار مدين / مرتجع مشتريات للمورد"
                    "SALE" -> "فاتورة مبيعات"
                    else -> "فاتورة مشتريات وتوريد"
                }
                """
                === ${settings.name} ===
                $actionType - رقم: $savedInvoiceNumber
                الرقم الضريبي: ${settings.taxId}
                طريقة التسوية: $paymentMethod
                العميل/المورد: ${selectedContact?.name ?: "نقدي"}
                ${if (originalInvoiceNum.isNotEmpty()) "الفاتورة الأصلية: $originalInvoiceNum" else ""}
                ${if (isReturn) "سبب الإرجاع: $returnReason" else ""}
                المجموع الفرعي: $subTotal ر.ي
                ${if (shippingVal > 0) "مصاريف النقل/الشحن: $shippingVal ر.ي" else ""}
                الخصم: $discountVal ر.ي
                الضريبة (15%): $taxValue ر.ي
                إجمالي المبلغ الكلي: $savedInvoiceTotal ر.ي
                تاريخ العملية: $dateString
                شكراً لتعاملكم معنا!
                """.trimIndent()
            }

            val invoiceHtml = remember(savedInvoiceNumber, savedInvoiceTotal) {
                val itemsRows = cartItems.joinToString("") { (item, invItem) ->
                    """
                    <tr>
                        <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right;">${item.name}</td>
                        <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: center;">${invItem.quantity} ${invItem.unitName}</td>
                        <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: left;">${invItem.unitPrice} ر.ي</td>
                        <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: left;">${invItem.total} ر.ي</td>
                    </tr>
                    """
                }
                val pageTitle = when (mode) {
                    "SALE_RETURN" -> "إشعار مرتجع مبيعات ضريبي (دائن)"
                    "PURCHASE_RETURN" -> "إشعار مدين / مرتجع مشتريات للمورد (Debit Note)"
                    "SALE" -> "فاتورة مبيعات ضريبية مبسطة"
                    else -> "فاتورة مشتريات وتوريد بضائع"
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
                        .info-table { width: 100%; margin-bottom: 20px; border-collapse: collapse; }
                        .info-table td { padding: 4px; font-size: 13px; }
                        .items-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                        .items-table th { background-color: #f0f4f8; padding: 8px; font-size: 13px; text-align: right; border-bottom: 2px solid #ccc; }
                        .totals-table { width: 45%; float: left; margin-bottom: 20px; border-collapse: collapse; }
                        .totals-table td { padding: 6px; font-size: 13px; }
                        .footer { text-align: center; margin-top: 30px; font-size: 12px; color: #777; border-top: 1px solid #eee; padding-top: 10px; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <div class="company-name">${settings.name}</div>
                        <div>${settings.activity} | ${settings.address}</div>
                        <div>الرقم الضريبي: <strong>${settings.taxId}</strong> | السجل التجاري: ${settings.crId}</div>
                        <div class="title" style="margin-top: 10px;">$pageTitle</div>
                    </div>
                    <table class="info-table">
                        <tr>
                            <td><strong>رقم المستند:</strong> $savedInvoiceNumber</td>
                            <td style="text-align: left;"><strong>التاريخ:</strong> $dateString</td>
                        </tr>
                        <tr>
                            <td><strong>المورد / العميل:</strong> ${selectedContact?.name ?: "نقدي"}</td>
                            <td style="text-align: left;"><strong>طريقة التسوية:</strong> $paymentMethod</td>
                        </tr>
                        ${if (originalInvoiceNum.isNotEmpty()) "<tr><td colspan='2'><strong>مرجع الفاتورة الأصلية:</strong> $originalInvoiceNum</td></tr>" else ""}
                        ${if (isReturn) "<tr><td colspan='2'><strong>سبب الإرجاع للمورد:</strong> $returnReason</td></tr>" else ""}
                    </table>
                    <table class="items-table">
                        <thead>
                            <tr>
                                <th style="text-align: right;">الصنف المرتجع للمورد</th>
                                <th style="text-align: center;">الكمية والوحدة</th>
                                <th style="text-align: left;">سعر الوحدة</th>
                                <th style="text-align: left;">الإجمالي</th>
                            </tr>
                        </thead>
                        <tbody>
                            $itemsRows
                        </tbody>
                    </table>
                    <table class="totals-table" style="float: left;">
                        <tr>
                            <td><strong>المجموع الفرعي:</strong></td>
                            <td style="text-align: left;">$subTotal ر.ي</td>
                        </tr>
                        ${if (shippingVal > 0) "<tr><td><strong>مصاريف الشحن والنقل:</strong></td><td style='text-align: left;'>$shippingVal ر.ي</td></tr>" else ""}
                        <tr>
                            <td><strong>إجمالي الخصم:</strong></td>
                            <td style="text-align: left;">$discountVal ر.ي</td>
                        </tr>
                        <tr>
                            <td><strong>ضريبة القيمة المضافة (15%):</strong></td>
                            <td style="text-align: left;">$taxValue ر.ي</td>
                        </tr>
                        <tr style="border-top: 2px solid #0061A4; font-size: 15px; font-weight: bold;">
                            <td><strong>المجموع الكلي النهائي:</strong></td>
                            <td style="text-align: left; color: #2E7D32;">$savedInvoiceTotal ر.ي</td>
                        </tr>
                    </table>
                    <div style="clear: both;"></div>
                    <div class="footer">
                        <p>${settings.invoiceFooter}</p>
                        <p>تاريخ وتطبيق الطباعة: $dateString</p>
                    </div>
                </body>
                </html>
                """.trimIndent()
            }

            AlertDialog(
                onDismissRequest = {
                    showSuccessDialog = false
                    cartItems.clear()
                    onBack()
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "نجاح", tint = Color(0xFF2E7D32), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✅ تم رحيل وتأكيد المستند بنجاح", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("رقم المستند المرتجع: $savedInvoiceNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("إجمالي المبلغ الكلي: $savedInvoiceTotal ر.ي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("خيارات المشاركة والطباعة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SuccessActionItem("إنشاء PDF", Icons.Default.PictureAsPdf, Color.Red) {
                                printInvoice(context, invoiceHtml, savedInvoiceNumber)
                            }
                            SuccessActionItem("طباعة", Icons.Default.Print, Color.Blue) {
                                printInvoice(context, invoiceHtml, savedInvoiceNumber)
                            }
                            SuccessActionItem("مشاركة", Icons.Default.Share, Color.DarkGray) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "مستند $savedInvoiceNumber")
                                    putExtra(Intent.EXTRA_TEXT, invoiceShareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة المستند عبر"))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SuccessActionItem("واتساب", Icons.Default.Chat, Color(0xFF25D366)) {
                                try {
                                    val formattedPhone = PhoneUtils.formatForWhatsApp(selectedContact?.phone)
                                    val phoneParam = if (formattedPhone.isNotEmpty()) "phone=${formattedPhone}&" else ""
                                    val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?${phoneParam}text=${Uri.encode(invoiceShareText)}")
                                    }
                                    context.startActivity(whatsappIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تطبيق واتساب غير مثبت أو فشل فتح الرابط", Toast.LENGTH_SHORT).show()
                                }
                            }
                            SuccessActionItem("رسالة SMS", Icons.Default.Sms, Color(0xFF00ACC1)) {
                                try {
                                    val formattedSms = PhoneUtils.formatForSms(selectedContact?.phone)
                                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("smsto:${formattedSms}")
                                        putExtra("sms_body", invoiceShareText)
                                    }
                                    context.startActivity(smsIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "فشل فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show()
                                }
                            }
                            SuccessActionItem("نسخ", Icons.Default.ContentCopy, Color.Black) {
                                try {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Invoice", invoiceShareText)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "تم نسخ تفاصيل المستند إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "فشل نسخ النص: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            cartItems.clear()
                            onBack()
                        }
                    ) {
                        Text("إغلاق والعودة", color = Color.White)
                    }
                }
            )
        }
        // Invoices List Dialog for Editing
        if (showInvoicesListDialog) {
            val allInvoices by viewModel.invoices.collectAsState(initial = emptyList())
            val relevantInvoices = allInvoices.filter { it.type == mode }.sortedByDescending { it.timestamp }
            var searchInvoiceText by remember { mutableStateOf("") }
            
            AlertDialog(
                onDismissRequest = { showInvoicesListDialog = false },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = { Text("استعراض وتعديل الفواتير السابقة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchInvoiceText,
                            onValueChange = { searchInvoiceText = it },
                            placeholder = { Text("ابحث برقم الفاتورة أو العميل...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        val filteredList = relevantInvoices.filter { 
                            it.invoiceNumber.contains(searchInvoiceText, ignoreCase = true) ||
                            (contacts.find { c -> c.id == it.contactId }?.name?.contains(searchInvoiceText, ignoreCase = true) == true)
                        }
                        
                        if (filteredList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد فواتير مطابقة", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredList) { inv ->
                                    val contact = contacts.find { it.id == inv.contactId }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("رقم الفاتورة: ${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
                                            Text("العميل: ${contact?.name ?: "نقدي"}", fontSize = 12.sp, color = Color.Gray)
                                            Text("الإجمالي: ${inv.total} ر.ي", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                editingInvoice = inv
                                                showInvoicesListDialog = false
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل الفاتورة", tint = Color.Blue)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInvoicesListDialog = false }) {
                        Text("إغلاق", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun SuccessActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

fun printInvoice(context: Context, htmlContent: String, invoiceNumber: String) {
    try {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = view.createPrintDocumentAdapter("Invoice_$invoiceNumber")
                    printManager.print("Invoice_$invoiceNumber", printAdapter, PrintAttributes.Builder().build())
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

