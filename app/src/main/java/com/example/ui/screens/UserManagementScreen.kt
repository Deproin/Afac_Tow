package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLog
import com.example.data.model.User
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<User?>(null) }
    var selectedUserForPasswordReset by remember { mutableStateOf<User?>(null) }

    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var resetPassInput by remember { mutableStateOf("") }


    // Tab state (Users vs Audit Logs)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("قائمة المستخدمين والصلاحيات", "سجل العمليات والرقابة (Audit Log)")

    // Audit log search & filter
    var auditSearchText by remember { mutableStateOf("") }
    var selectedOperationFilter by remember { mutableStateOf("الكل") }

    val activeUsersCount = remember(users) { users.count { !it.isSuspended } }
    val suspendedUsersCount = remember(users) { users.count { it.isSuspended } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة المستخدمين والصلاحيات والأمن", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            val canManageUsers = viewModel.licenseManager.canManageUsers()
            var showLockMessage by remember { mutableStateOf(false) }

            if (showLockMessage) {
                AlertDialog(
                    onDismissRequest = { showLockMessage = false },
                    title = { Text("🔒 ميزة مقفلة", fontWeight = FontWeight.Bold) },
                    text = { Text("إضافة واستدعاء المستخدمين تتطلب الربط بكود التزامن الخاص بمؤسستك سحابياً.") },
                    confirmButton = {
                        TextButton(onClick = { showLockMessage = false }) {
                            Text("حسناً")
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        if (canManageUsers) {
                            usernameInput = ""
                            passwordInput = ""
                            showAddDialog = true
                        } else {
                            showLockMessage = true
                        }
                    },
                    containerColor = if (canManageUsers) MaterialTheme.colorScheme.primary else Color.Gray,
                    contentColor = Color.White
                ) {
                    Icon(if (canManageUsers) Icons.Default.Add else Icons.Default.Lock, contentDescription = "إضافة مستخدم")
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }

            if (selectedTab == 0) {
                // TAB 0: List Users & Edit permissions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Security Header Summary Card
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
                                Text("إجمالي حسابات المستخدمين المسجلة", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                Text("${users.size} مستخدم", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("نشط 🟢", fontSize = 10.sp, color = Color.Gray)
                                    Text("$activeUsersCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("موقوف 🔴", fontSize = 10.sp, color = Color.Gray)
                                    Text("$suspendedUsersCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (suspendedUsersCount > 0) Color.Red else Color.Gray)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(users) { user ->
                            UserListItem(
                                user = user,
                                onEditPermissions = { selectedUserForEdit = user },
                                onResetPassword = {
                                    resetPassInput = ""
                                    selectedUserForPasswordReset = user
                                },
                                onToggleSuspend = {
                                    viewModel.updateUser(user.copy(isSuspended = !user.isSuspended))
                                },
                                onDelete = {
                                    viewModel.deleteUser(user)
                                }
                            )
                        }

                    }
                }
            } else {
                // TAB 1: Audit Logs View with Search & Filter
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "سجل الرقابة لجميع حركات النظام (${auditLogs.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )


                    OutlinedTextField(
                        value = auditSearchText,
                        onValueChange = { auditSearchText = it },
                        placeholder = { Text("ابحث في السجل بالاسم، الجدول، أو الوصف...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    val opFilters = listOf("الكل", "إضافة", "تعديل", "حذف", "دخول")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        opFilters.forEach { filterName ->
                            FilterChip(
                                selected = selectedOperationFilter == filterName,
                                onClick = { selectedOperationFilter = filterName },
                                label = { Text(filterName, fontSize = 10.sp) }
                            )
                        }
                    }

                    val filteredAuditLogs = auditLogs.filter { log ->
                        (selectedOperationFilter == "الكل" || log.operationType.contains(selectedOperationFilter, ignoreCase = true)) &&
                                (log.username.contains(auditSearchText, ignoreCase = true) ||
                                        log.tableName.contains(auditSearchText, ignoreCase = true) ||
                                        log.details.contains(auditSearchText, ignoreCase = true))
                    }

                    if (filteredAuditLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد سجلات مطابقة للبحث", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredAuditLogs) { log ->
                                AuditLogItem(log = log, onDelete = { viewModel.deleteAuditLog(log) })
                            }
                        }
                    }

                }
            }
        }

        // Add User Dialog with Role Preset Selector
        if (showAddDialog) {
            var selectedRolePreset by remember { mutableStateOf("CASHIER") }
            var selWhId by remember { mutableStateOf<Long?>(null) }
            var whExpanded by remember { mutableStateOf(false) }
            var selAccId by remember { mutableStateOf<Long?>(null) }
            var accExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("إنشاء مستخدم جديد للنظام", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("اسم المستخدم (Username) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("كلمة المرور المراد اعتمادها *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("اختر الدور الوظيفي للمستخدم الجديد:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = selectedRolePreset == "ADMIN",
                                onClick = { selectedRolePreset = "ADMIN" },
                                label = { Text("👑 مدير", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = selectedRolePreset == "ACCOUNTANT",
                                onClick = { selectedRolePreset = "ACCOUNTANT" },
                                label = { Text("💼 محاسب", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = selectedRolePreset == "CASHIER",
                                onClick = { selectedRolePreset = "CASHIER" },
                                label = { Text("🛒 كاشير", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = selectedRolePreset == "AUDITOR",
                                onClick = { selectedRolePreset = "AUDITOR" },
                                label = { Text("🔍 مراجع", fontSize = 10.sp) }
                            )
                        }

                        Divider()
                        
                        val selectedWhName = warehouses.find { it.id == selWhId }?.name ?: "الكل (بدون تقييد)"
                        
                        ExposedDropdownMenuBox(expanded = whExpanded, onExpandedChange = { whExpanded = it }) {
                            OutlinedTextField(
                                value = selectedWhName,
                                onValueChange = {}, readOnly = true,
                                label = { Text("المخزن الافتراضي للمستخدم") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = whExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = whExpanded, onDismissRequest = { whExpanded = false }) {
                                DropdownMenuItem(text = { Text("الكل (بدون تقييد)") }, onClick = { selWhId = null; whExpanded = false })
                                warehouses.forEach { wh ->
                                    DropdownMenuItem(text = { Text(wh.name) }, onClick = { selWhId = wh.id; whExpanded = false })
                                }
                            }
                        }

                        val selectedAccName = accounts.find { it.id == selAccId }?.name ?: "الكل (بدون تقييد)"
                        
                        ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = it }) {
                            OutlinedTextField(
                                value = selectedAccName,
                                onValueChange = {}, readOnly = true,
                                label = { Text("الصندوق الافتراضي للمستخدم") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                                DropdownMenuItem(text = { Text("الكل (بدون تقييد)") }, onClick = { selAccId = null; accExpanded = false })
                                accounts.filter { it.type == "ASSETS" }.forEach { acc ->
                                    DropdownMenuItem(text = { Text(acc.name) }, onClick = { selAccId = acc.id; accExpanded = false })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (usernameInput.trim().isNotEmpty() && passwordInput.trim().isNotEmpty()) {
                                // Extract state outside
                                val finalWhId = selWhId
                                val finalAccId = selAccId
                                val newPerms = when (selectedRolePreset) {
                                    "ADMIN" -> User(username = usernameInput.trim(), passwordHash = passwordInput.trim(), defaultWarehouseId = finalWhId, defaultSafeAccountId = finalAccId, permSale = true, permPurchase = true, permDeleteInvoice = true, permEditInvoice = true, permViewProfits = true, permViewReports = true, permEditPrices = true, permBackup = true, permSettings = true, permAI = true)
                                    "ACCOUNTANT" -> User(username = usernameInput.trim(), passwordHash = passwordInput.trim(), defaultWarehouseId = finalWhId, defaultSafeAccountId = finalAccId, permSale = true, permPurchase = true, permDeleteInvoice = false, permEditInvoice = true, permViewProfits = true, permViewReports = true, permEditPrices = false, permBackup = false, permSettings = false, permAI = true)
                                    "AUDITOR" -> User(username = usernameInput.trim(), passwordHash = passwordInput.trim(), defaultWarehouseId = finalWhId, defaultSafeAccountId = finalAccId, permSale = false, permPurchase = false, permDeleteInvoice = false, permEditInvoice = false, permViewProfits = true, permViewReports = true, permEditPrices = false, permBackup = false, permSettings = false, permAI = false)
                                    else -> User(username = usernameInput.trim(), passwordHash = passwordInput.trim(), defaultWarehouseId = finalWhId, defaultSafeAccountId = finalAccId, permSale = true, permPurchase = false, permDeleteInvoice = false, permEditInvoice = false, permViewProfits = false, permViewReports = false, permEditPrices = false, permBackup = false, permSettings = false, permAI = false)
                                }
                                viewModel.addUser(newPerms)
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("حفظ المستخدم", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") }
                }
            )
        }


        // Edit Permissions Dialog (with Quick Presets for Roles)
        if (selectedUserForEdit != null) {
            val user = selectedUserForEdit!!
            var pSale by remember { mutableStateOf(user.permSale) }
            var pPurchase by remember { mutableStateOf(user.permPurchase) }
            var pDelete by remember { mutableStateOf(user.permDeleteInvoice) }
            var pEdit by remember { mutableStateOf(user.permEditInvoice) }
            var pProfits by remember { mutableStateOf(user.permViewProfits) }
            var pReports by remember { mutableStateOf(user.permViewReports) }
            var pPrices by remember { mutableStateOf(user.permEditPrices) }
            var pBackup by remember { mutableStateOf(user.permBackup) }
            var pSettings by remember { mutableStateOf(user.permSettings) }
            var pAI by remember { mutableStateOf(user.permAI) }
            var editWhId by remember { mutableStateOf(user.defaultWarehouseId) }
            var editAccId by remember { mutableStateOf(user.defaultSafeAccountId) }
            var editWhExpanded by remember { mutableStateOf(false) }
            var editAccExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { selectedUserForEdit = null },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = {
                    Column {
                        Text("إدارة صلاحيات المستخدم: ${user.username}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("منح قوالب الصلاحيات السريعة بحسب الدور الوظيفي:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick Presets Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = pSale && pPurchase && pDelete && pEdit && pProfits && pReports && pPrices && pBackup && pSettings && pAI,
                                onClick = {
                                    pSale = true; pPurchase = true; pDelete = true; pEdit = true; pProfits = true; pReports = true; pPrices = true; pBackup = true; pSettings = true; pAI = true
                                },
                                label = { Text("👑 مدير", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = pSale && pPurchase && pEdit && pReports && pProfits && !pSettings,
                                onClick = {
                                    pSale = true; pPurchase = true; pDelete = false; pEdit = true; pProfits = true; pReports = true; pPrices = false; pBackup = false; pSettings = false; pAI = true
                                },
                                label = { Text("💼 محاسب", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = pSale && !pPurchase && !pReports && !pProfits,
                                onClick = {
                                    pSale = true; pPurchase = false; pDelete = false; pEdit = false; pProfits = false; pReports = false; pPrices = false; pBackup = false; pSettings = false; pAI = false
                                },
                                label = { Text("🛒 كاشير", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = !pSale && pReports && pProfits,
                                onClick = {
                                    pSale = false; pPurchase = false; pDelete = false; pEdit = false; pProfits = true; pReports = true; pPrices = false; pBackup = false; pSettings = false; pAI = false
                                },
                                label = { Text("🔍 مراجع", fontSize = 10.sp) }
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item { PermissionToggle("صلاحية البيع (فواتير المبيعات)", pSale) { pSale = it } }
                            item { PermissionToggle("صلاحية الشراء (فواتير المشتريات)", pPurchase) { pPurchase = it } }
                            item { PermissionToggle("صلاحية حذف الفواتير", pDelete) { pDelete = it } }
                            item { PermissionToggle("صلاحية تعديل الفواتير", pEdit) { pEdit = it } }
                            item { PermissionToggle("صلاحية عرض الأرباح بالشاشات", pProfits) { pProfits = it } }
                            item { PermissionToggle("صلاحية عرض التقارير المالية والختامية", pReports) { pReports = it } }
                            item { PermissionToggle("صلاحية تعديل أسعار الأصناف", pPrices) { pPrices = it } }
                            item { PermissionToggle("صلاحية النسخ الاحتياطي والاستعادة", pBackup) { pBackup = it } }
                            item { PermissionToggle("صلاحية تعديل إعدادات المنشأة", pSettings) { pSettings = it } }
                            item { PermissionToggle("صلاحية استخدام مساعد الذكاء الاصطناعي", pAI) { pAI = it } }
                            
                            item { Divider(modifier = Modifier.padding(vertical = 4.dp)) }
                            
                            item {
                                val selectedWhName = warehouses.find { it.id == editWhId }?.name ?: "الكل (بدون تقييد)"
                                ExposedDropdownMenuBox(expanded = editWhExpanded, onExpandedChange = { editWhExpanded = it }) {
                                    OutlinedTextField(
                                        value = selectedWhName,
                                        onValueChange = {}, readOnly = true,
                                        label = { Text("المخزن الافتراضي للمستخدم") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editWhExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = editWhExpanded, onDismissRequest = { editWhExpanded = false }) {
                                        DropdownMenuItem(text = { Text("الكل (بدون تقييد)") }, onClick = { editWhId = null; editWhExpanded = false })
                                        warehouses.forEach { wh ->
                                            DropdownMenuItem(text = { Text(wh.name) }, onClick = { editWhId = wh.id; editWhExpanded = false })
                                        }
                                    }
                                }
                            }
                            
                            item {
                                val selectedAccName = accounts.find { it.id == editAccId }?.name ?: "الكل (بدون تقييد)"
                                ExposedDropdownMenuBox(expanded = editAccExpanded, onExpandedChange = { editAccExpanded = it }) {
                                    OutlinedTextField(
                                        value = selectedAccName,
                                        onValueChange = {}, readOnly = true,
                                        label = { Text("الصندوق الافتراضي للمستخدم") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editAccExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = editAccExpanded, onDismissRequest = { editAccExpanded = false }) {
                                        DropdownMenuItem(text = { Text("الكل (بدون تقييد)") }, onClick = { editAccId = null; editAccExpanded = false })
                                        accounts.filter { it.type == "ASSETS" }.forEach { acc ->
                                            DropdownMenuItem(text = { Text(acc.name) }, onClick = { editAccId = acc.id; editAccExpanded = false })
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateUser(
                                user.copy(
                                    permSale = pSale,
                                    permPurchase = pPurchase,
                                    permDeleteInvoice = pDelete,
                                    permEditInvoice = pEdit,
                                    permViewProfits = pProfits,
                                    permViewReports = pReports,
                                    permEditPrices = pPrices,
                                    permBackup = pBackup,
                                    permSettings = pSettings,
                                    permAI = pAI,
                                    defaultWarehouseId = editWhId,
                                    defaultSafeAccountId = editAccId,
                                    syncState = "PENDING_UPDATE"
                                )
                            )
                            selectedUserForEdit = null
                        }
                    ) {
                        Text("حفظ وتحديث الصلاحيات", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedUserForEdit = null }) { Text("إلغاء") }
                }
            )
        }
        // Reset Password Dialog for Admin
        if (selectedUserForPasswordReset != null) {
            val user = selectedUserForPasswordReset!!
            AlertDialog(
                onDismissRequest = { selectedUserForPasswordReset = null },
                title = { Text("تغير/إعادة تعيين كلمة المرور للمستخدم: ${user.username}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اكتب كلمة المرور الجديدة المعينة للمستخدم ${user.username}:", fontSize = 12.sp, color = Color.Gray)
                        OutlinedTextField(
                            value = resetPassInput,
                            onValueChange = { resetPassInput = it },
                            label = { Text("كلمة المرور الجديدة *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetPassInput.trim().isNotEmpty()) {
                                viewModel.updateUser(user.copy(passwordHash = resetPassInput.trim()))
                                selectedUserForPasswordReset = null
                            }
                        }
                    ) {
                        Text("حفظ كلمة المرور", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedUserForPasswordReset = null }) { Text("إلغاء") }
                }
            )
        }
    }
}

@Composable
fun UserListItem(
    user: User,
    onEditPermissions: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleSuspend: () -> Unit,
    onDelete: () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "مستخدم",
                        tint = if (user.isSuspended) Color.Gray else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(user.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                val isAdminAccount = user.username.equals("admin", ignoreCase = true)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onEditPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("الصلاحيات", fontSize = 12.sp, color = Color.White)
                    }
                    IconButton(onClick = onResetPassword) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = "تصفير كلمة المرور",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (!isAdminAccount) {
                        IconButton(onClick = onToggleSuspend) {
                            Icon(
                                imageVector = if (user.isSuspended) Icons.Default.PlayArrow else Icons.Default.Block,
                                contentDescription = "تعليق/تفعيل",
                                tint = if (user.isSuspended) Color.Green else Color.Red
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                        }
                    }
                }


            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "حالة الحساب: ${if (user.isSuspended) "موقوف 🔴" else "نشط 🟢"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "آخر دخول: ${if (user.lastLogin > 0) formatTime(user.lastLogin) else "لم يسجل دخول بعد"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun PermissionToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun AuditLogItem(log: AuditLog, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when (log.operationType) {
                                    "إضافة" -> Color.Green
                                    "تعديل" -> Color.Blue
                                    "حذف" -> Color.Red
                                    else -> Color.Gray
                                },
                                RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${log.operationType}: ${log.tableName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(log.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف سجل العملية",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "المستخدم: ${log.username} | تفاصيل العمل: ${log.details}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}


fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
    return sdf.format(Date(timestamp))
}
