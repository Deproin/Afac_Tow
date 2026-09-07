package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashTransaction
import com.example.data.model.Invoice
import com.example.data.model.JournalEntry
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("الفواتير", "السندات", "القيود اليومية")

    val deletedInvoices by viewModel.deletedInvoices.collectAsState()
    val deletedCashTransactions by viewModel.deletedCashTransactions.collectAsState()
    val deletedJournalEntries by viewModel.deletedJournalEntries.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete: Any? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سلة المحذوفات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "عودة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF44336), // Red color to indicate danger/trash
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color(0xFFF44336),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTabIndex) {
                    0 -> {
                        if (deletedInvoices.isEmpty()) {
                            EmptyStateMessage("لا توجد فواتير محذوفة")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(deletedInvoices) { invoice ->
                                    RecycleBinItemCard(
                                        title = "فاتورة رقم ${invoice.invoiceNumber}",
                                        subtitle = "النوع: ${getInvoiceTypeName(invoice.type)} - الإجمالي: ${invoice.total} ${invoice.currencyCode}",
                                        date = invoice.timestamp,
                                        onRestore = { viewModel.restoreInvoice(invoice) },
                                        onPermanentDelete = {
                                            itemToDelete = invoice
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        if (deletedCashTransactions.isEmpty()) {
                            EmptyStateMessage("لا توجد سندات محذوفة")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(deletedCashTransactions) { tx ->
                                    RecycleBinItemCard(
                                        title = "سند رقم ${tx.id}",
                                        subtitle = "النوع: ${if (tx.type == "RECEIPT") "قبض" else "صرف"} - المبلغ: ${tx.amount} ${tx.currencyCode}",
                                        date = tx.timestamp,
                                        onRestore = { viewModel.restoreCashTransaction(tx) },
                                        onPermanentDelete = {
                                            itemToDelete = tx
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        if (deletedJournalEntries.isEmpty()) {
                            EmptyStateMessage("لا توجد قيود يومية محذوفة")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(deletedJournalEntries) { entry ->
                                    RecycleBinItemCard(
                                        title = "قيد رقم ${entry.entryNumber}",
                                        subtitle = "البيان: ${entry.description}",
                                        date = entry.timestamp,
                                        onRestore = { viewModel.restoreJournalEntry(entry) },
                                        onPermanentDelete = {
                                            itemToDelete = entry
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف نهائي", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("هل أنت متأكد من الحذف النهائي؟ لا يمكن التراجع عن هذا الإجراء ولن يعود للسجل أي أثر مالي.") },
            confirmButton = {
                Button(
                    onClick = {
                        when (val item = itemToDelete) {
                            is Invoice -> viewModel.permanentDeleteInvoice(item)
                            is CashTransaction -> viewModel.permanentDeleteCashTransaction(item)
                            is JournalEntry -> viewModel.permanentDeleteJournalEntry(item)
                        }
                        showDeleteDialog = false
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("نعم، حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RecycleBinItemCard(
    title: String,
    subtitle: String,
    date: Long,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(date))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = dateStr, fontSize = 12.sp, color = Color.LightGray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.background(Color(0xFFE8F5E9), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = "استعادة", tint = Color(0xFF4CAF50))
                }
                IconButton(
                    onClick = onPermanentDelete,
                    modifier = Modifier.background(Color(0xFFFFEBEE), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "حذف نهائي", tint = Color(0xFFF44336))
                }
            }
        }
    }
}

private fun getInvoiceTypeName(type: String): String {
    return when (type) {
        "SALE_CASH" -> "مبيعات نقدية"
        "SALE_CREDIT" -> "مبيعات آجلة"
        "PURCHASE_CASH" -> "مشتريات نقدية"
        "PURCHASE_CREDIT" -> "مشتريات آجلة"
        "SALE_RETURN" -> "مرتجع مبيعات"
        "PURCHASE_RETURN" -> "مرتجع مشتريات"
        else -> type
    }
}
