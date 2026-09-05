package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashTransaction
import com.example.data.model.Invoice
import com.example.data.model.JournalEntry
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

sealed class GenericOperationItem {
    abstract val timestamp: Long
    abstract val displayTitle: String
    abstract val displaySubtitle: String
    abstract val amount: Double
    abstract val tagLabel: String
    abstract val tagColor: Color

    data class InvoiceOp(val invoice: Invoice, val contactName: String) : GenericOperationItem() {
        override val timestamp: Long = invoice.timestamp
        override val displayTitle: String = "فاتورة رقم ${invoice.invoiceNumber}"
        override val displaySubtitle: String = "الجهة: $contactName | طريقة التسوية: ${invoice.paymentMethod}"
        override val amount: Double = invoice.total
        override val tagLabel: String = when (invoice.type) {
            "SALE_CASH", "SALE_CREDIT" -> "مبيعات"
            "PURCHASE_CASH", "PURCHASE_CREDIT" -> "مشتريات"
            "SALE_RETURN" -> "مرتجع مبيعات"
            "PURCHASE_RETURN" -> "مرتجع مشتريات"
            else -> "فاتورة"
        }
        override val tagColor: Color = when (invoice.type) {
            "SALE_CASH", "SALE_CREDIT" -> Color(0xFF0061A4)
            "PURCHASE_CASH", "PURCHASE_CREDIT" -> Color(0xFFC62828)
            "SALE_RETURN" -> Color(0xFFF57C00)
            "PURCHASE_RETURN" -> Color(0xFFE53935)
            else -> Color.Gray
        }
    }

    data class JournalOp(val entry: JournalEntry) : GenericOperationItem() {
        override val timestamp: Long = entry.timestamp
        override val displayTitle: String = "قيد محاسبي رقم ${entry.entryNumber}"
        override val displaySubtitle: String = entry.description.ifEmpty { "قيد محاسبي دفتري" }
        override val amount: Double = 0.0
        override val tagLabel: String = "قيد دفتري"
        override val tagColor: Color = Color(0xFF7B1FA2)
    }

    data class CashOp(val tx: CashTransaction) : GenericOperationItem() {
        override val timestamp: Long = tx.timestamp
        override val displayTitle: String = "${if (tx.type == "RECEIPT") "سند قبض مالي 🟢" else "سند صرف مالي 🔴"} (#${tx.id})"
        override val displaySubtitle: String = tx.notes.ifEmpty { "سند خزانة/بنك" }
        override val amount: Double = tx.amount
        override val tagLabel: String = if (tx.type == "RECEIPT") "سند قبض" else "سند صرف"
        override val tagColor: Color = if (tx.type == "RECEIPT") Color(0xFF2E7D32) else Color(0xFFC62828)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    viewModel: AppViewModel,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val invoices by viewModel.invoices.collectAsState(initial = emptyList())
    val journalEntries by viewModel.journalEntries.collectAsState(initial = emptyList())
    val cashTransactions by viewModel.cashTransactions.collectAsState(initial = emptyList())
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())

    var searchText by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    var selectedOpForView by remember { mutableStateOf<GenericOperationItem?>(null) }
    var selectedOpForDelete by remember { mutableStateOf<GenericOperationItem?>(null) }

    val categories = listOf("الكل 🌐", "فواتير المبيعات 🛒", "فواتير المشتريات 📦", "المرتجعات 🔄", "القيود المحاسبية 📖", "السندات المالية 💵")

    val allOps = remember(invoices, journalEntries, cashTransactions, contacts) {
        val list = mutableListOf<GenericOperationItem>()
        invoices.forEach { inv ->
            val cName = contacts.find { it.id == inv.contactId }?.name ?: "نقدي"
            list.add(GenericOperationItem.InvoiceOp(inv, cName))
        }
        journalEntries.forEach { j ->
            list.add(GenericOperationItem.JournalOp(j))
        }
        cashTransactions.forEach { c ->
            list.add(GenericOperationItem.CashOp(c))
        }
        list.sortByDescending { it.timestamp }
        list
    }

