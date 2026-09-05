package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.Item
import com.example.data.model.ItemUnit
import com.example.data.model.Warehouse
import com.example.ui.viewmodel.AppViewModel

val PRESET_UNITS = listOf("حبة", "قطعة", "علبة", "كرتون", "صندوق", "باكيت", "درزن", "كيلو", "جرام", "متر", "لتر", "طقم", "حزمة", "جالون", "شدّة")
val PRESET_CATEGORIES = listOf("عام", "مواد غذائية", "إلكترونيات", "أدوات منزلية", "قطع غيار", "ملابس", "مستحضرات تجميل", "مستلزمات مكتبية", "أخرى")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val itemStocks by viewModel.itemStocks.collectAsState()
    val itemUnits by viewModel.itemUnits.collectAsState()

    val tempUnits = remember { mutableStateListOf<ItemUnit>() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الأصناف والمخزون", "المستودعات والمخازن", "الجرد والتسويات")

    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedItemForEdit by remember { mutableStateOf<Item?>(null) }

    // Search & Filter States
    var searchText by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }

    // Warehouse Dialog States
    var showAddWarehouseDialog by remember { mutableStateOf(false) }
    var showStockTransferDialog by remember { mutableStateOf(false) }
    var wName by remember { mutableStateOf("") }
    var wLoc by remember { mutableStateOf("") }

    // Handle pendingDialogToOpen from Dashboard Quick Operations
    LaunchedEffect(viewModel.pendingDialogToOpen) {
        if (viewModel.pendingDialogToOpen == "NEW_ITEM") {
            viewModel.pendingDialogToOpen = null
            selectedTab = 0
            tempUnits.clear()
            showAddItemDialog = true
        }
    }

    // Stock Transfer Form States
    var transferFromWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var transferToWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var transferItem by remember { mutableStateOf<Item?>(null) }
    var transferQtyInput by remember { mutableStateOf("1.0") }
    var transferNotesInput by remember { mutableStateOf("") }

    // Stock Settlement States
    var selectedItemForSettlement by remember { mutableStateOf<Item?>(null) }
    var newQtySettle by remember { mutableStateOf("") }

    // Stock Supply Form States
    var showStockSupplyDialog by remember { mutableStateOf(false) }
    var showStockIssueDialog by remember { mutableStateOf(false) }
    var supplyTargetItem by remember { mutableStateOf<Item?>(null) }
    var supplyWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var supplyQtyInput by remember { mutableStateOf("10.0") }
    var supplyCostInput by remember { mutableStateOf("") }
    var supplyNotesInput by remember { mutableStateOf("") }

    // Financial Inventory Metrics
    val totalStockValuation = remember(items) {
        items.sumOf { it.currentQuantity * it.purchasePrice }
    }
    val lowStockCount = remember(items) {
        items.count { it.currentQuantity <= it.minLimit }
    }

    val context = LocalContext.current
    var showExcelMenu by remember { mutableStateOf(false) }

    val dynamicUnits = remember(items, itemUnits) {
        val baseUnits = PRESET_UNITS
        val usedItemUnits = items.map { it.unit }.filter { it.isNotBlank() }
        val usedSubUnits = itemUnits.map { it.unitName }.filter { it.isNotBlank() }
        (baseUnits + usedItemUnits + usedSubUnits).distinct()
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importItemsFromCsv(it, context) { count, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة المخازن والمخزون والسلع", fontWeight = FontWeight.Bold) },
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
                        DropdownMenuItem(
                            text = { Text("📊 تصدير الأصناف إلى Excel (CSV)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                viewModel.exportItemsToCsv(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📥 استيراد أصناف من Excel (CSV)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                csvPickerLauncher.launch("*/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📄 تحميل نموذج استيراد فارغ") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showExcelMenu = false
                                viewModel.generateItemsTemplate(context)
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        tempUnits.clear()
                        showAddItemDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "صنف جديد")
                }
            } else if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = {
                        wName = ""
                        wLoc = ""
                        showAddWarehouseDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "مستودع جديد")
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
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = Color.Gray
                    )
                }
            }


            when (selectedTab) {
                0 -> {
                    // TAB 0: ITEMS & STOCK
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stock Valuation Summary Header Card
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
                                    Text("القيمة الإجمالية للمخزون (بالتكلفة)", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    Text("${String.format("%.2f", totalStockValuation)} ر.ي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("عدد الأصناف", fontSize = 10.sp, color = Color.Gray)
                                        Text("${items.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("منخفض المخزون", fontSize = 10.sp, color = Color.Gray)
                                        Text("$lowStockCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) Color.Red else Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }

                        // Search and Filter Bar
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("ابحث عن صنف بالاسم أو الكود أو الباركود...") },
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

                        // Excel & Stock Operations Quick Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    supplyTargetItem = items.firstOrNull()
                                    supplyWarehouse = warehouses.firstOrNull()
                                    supplyQtyInput = "10.0"
                                    supplyCostInput = ""
                                    supplyNotesInput = ""
                                    showStockSupplyDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("توريد مخزني +", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { viewModel.exportItemsToCsv(context) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("تصدير", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { csvPickerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("استيراد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = { viewModel.generateItemsTemplate(context) },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("نموذج", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Category Chips Filter
                        val allCategoryFilters = listOf("الكل") + PRESET_CATEGORIES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allCategoryFilters.take(5).forEach { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat, fontSize = 10.sp) }
                                )
                            }
                        }

                        // Filtered Items List
                        val filteredItems = items.filter { item ->
                            (selectedCategoryFilter == "الكل" || item.category == selectedCategoryFilter) &&
                                    (item.name.contains(searchText, ignoreCase = true) ||
                                            item.code.contains(searchText, ignoreCase = true) ||
                                            item.barcode.contains(searchText, ignoreCase = true))
                        }

                        if (filteredItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد أصناف مطابقة للبحث", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredItems) { item ->
                                    ItemRow(
                                        item = item,
                                        itemStocks = itemStocks,
                                        warehouses = warehouses,
                                        onEdit = { selectedItemForEdit = item },
                                        onDelete = { viewModel.deleteItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: WAREHOUSES & TRANSFERS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المستودعات والمخازن المسجلة (${warehouses.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            Button(
                                onClick = {
                                    if (warehouses.size >= 2 && items.isNotEmpty()) {
                                        transferFromWarehouse = warehouses.firstOrNull()
                                        transferToWarehouse = warehouses.getOrNull(1)
                                        transferItem = items.firstOrNull()
                                        transferQtyInput = "1.0"
                                        transferNotesInput = ""
                                        showStockTransferDialog = true
                                    }
                                },
                                enabled = warehouses.size >= 2 && items.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحويل مخزني بين الفروع", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        if (warehouses.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد مستودعات مضافة بعد", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(warehouses) { w ->
                                    WarehouseRow(
                                        w = w,
                                        onDelete = { viewModel.deleteWarehouse(w) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: STOCKTAKE & SETTLEMENT VARIANCE CALCULATOR
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("الجرد الدوري وتسوية الفروقات (عجز / فائض جرد)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("ابحث عن صنف للتسوية والجرد...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        val settlementItems = items.filter {
                            it.name.contains(searchText, ignoreCase = true) || it.code.contains(searchText, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(settlementItems) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("الكود: ${item.code} | الكمية بالدفتر: ${item.currentQuantity} ${item.unit}", fontSize = 12.sp, color = Color.Gray)
                                            Text("تكلفة الحبة: ${item.purchasePrice} ر.ي", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Button(
                                            onClick = {
                                                selectedItemForSettlement = item
                                                newQtySettle = item.currentQuantity.toString()
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("جرد وتسوية", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Item Dialog
        if (showAddItemDialog) {
            ItemFormDialog(
                initialItem = null,
                itemsCount = items.size,
                tempUnits = tempUnits,
                itemUnitsList = emptyList(),
                presetUnits = dynamicUnits,
                onDismiss = { showAddItemDialog = false },
                onSave = { itemToSave, unitsToSave ->
                    viewModel.addItem(itemToSave, unitsToSave)
                    tempUnits.clear()
                    showAddItemDialog = false
                },
                onAddSubUnit = { tempUnits.add(it) },
                onDeleteSubUnit = { tempUnits.remove(it) }
            )
        }

        // Edit Item Dialog
        if (selectedItemForEdit != null) {
            val itemToEdit = selectedItemForEdit!!
            ItemFormDialog(
                initialItem = itemToEdit,
                itemsCount = items.size,
                tempUnits = remember { mutableStateListOf() },
                itemUnitsList = itemUnits,
                presetUnits = dynamicUnits,
                onDismiss = { selectedItemForEdit = null },
                onSave = { updatedItem, _ ->
                    viewModel.updateItem(updatedItem)
                    selectedItemForEdit = null
                },
                onAddSubUnit = { viewModel.addItemUnit(it) },
                onDeleteSubUnit = { viewModel.deleteItemUnit(it) }
            )
        }

        // Add Warehouse Dialog
        if (showAddWarehouseDialog) {
            AlertDialog(
                onDismissRequest = { showAddWarehouseDialog = false },
                title = { Text("إنشاء مستودع جديد", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = wName, onValueChange = { wName = it }, label = { Text("اسم المستودع") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = wLoc, onValueChange = { wLoc = it }, label = { Text("الموقع/العنوان") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (wName.isNotEmpty()) {
                                viewModel.addWarehouse(Warehouse(name = wName, location = wLoc))
                                showAddWarehouseDialog = false
                            }
                        }
                    ) { Text("حفظ المستودع", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showAddWarehouseDialog = false }) { Text("إلغاء") } }
            )
        }

        // Stock Transfer Dialog between Warehouses
        if (showStockTransferDialog) {
            AlertDialog(
                onDismissRequest = { showStockTransferDialog = false },
                title = { Text("إجراء تحويل مخزني بين المستودعات", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الصنف المراد تحويله:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        DropdownSelector(
                            options = items.map { "${it.name} (متوفر: ${it.currentQuantity} ${it.unit})" },
                            selectedOption = transferItem?.name ?: "اختر الصنف",
                            onOptionSelected = { selectedStr ->
                                transferItem = items.firstOrNull { selectedStr.startsWith(it.name) }
                            }
                        )

                        Text("من المستودع (المستند منه):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        DropdownSelector(
                            options = warehouses.map { it.name },
                            selectedOption = transferFromWarehouse?.name ?: "المستودع الرئيسي",
                            onOptionSelected = { selectedW ->
                                transferFromWarehouse = warehouses.firstOrNull { it.name == selectedW }
                            }
                        )

                        Text("إلى المستودع (المحول إليه):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        DropdownSelector(
                            options = warehouses.map { it.name },
                            selectedOption = transferToWarehouse?.name ?: "الفرع الثاني",
                            onOptionSelected = { selectedW ->
                                transferToWarehouse = warehouses.firstOrNull { it.name == selectedW }
                            }
                        )

                        OutlinedTextField(
                            value = transferQtyInput,
                            onValueChange = { transferQtyInput = it },
                            label = { Text("الكمية المحولة") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        OutlinedTextField(
                            value = transferNotesInput,
                            onValueChange = { transferNotesInput = it },
                            label = { Text("بيان التحويل والملاحظات") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qty = transferQtyInput.toDoubleOrNull() ?: 0.0
                            if (transferItem != null && transferFromWarehouse != null && transferToWarehouse != null && qty > 0) {
                                viewModel.transferStock(
                                    from = transferFromWarehouse!!.id,
                                    to = transferToWarehouse!!.id,
                                    itemId = transferItem!!.id,
                                    qty = qty,
                                    notes = transferNotesInput
                                )
                                showStockTransferDialog = false
                            }
                        }
                    ) { Text("تأكيد وترحيل التحويل", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showStockTransferDialog = false }) { Text("إلغاء") } }
            )
        }

        // Inventory Settlement & Variance Calculator Dialog
        if (selectedItemForSettlement != null) {
            val item = selectedItemForSettlement!!
            val bookQty = item.currentQuantity
            val actualQty = newQtySettle.toDoubleOrNull() ?: bookQty
            val diffQty = actualQty - bookQty
            val diffValue = Math.abs(diffQty) * item.purchasePrice

            AlertDialog(
                onDismissRequest = { selectedItemForSettlement = null },
                title = { Text("جرد وتسوية مخزون: ${item.name}", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("الكمية الدفترية الحالية: $bookQty ${item.unit}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("سعر التكلفة للحبة: ${item.purchasePrice} ر.ي", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        OutlinedTextField(
                            value = newQtySettle,
                            onValueChange = { newQtySettle = it },
                            label = { Text("الكمية الفعلية الناتجة بعد الجرد اليدوي") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        // Variance Calculator Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    diffQty > 0 -> Color(0xFFE8F5E9)
                                    diffQty < 0 -> Color(0xFFFFEBEE)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = when {
                                        diffQty > 0 -> "🟢 فائض جرد: +${String.format("%.2f", diffQty)} ${item.unit}"
                                        diffQty < 0 -> "🔴 عجز جرد: ${String.format("%.2f", diffQty)} ${item.unit}"
                                        else -> "🟢 الجرد الفعلي متطابق تماماً مع الدفتر"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = when {
                                        diffQty > 0 -> Color(0xFF2E7D32)
                                        diffQty < 0 -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (diffQty != 0.0) {
                                    Text("الأثر والتكلفة المالية للجرد: ${String.format("%.2f", diffValue)} ر.ي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newQty = newQtySettle.toDoubleOrNull()
                            if (newQty != null) {
                                viewModel.updateItem(item.copy(currentQuantity = newQty))
                                selectedItemForSettlement = null
                            }
                        }
                    ) { Text("تحديث وتطابق الجرد بالدفاتر", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { selectedItemForSettlement = null }) { Text("إلغاء") } }
            )
        }

        // Stock Supply Dialog (Multi-Item)
        if (showStockSupplyDialog) {
            MultiItemStockSupplyDialog(
                viewModel = viewModel,
                items = items,
                warehouses = warehouses,
                onDismiss = { showStockSupplyDialog = false }
            )
        }

        if (showStockIssueDialog) {
            MultiItemStockIssueDialog(
                viewModel = viewModel,
                items = items,
                warehouses = warehouses,
                onDismiss = { showStockIssueDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiItemStockSupplyDialog(
    viewModel: AppViewModel,
    items: List<Item>,
    warehouses: List<Warehouse>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currencies by viewModel.currencies.collectAsState()

    var selectedWarehouse by remember { mutableStateOf(warehouses.firstOrNull()) }
    var selectedCurrencyCode by remember { mutableStateOf("YER") }
    var exchangeRateInput by remember { mutableStateOf("1.0") }
    var generalNotes by remember { mutableStateOf("") }

    // Item selection form state
    var selectedItemToAdd by remember { mutableStateOf<Item?>(null) }
    var qtyInput by remember { mutableStateOf("1") }
    var costInput by remember { mutableStateOf("0.0") }
    var expiryInput by remember { mutableStateOf("") } // YYYY-MM-DD

    val supplyBasket = remember { mutableStateListOf<Triple<Item, com.example.ui.viewmodel.StockSupplyEntry, Double>>() }

    val exchangeRate = exchangeRateInput.toDoubleOrNull() ?: 1.0
    val totalAmountInCurrency = supplyBasket.sumOf { it.third }
    val totalAmountLocal = totalAmountInCurrency * exchangeRate

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = "توريد مخزني",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "📦 سند توريد مخزني (متعدد الأصناف)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warehouse & Currency Row
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("بيانات المستودع والعملة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (warehouses.isNotEmpty()) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text("المستودع المستهدف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        DropdownSelector(
                                            options = warehouses.map { it.name },
                                            selectedOption = selectedWarehouse?.name ?: warehouses.first().name,
                                            onOptionSelected = { selected ->
                                                selectedWarehouse = warehouses.find { it.name == selected }
                                            }
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("عملة السند:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val currencyOptions = if (currencies.isEmpty()) listOf("YER", "SAR", "USD") else currencies.map { it.code }
                                    DropdownSelector(
                                        options = currencyOptions,
                                        selectedOption = selectedCurrencyCode,
                                        onOptionSelected = { selected ->
                                            selectedCurrencyCode = selected
                                            val currObj = currencies.find { it.code == selected }
                                            if (currObj != null && currObj.exchangeRate > 0) {
                                                exchangeRateInput = currObj.exchangeRate.toString()
                                            } else if (selected == "YER") {
                                                exchangeRateInput = "1.0"
                                            }
                                        }
                                    )
                                }

                                if (selectedCurrencyCode != "YER") {
                                    Column(modifier = Modifier.weight(0.9f)) {
                                        Text("سعر الصرف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = exchangeRateInput,
                                            onValueChange = { exchangeRateInput = it },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Item Configurator Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕ إضافة صنف للسند", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)

                            Text("اختر الصنف المراد توريده:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            DropdownSelector(
                                options = items.map { "${it.name} (${it.code})" },
                                selectedOption = selectedItemToAdd?.let { "${it.name} (${it.code})" } ?: "اختر الصنف من الكتلوج",
                                onOptionSelected = { selected ->
                                    val matched = items.find { "${it.name} (${it.code})" == selected }
                                    selectedItemToAdd = matched
                                    if (matched != null && matched.purchasePrice > 0) {
                                        costInput = matched.purchasePrice.toString()
                                    }
                                    if (matched != null && matched.expiryDate.isNotBlank()) {
                                        expiryInput = matched.expiryDate
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = qtyInput,
                                    onValueChange = { qtyInput = it },
                                    label = { Text("الكمية *") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = costInput,
                                    onValueChange = { costInput = it },
                                    label = { Text("تكلفة الوحدة ($selectedCurrencyCode)") },
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = expiryInput,
                                    onValueChange = { expiryInput = it },
                                    label = { Text("الانتهاء (YYYY-MM-DD)") },
                                    placeholder = { Text("2026-12-31") },
                                    modifier = Modifier.weight(1.3f),
                                    singleLine = true
                                )
                            }

                            Button(
                                onClick = {
                                    val item = selectedItemToAdd
                                    val qty = qtyInput.toDoubleOrNull() ?: 0.0
                                    val cost = costInput.toDoubleOrNull() ?: 0.0
                                    if (item != null && qty > 0) {
                                        val entry = com.example.ui.viewmodel.StockSupplyEntry(
                                            itemId = item.id,
                                            quantity = qty,
                                            unitCostInCurrency = cost,
                                            expiryDate = expiryInput.trim()
                                        )
                                        supplyBasket.add(Triple(item, entry, qty * cost))
                                        qtyInput = "1"
                                        Toast.makeText(context, "تمت إضافة ${item.name} إلى السند", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "يرجى اختيار الصنف وتحديد كمية صحيحة", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إضافة الصنف إلى قائمة التوريد", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Basket Table
                    if (supplyBasket.isNotEmpty()) {
                        Text("📋 الأصناف المضافة في السند (${supplyBasket.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                supplyBasket.forEachIndexed { idx, row ->
                                    val (item, entry, subTotal) = row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("الرمز: ${item.code}" + (if (entry.expiryDate.isNotBlank()) " | انتهاء: ${entry.expiryDate}" else ""), fontSize = 10.sp, color = Color.Gray)
                                        }

                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                            Text("الكمية: ${entry.quantity} ${item.unit}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Text("الإجمالي: $subTotal $selectedCurrencyCode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(
                                            onClick = { supplyBasket.removeAt(idx) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = generalNotes,
                        onValueChange = { generalNotes = it },
                        label = { Text("ملاحظات عامة على السند / رقم الشحنة أو المورد") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Total & Save Footer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إجمالي السند ($selectedCurrencyCode):", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "$totalAmountInCurrency $selectedCurrencyCode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (selectedCurrencyCode != "YER") {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("المقابل بالمحلي (ر.ي):", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "$totalAmountLocal ر.ي",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("إلغاء") }

                    Button(
                        onClick = {
                            if (supplyBasket.isNotEmpty()) {
                                val wId = selectedWarehouse?.id ?: 0L
                                val entriesList = supplyBasket.map { it.second }
                                viewModel.supplyStockMulti(
                                    entries = entriesList,
                                    warehouseId = wId,
                                    currencyCode = selectedCurrencyCode,
                                    exchangeRate = exchangeRate,
                                    generalNotes = generalNotes.trim()
                                )
                                onDismiss()
                                Toast.makeText(context, "✅ تم حفظ سند التوريد المخزني بنجاح (${supplyBasket.size} أصناف)", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "يرجى إضافة صنف واحد على الأقل للسند", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "حفظ")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تأكيد وحفظ سند التوريد 📦", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemFormDialog(
    initialItem: Item?,
    itemsCount: Int,
    tempUnits: MutableList<ItemUnit>,
    itemUnitsList: List<ItemUnit>,
    presetUnits: List<String>,
    onDismiss: () -> Unit,
    onSave: (Item, List<ItemUnit>) -> Unit,
    onAddSubUnit: (ItemUnit) -> Unit,
    onDeleteSubUnit: (ItemUnit) -> Unit
) {
    var dialogTab by remember { mutableIntStateOf(0) }
    val isEdit = initialItem != null

    // Form fields
    var code by remember { mutableStateOf(initialItem?.code ?: "ITEM-${itemsCount + 101}") }
    var barcode by remember { mutableStateOf(initialItem?.barcode ?: (100000000000..999999999999).random().toString()) }
    
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            barcode = result.contents
        }
    }

    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var category by remember { mutableStateOf(initialItem?.category.takeIf { !it.isNullOrEmpty() } ?: "عام") }
    var unit by remember { mutableStateOf(initialItem?.unit.takeIf { !it.isNullOrEmpty() } ?: "حبة") }
    var brand by remember { mutableStateOf(initialItem?.brand ?: "") }
    var color by remember { mutableStateOf(initialItem?.color ?: "") }
    var size by remember { mutableStateOf(initialItem?.size ?: "") }
    var location by remember { mutableStateOf(initialItem?.location ?: "") }

    var purchasePrice by remember { mutableStateOf(initialItem?.purchasePrice?.toString() ?: "0.0") }
    var salePrice by remember { mutableStateOf(initialItem?.salePrice?.toString() ?: "0.0") }
    var wholesalePrice by remember { mutableStateOf(initialItem?.wholesalePrice?.toString() ?: "0.0") }
    var specialPrice by remember { mutableStateOf(initialItem?.specialPrice?.toString() ?: "0.0") }

    var initialQty by remember { mutableStateOf(initialItem?.currentQuantity?.toString() ?: "0") }
    var minLimit by remember { mutableStateOf(initialItem?.minLimit?.toString() ?: "5") }
    var maxLimit by remember { mutableStateOf(initialItem?.maxLimit?.toString() ?: "9999") }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 12.dp),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isEdit) "تعديل بيانات الصنف: ${initialItem?.name}" else "إضافة صنف جديد للمخزون",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Form Tab Selector
                TabRow(selectedTabIndex = dialogTab) {
                    Tab(
                        selected = dialogTab == 0,
                        onClick = { dialogTab = 0 },
                        text = { Text("الرئيسية", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = dialogTab == 1,
                        onClick = { dialogTab = 1 },
                        text = { Text("الأسعار والربح", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = dialogTab == 2,
                        onClick = { dialogTab = 2 },
                        text = { Text("المخزون والوحدات", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = dialogTab == 3,
                        onClick = { dialogTab = 3 },
                        text = { Text("التفاصيل", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (dialogTab) {
                    0 -> {
                        // TAB 1: BASIC INFO
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; errorMessage = null },
                            label = { Text("اسم الصنف التجاري *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text("كود الصنف") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Column(modifier = Modifier.weight(1.3f)) {
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("الباركود") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = {
                                                val options = com.journeyapps.barcodescanner.ScanOptions()
                                                options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.ALL_CODE_TYPES)
                                                options.setPrompt("امسح الباركود للصنف (وجه الكاميرا نحو الرمز)")
                                                options.setBeepEnabled(true)
                                                options.setBarcodeImageEnabled(true)
                                                scanLauncher.launch(options)
                                            }) {
                                                Icon(Icons.Default.QrCodeScanner, contentDescription = "قراءة الباركود", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = {
                                                barcode = (100000000000..999999999999).random().toString()
                                            }) {
                                                Icon(Icons.Default.Autorenew, contentDescription = "توليد تلقائي", tint = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Category Dropdown Selection
                        Text("قسم / تصنيف الصنف:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        DropdownSelector(
                            options = PRESET_CATEGORIES,
                            selectedOption = category,
                            onOptionSelected = { category = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("الماركة / الشركة المصنعة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("مكان التخزين / الرف") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    1 -> {
                        // TAB 2: PRICING & PROFIT MARGIN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = purchasePrice,
                                onValueChange = { purchasePrice = it },
                                label = { Text("سعر الشراء / التكلفة (ر.ي)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = salePrice,
                                onValueChange = { salePrice = it },
                                label = { Text("سعر البيع التجزئة (ر.ي)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        // Dynamic Profit Margin Indicator Bar
                        val purVal = purchasePrice.toDoubleOrNull() ?: 0.0
                        val salVal = salePrice.toDoubleOrNull() ?: 0.0
                        val profitVal = salVal - purVal
                        val marginPercent = if (purVal > 0) (profitVal / purVal) * 100 else 0.0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (profitVal >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ربح القطعة:", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = "${String.format("%.2f", profitVal)} ر.ي",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profitVal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("هامش الربح (%):", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = "${String.format("%.1f", marginPercent)}%",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (marginPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = wholesalePrice,
                                onValueChange = { wholesalePrice = it },
                                label = { Text("سعر الجملة (اختياري)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = specialPrice,
                                onValueChange = { specialPrice = it },
                                label = { Text("سعر خاص (اختياري)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }

                    2 -> {
                        // TAB 3: INVENTORY & MULTI-UNITS
                        Text("الوحدة الأساسية للصنف (اختر من القائمة بدون كتابة):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        DropdownSelector(
                            options = presetUnits,
                            selectedOption = unit,
                            onOptionSelected = { unit = it }
                        )

                        if (!isEdit) {
                            OutlinedTextField(
                                value = initialQty,
                                onValueChange = { initialQty = it },
                                label = { Text("الكمية الابتدائية بالحساب الحالي") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = minLimit,
                                onValueChange = { minLimit = it },
                                label = { Text("حد أدنى للتنبيه") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = maxLimit,
                                onValueChange = { maxLimit = it },
                                label = { Text("حد أقصى للمخزون") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        // Sub-units manager
                        ItemUnitsManagerSection(
                            itemId = initialItem?.id ?: 0L,
                            tempUnits = tempUnits,
                            itemUnitsList = itemUnitsList,
                            presetUnits = presetUnits,
                            onAddUnit = onAddSubUnit,
                            onDeleteUnit = onDeleteSubUnit
                        )
                    }

                    3 -> {
                        // TAB 4: EXTRA DETAILS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = color,
                                onValueChange = { color = it },
                                label = { Text("اللون") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = size,
                                onValueChange = { size = it },
                                label = { Text("المقاس") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات إضافية عن الصنف") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                    }
                }

                errorMessage?.let { err ->
                    Text(err, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMessage = "يرجى كتابة اسم الصنف الإجباري"
                        dialogTab = 0
                        return@Button
                    }

                    val pur = purchasePrice.toDoubleOrNull() ?: 0.0
                    val sal = salePrice.toDoubleOrNull() ?: 0.0
                    val wSal = wholesalePrice.toDoubleOrNull() ?: 0.0
                    val spec = specialPrice.toDoubleOrNull() ?: 0.0
                    val qInit = initialQty.toDoubleOrNull() ?: 0.0
                    val mnLim = minLimit.toDoubleOrNull() ?: 5.0
                    val mxLim = maxLimit.toDoubleOrNull() ?: 9999.0

                    val itemResult = Item(
                        id = initialItem?.id ?: 0L,
                        code = code.ifEmpty { "ITEM-${itemsCount + 101}" },
                        barcode = barcode,
                        name = name.trim(),
                        category = category,
                        unit = unit,
                        brand = brand,
                        color = color,
                        size = size,
                        location = location,
                        minLimit = mnLim,
                        maxLimit = mxLim,
                        purchasePrice = pur,
                        salePrice = sal,
                        wholesalePrice = wSal,
                        specialPrice = spec,
                        currentQuantity = if (isEdit) initialItem.currentQuantity else qInit,
                        notes = notes
                    )

                    onSave(itemResult, tempUnits.toList())
                }
            ) {
                Text(if (isEdit) "حفظ التعديلات" else "إضافة الصنف", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun DropdownSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(selectedOption !in options && selectedOption.isNotEmpty()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCustom) "تخصيص: $selectedOption" else selectedOption,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "سهم الاختيار")
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item, fontSize = 13.sp, fontWeight = if (item == selectedOption) FontWeight.Bold else FontWeight.Normal)
                                if (item == selectedOption) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            isCustom = false
                            onOptionSelected(item)
                            expanded = false
                        }
                    )
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("✏️ إدخال قيمة مخصصة أخرى...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                    onClick = {
                        isCustom = true
                        expanded = false
                    }
                )
            }
        }

        if (isCustom) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = customInput,
                onValueChange = {
                    customInput = it
                    onOptionSelected(it)
                },
                label = { Text("اكتب القيمة المخصصة يدوياً") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun ItemRow(
    item: Item,
    itemStocks: List<ItemStock> = emptyList(),
    warehouses: List<Warehouse> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
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
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الكود: ${item.code}", fontSize = 11.sp, color = Color.Gray)
                        Text("|", fontSize = 11.sp, color = Color.LightGray)
                        Text("القسم: ${item.category}", fontSize = 11.sp, color = Color.Gray)
                        if (item.location.isNotEmpty()) {
                            Text("|", fontSize = 11.sp, color = Color.LightGray)
                            Text("الرف: ${item.location}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red) }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("سعر الشراء", fontSize = 10.sp, color = Color.Gray)
                    Text("${item.purchasePrice} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("سعر البيع", fontSize = 10.sp, color = Color.Gray)
                    Text("${item.salePrice} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (item.wholesalePrice > 0) {
                    Column {
                        Text("الجملة", fontSize = 10.sp, color = Color.Gray)
                        Text("${item.wholesalePrice} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00796B))
                    }
                }
                Column {
                    Text("المخزون الإجمالي", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = "${item.currentQuantity} ${item.unit}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.currentQuantity <= item.minLimit) Color.Red else Color(0xFF2E7D32)
                    )
                }
            }

            // Expandable Warehouse Stocks Section
            if (expanded && warehouses.isNotEmpty()) {
                val itemWarehouseStocks = itemStocks.filter { it.itemId == item.id }
                if (itemWarehouseStocks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("توزيع المخزون عبر المستودعات:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemWarehouseStocks.forEach { stock ->
                            val wName = warehouses.find { it.id == stock.warehouseId }?.name ?: "مستودع مجهول"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• $wName", fontSize = 11.sp, color = Color.DarkGray)
                                Text("${stock.quantity} ${item.unit}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stock.quantity <= 0) Color.Red else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarehouseRow(w: Warehouse, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(w.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("العنوان والفرع: ${w.location.ifEmpty { "الرئيسي" }}", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red) }
        }
    }
}

@Composable
fun ItemUnitsManagerSection(
    itemId: Long,
    tempUnits: MutableList<ItemUnit>,
    itemUnitsList: List<ItemUnit>,
    presetUnits: List<String>,
    onAddUnit: (ItemUnit) -> Unit,
    onDeleteUnit: (ItemUnit) -> Unit
) {
    var uName by remember { mutableStateOf("كرتون") }
    var uFactor by remember { mutableStateOf("12.0") }
    var uPurPrice by remember { mutableStateOf("0.0") }
    var uSalPrice by remember { mutableStateOf("0.0") }
    var uBarcode by remember { mutableStateOf("") }

    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            uBarcode = result.contents
        }
    }

    val filteredUnits = if (itemId > 0) {
        itemUnitsList.filter { it.itemId == itemId }
    } else {
        tempUnits
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "الوحدات الفرعية المتعددة للصنف (كرتون، علبة، الخ):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Current Units List
            if (filteredUnits.isEmpty()) {
                Text("لا توجد وحدات فرعية مضافة بعد. يمكنك اختيار وإضافة وحدة فرعية بسهولة من الأسفل.", fontSize = 11.sp, color = Color.Gray)
            } else {
                filteredUnits.forEach { unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${unit.unitName} (تحتوي: ${unit.conversionFactor} قطعة/وحدة)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("سعر الشراء: ${unit.purchasePrice} ر.ي | سعر البيع: ${unit.salePrice} ر.ي", fontSize = 10.sp, color = Color.Gray)
                            if (unit.barcode.isNotEmpty()) {
                                Text("الباركود: ${unit.barcode}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { onDeleteUnit(unit) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الوحدة", tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            Text("إضافة وحدة فرعية جديدة من القائمة المنسدلة:", fontWeight = FontWeight.Bold, fontSize = 11.sp)

            // Dropdown selector for unit name
            DropdownSelector(
                options = presetUnits,
                selectedOption = uName,
                onOptionSelected = { uName = it }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uFactor,
                    onValueChange = { uFactor = it },
                    label = { Text("معامل التحويل (كم يحتوي؟)", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = uPurPrice,
                    onValueChange = { uPurPrice = it },
                    label = { Text("سعر شراء الوحدة", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uSalPrice,
                    onValueChange = { uSalPrice = it },
                    label = { Text("سعر بيع الوحدة", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = uBarcode,
                    onValueChange = { uBarcode = it },
                    label = { Text("باركود الوحدة (اختياري)", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val options = com.journeyapps.barcodescanner.ScanOptions()
                            options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.ALL_CODE_TYPES)
                            options.setPrompt("امسح باركود الوحدة")
                            options.setBeepEnabled(true)
                            options.setBarcodeImageEnabled(true)
                            scanLauncher.launch(options)
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح باركود الوحدة", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }

            Button(
                onClick = {
                    if (uName.isNotEmpty()) {
                        val factor = uFactor.toDoubleOrNull() ?: 1.0
                        val pur = uPurPrice.toDoubleOrNull() ?: 0.0
                        val sal = uSalPrice.toDoubleOrNull() ?: 0.0
                        val newUnit = ItemUnit(
                            itemId = itemId,
                            unitName = uName,
                            conversionFactor = factor,
                            purchasePrice = pur,
                            salePrice = sal,
                            barcode = uBarcode
                        )
                        onAddUnit(newUnit)
                        // Reset inputs
                        uFactor = "12.0"
                        uPurPrice = "0.0"
                        uSalPrice = "0.0"
                        uBarcode = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة هذه الوحدة الفرعية للصنف", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiItemStockIssueDialog(
    viewModel: AppViewModel,
    items: List<Item>,
    warehouses: List<Warehouse>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currencies by viewModel.currencies.collectAsState()

    var selectedWarehouse by remember { mutableStateOf(warehouses.firstOrNull()) }
    var selectedCurrencyCode by remember { mutableStateOf("YER") }
    var exchangeRateInput by remember { mutableStateOf("1.0") }
    var generalNotes by remember { mutableStateOf("") }

    // Item selection form state
    var selectedItemToAdd by remember { mutableStateOf<Item?>(null) }
    var qtyInput by remember { mutableStateOf("1") }
    var costInput by remember { mutableStateOf("0.0") }

    val supplyBasket = remember { mutableStateListOf<Triple<Item, com.example.ui.viewmodel.StockSupplyEntry, Double>>() }

    val exchangeRate = exchangeRateInput.toDoubleOrNull() ?: 1.0
    val totalAmountInCurrency = supplyBasket.sumOf { it.third }
    val totalAmountLocal = totalAmountInCurrency * exchangeRate

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Output,
                            contentDescription = "صرف مخزني",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "📤 سند صرف مخزني (تالف/استخدام داخلي)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warehouse & Currency Row
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("بيانات المستودع والعملة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (warehouses.isNotEmpty()) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text("يصرف من مستودع:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        DropdownSelector(
                                            options = warehouses.map { it.name },
                                            selectedOption = selectedWarehouse?.name ?: warehouses.first().name,
                                            onOptionSelected = { selected ->
                                                selectedWarehouse = warehouses.find { it.name == selected }
                                            }
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("العملة المرجعية:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val currencyOptions = if (currencies.isEmpty()) listOf("YER", "SAR", "USD") else currencies.map { it.code }
                                    DropdownSelector(
                                        options = currencyOptions,
                                        selectedOption = selectedCurrencyCode,
                                        onOptionSelected = { selected ->
                                            selectedCurrencyCode = selected
                                            val currObj = currencies.find { it.code == selected }
                                            if (currObj != null && currObj.exchangeRate > 0) {
                                                exchangeRateInput = currObj.exchangeRate.toString()
                                            } else if (selected == "YER") {
                                                exchangeRateInput = "1.0"
                                            }
                                        }
                                    )
                                }

                                if (selectedCurrencyCode != "YER") {
                                    Column(modifier = Modifier.weight(0.9f)) {
                                        Text("سعر الصرف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = exchangeRateInput,
                                            onValueChange = { exchangeRateInput = it },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Item Configurator Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➖ صرف صنف من المخزون", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)

                            Text("اختر الصنف المراد صرفه:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            DropdownSelector(
                                options = items.map { "${it.name} (${it.code}) [${it.currentQuantity}]" },
                                selectedOption = selectedItemToAdd?.let { "${it.name} (${it.code}) [${it.currentQuantity}]" } ?: "اختر الصنف",
                                onOptionSelected = { selected ->
                                    val matched = items.find { selected.startsWith("${it.name} (${it.code})") }
                                    selectedItemToAdd = matched
                                    if (matched != null && matched.purchasePrice > 0) {
                                        costInput = matched.purchasePrice.toString()
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = qtyInput,
                                    onValueChange = { qtyInput = it },
                                    label = { Text("الكمية المصروفة *") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = costInput,
                                    onValueChange = { costInput = it },
                                    label = { Text("تكلفة الصرف المقدرة") },
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            Button(
                                onClick = {
                                    val item = selectedItemToAdd
                                    val qty = qtyInput.toDoubleOrNull() ?: 0.0
                                    val cost = costInput.toDoubleOrNull() ?: 0.0
                                    if (item != null && qty > 0) {
                                        if (qty > item.currentQuantity) {
                                            Toast.makeText(context, "تنبيه: الكمية المصروفة أكبر من المتوفر (${item.currentQuantity})", Toast.LENGTH_SHORT).show()
                                        }
                                        val entry = com.example.ui.viewmodel.StockSupplyEntry(
                                            itemId = item.id,
                                            quantity = qty,
                                            unitCostInCurrency = cost,
                                            expiryDate = ""
                                        )
                                        supplyBasket.add(Triple(item, entry, qty * cost))
                                        qtyInput = "1"
                                        Toast.makeText(context, "تمت إضافة ${item.name} إلى السند", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "يرجى اختيار الصنف وتحديد كمية صحيحة", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إضافة")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إدراج ضمن قائمة الصرف", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Basket Table
                    if (supplyBasket.isNotEmpty()) {
                        Text("📋 الأصناف المصروفة في السند (${supplyBasket.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                supplyBasket.forEachIndexed { idx, row ->
                                    val (item, entry, subTotal) = row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("الرمز: ${item.code}", fontSize = 10.sp, color = Color.Gray)
                                        }

                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                            Text("صرف: ${entry.quantity} ${item.unit}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                            Text("التكلفة: $subTotal $selectedCurrencyCode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(
                                            onClick = { supplyBasket.removeAt(idx) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = generalNotes,
                        onValueChange = { generalNotes = it },
                        label = { Text("سبب الصرف / المستلم / ملاحظات عامة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Total & Save Footer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إجمالي تكلفة المصروف ($selectedCurrencyCode):", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "$totalAmountInCurrency $selectedCurrencyCode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("إلغاء") }

                    Button(
                        onClick = {
                            if (supplyBasket.isNotEmpty()) {
                                val wId = selectedWarehouse?.id ?: 0L
                                val entriesList = supplyBasket.map { it.second }
                                viewModel.issueStockMulti(
                                    entries = entriesList,
                                    warehouseId = wId,
                                    currencyCode = selectedCurrencyCode,
                                    exchangeRate = exchangeRate,
                                    generalNotes = generalNotes.trim()
                                )
                                onDismiss()
                                Toast.makeText(context, "✅ تم حفظ سند الصرف المخزني بنجاح", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "يرجى إضافة صنف واحد على الأقل", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "حفظ")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اعتماد وحفظ الصرف 📤", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