    val filteredOps = remember(allOps, searchText, selectedCategoryIndex) {
        allOps.filter { op ->
            val matchesCategory = when (selectedCategoryIndex) {
                1 -> op is GenericOperationItem.InvoiceOp && (op.invoice.type == "SALE_CASH" || op.invoice.type == "SALE_CREDIT")
                2 -> op is GenericOperationItem.InvoiceOp && (op.invoice.type == "PURCHASE_CASH" || op.invoice.type == "PURCHASE_CREDIT")
                3 -> op is GenericOperationItem.InvoiceOp && (op.invoice.type == "SALE_RETURN" || op.invoice.type == "PURCHASE_RETURN")
                4 -> op is GenericOperationItem.JournalOp
                5 -> op is GenericOperationItem.CashOp
                else -> true
            }

            val query = searchText.trim()
            val matchesSearch = query.isEmpty() ||
                    op.displayTitle.contains(query, ignoreCase = true) ||
                    op.displaySubtitle.contains(query, ignoreCase = true) ||
                    op.tagLabel.contains(query, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل وإدارة العمليات والبحث الشامل", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("ابحث برقم المستند، العميل/المورد، أو البيان...") },
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
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Scrollable Tab Categories
            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = { Text(cat, fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("إجمالي العمليات المطابقة: ${filteredOps.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)

            if (filteredOps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد عمليات مسجلة مطابقة للبحث", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOps) { op ->
                        val dateStr = remember(op.timestamp) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(op.timestamp))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = op.tagColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = op.tagLabel,
                                            color = op.tagColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(dateStr, fontSize = 11.sp, color = Color.Gray)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(op.displayTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(op.displaySubtitle, fontSize = 12.sp, color = Color.Gray)

                                if (op.amount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("المبلغ: ${String.format("%.2f", op.amount)} ر.ي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                // Operations Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // View Action (Read Only)
                                    OutlinedButton(
                                        onClick = { selectedOpForView = op },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = "عرض", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("عرض 👁️", fontSize = 11.sp)
                                    }

                                    // Edit Action
                                    Button(
                                        onClick = {
                                            when (op) {
                                                is GenericOperationItem.InvoiceOp -> viewModel.startEditingInvoice(op.invoice, onNavigate)
                                                is GenericOperationItem.JournalOp -> viewModel.startEditingJournalEntry(op.entry, onNavigate)
                                                is GenericOperationItem.CashOp -> viewModel.startEditingCashTx(op.tx, onNavigate)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("تعديل ✏️", fontSize = 11.sp, color = Color.White)
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Delete / Reverse Action
                                    IconButton(
                                        onClick = { selectedOpForDelete = op }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف/عكس", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (selectedOpForDelete != null) {
            val op = selectedOpForDelete!!
            AlertDialog(
                onDismissRequest = { selectedOpForDelete = null },
                title = { Text("⚠️ تأكيد عكس/حذف العملية", fontWeight = FontWeight.Bold, color = Color.Red) },
                text = {
                    Text("هل أنت تأكد من رغبتك في عكس وحذف [${op.displayTitle}]؟ ستقوم هذه العملية بإلغاء كافة التأثيرات المحاسبية والمخزنية المتولدة عنها بأمان.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (op) {
                                is GenericOperationItem.InvoiceOp -> viewModel.deleteInvoice(op.invoice)
                                is GenericOperationItem.JournalOp -> viewModel.deleteJournalEntry(op.entry)
                                is GenericOperationItem.CashOp -> viewModel.deleteCashTransaction(op.tx)
                            }
                            selectedOpForDelete = null
                            Toast.makeText(context, "تمت عملية العكس والحذف بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("نعم، احذف وعكس العملية", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedOpForDelete = null }) { Text("إلغاء") }
                }
            )
        }

        // Read-Only View Dialog
        if (selectedOpForView != null) {
            val op = selectedOpForView!!
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(op.timestamp))
            AlertDialog(
                onDismissRequest = { selectedOpForView = null },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(op.displayTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Surface(shape = RoundedCornerShape(6.dp), color = op.tagColor.copy(alpha = 0.12f)) {
                            Text(op.tagLabel, color = op.tagColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔒 حالة البيانات: معروضة للقراءة فقط (غير قابلة للتعديل بصفحة العرض)", fontSize = 11.sp, color = Color.Gray)
                        Divider()
                        Text("التاريخ والوقت: $dateStr", fontSize = 12.sp)
                        Text(op.displaySubtitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        if (op.amount > 0) {
                            Text("المبلغ الإجمالي: ${String.format("%.2f", op.amount)} ر.ي", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        when (op) {
                            is GenericOperationItem.InvoiceOp -> {
                                Text("نوع الفاتورة: ${op.invoice.type}", fontSize = 12.sp)
                                Text("طريقة التسوية: ${op.invoice.paymentMethod}", fontSize = 12.sp)
                                Text("مبلغ الضريبة: ${op.invoice.tax} ر.ي", fontSize = 12.sp)
                                Text("العملة: ${op.invoice.currencyCode} (سعر الصرف: ${op.invoice.exchangeRate})", fontSize = 12.sp)
                            }
                            is GenericOperationItem.CashOp -> {
                                Text("العملة: ${op.tx.currencyCode} (سعر الصرف: ${op.tx.exchangeRate})", fontSize = 12.sp)
                            }
                            is GenericOperationItem.JournalOp -> {
                                Text("العملة: ${op.entry.currencyCode} (سعر الصرف: ${op.entry.exchangeRate})", fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetOp = op
                            selectedOpForView = null
                            when (targetOp) {
                                is GenericOperationItem.InvoiceOp -> viewModel.startEditingInvoice(targetOp.invoice, onNavigate)
                                is GenericOperationItem.JournalOp -> viewModel.startEditingJournalEntry(targetOp.entry, onNavigate)
                                is GenericOperationItem.CashOp -> viewModel.startEditingCashTx(targetOp.tx, onNavigate)
                            }
                        }
                    ) {
                        Text("الانتقال لتعديل العملية ✏️", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedOpForView = null }) { Text("إلغاء العرض") }
                }
            )
        }
    }
}
