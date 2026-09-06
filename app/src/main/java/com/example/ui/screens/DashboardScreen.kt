package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.AppViewModel
import java.text.NumberFormat
import java.util.*

val ProfitGreen = Color(0xFF2E7D32)

data class FinancialItem(
    val title: String,
    val value: Double,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val isInteger: Boolean = false
)

data class QuickAction(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val screen: AppScreen?, val actionKey: String? = null, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: AppViewModel, onNavigate: (AppScreen) -> Unit) {
    val scrollState = rememberScrollState()

    // Collect data reactively from ViewModel avoiding memory leaks
    val accounts by viewModel.accounts.collectAsState()
    val contacts by viewModel.contacts.collectAsState() // Retained for alert checks
    val lowStockItems by viewModel.lowStockItems.collectAsState()
    
    val itemsCount by viewModel.dashboardItemsCount.collectAsState()
    val inventoryValue by viewModel.dashboardStockValue.collectAsState()
    val clientsCount by viewModel.dashboardClientsCount.collectAsState()
    val suppliersCount by viewModel.dashboardSuppliersCount.collectAsState()
    
    // For display only (Daily Payment Vouchers)
    val _dailyExpenses by viewModel.dashboardExpenses.collectAsState()
    val dailyExpenses = _dailyExpenses ?: 0.0
    
    // High performance exact accounting metrics from Accounts (Chart of Accounts)
    val totalSales = accounts.find { it.code == "4101" }?.balance ?: 0.0
    val totalPurchases = accounts.find { it.code == "5102" }?.balance ?: 0.0
    val cogs = accounts.find { it.code == "5101" }?.balance ?: 0.0
    
    val totalRevenues = accounts.filter { it.type == "REVENUE" && it.code.length > 2 }.sumOf { it.balance }
    val totalExpenses = accounts.filter { it.type == "EXPENSES" && it.code.length > 2 }.sumOf { it.balance }
    
    val netProfit = totalRevenues - totalExpenses

    val boxBalance = accounts.find { it.code == "1101" }?.balance ?: 0.0
    val bankBalance = accounts.find { it.code == "1102" }?.balance ?: 0.0

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showRemittanceDialog by remember { mutableStateOf(false) }
    var showExchangeDialog by remember { mutableStateOf(false) }
    var showCalculatorDialog by remember { mutableStateOf(false) }

    val onCustomAction = { action: String ->
        when (action) {
            "REMITTANCE" -> showRemittanceDialog = true
            "EXCHANGE" -> showExchangeDialog = true
            "CALCULATOR" -> showCalculatorDialog = true
        }
    }

    if (showCalculatorDialog) {
        CalculatorDialog(onDismiss = { showCalculatorDialog = false })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                viewModel = viewModel,
                onNavigate = onNavigate,
                onCustomAction = onCustomAction,
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onNavigate(AppScreen.AI_ASSISTANT) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = "المساعد الذكي")
                }
            }
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Immersive Header Row
                ImmersiveHeader(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenCalculator = { showCalculatorDialog = true }
                )

                // Real-time Supabase Sync Status Chip
                val isSupabaseEnabled by viewModel.supabaseSyncManager.isSyncEnabled.collectAsState()
                val supabaseStatus by viewModel.supabaseSyncManager.syncStatusMessage.collectAsState()
                val isSynced = viewModel.licenseManager.isSyncedWithCompany()

                if (isSynced || isSupabaseEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(AppScreen.SETTINGS) },
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                                Text("التزامن اللحظي:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                Text(supabaseStatus, fontSize = 11.sp, color = Color(0xFF2E7D32))
                            }
                            Text("إعدادات ⚙️", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Trial Banner for non-synced trial account
                    val remainingDays = viewModel.licenseManager.getRemainingDays()
                    val remainingOps = viewModel.licenseManager.getRemainingOperations()
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(AppScreen.LOGIN) },
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFE65100))
                                Column {
                                    Text("باقة تجريبية (مؤسسة غير متزامنة)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    Text("متبقي: $remainingDays أيام | $remainingOps عملية مجانية", fontSize = 10.sp, color = Color(0xFFEF6C00))
                                }
                            }
                            Text("انضمام لمؤسسة 🔗", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            // License Banner (Shown ONLY when account is blocked or revoked)
            val licenseStatus by viewModel.licenseStatus.collectAsState()

            when (licenseStatus) {
                is com.example.util.LicenseStatus.LimitReached, is com.example.util.LicenseStatus.Expired -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(AppScreen.LICENSE) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "محظور", tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تنبيه الترخيص: توقفت إضافة عمليات جديدة", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("اضغط هنا لتفعيل الاشتراك وتوثيق جهازك لتتمكن من إنشاء فواتير وسندات جديدة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                else -> {}
            }



            // Quick Actions Panel
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "الوصول السريع والعمليات",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            QuickActionsList(
                viewModel = viewModel,
                onNavigate = onNavigate,
                onCustomAction = onCustomAction
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Render dialogs inside the main screen composable but outside the scrollable Column
        if (showRemittanceDialog) {
            RemittanceDialog(viewModel = viewModel, onDismiss = { showRemittanceDialog = false })
        }
        if (showExchangeDialog) {
            CurrencyExchangeDialog(viewModel = viewModel, onDismiss = { showExchangeDialog = false })
        }
    }
}
}

@Composable
fun AppDrawerContent(
    viewModel: AppViewModel,
    onNavigate: (AppScreen) -> Unit,
    onCustomAction: (String) -> Unit,
    closeDrawer: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val appName = settings?.name ?: "آفاق محاسب"

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(320.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.take(1),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المدير العام",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        val isDarkMode by viewModel.isDarkMode.collectAsState()
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { viewModel.toggleDarkMode() },
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isDarkMode) "ليلي 🌙" else "نهاري ☀️",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Menu Items
            val actions = listOf(
                QuickAction("الشاشة الرئيسية", Icons.Default.Dashboard, AppScreen.DASHBOARD, null, Color(0xFF0288D1)),
                QuickAction("سجل وإدارة العمليات", Icons.Default.HistoryEdu, AppScreen.OPERATIONS, null, Color(0xFF1E88E5)),
                QuickAction("حوالة جديدة", Icons.Default.Send, null, "REMITTANCE", Color(0xFF0288D1)),
                QuickAction("صرف عملات", Icons.Default.CurrencyExchange, null, "EXCHANGE", Color(0xFF2E7D32)),
                QuickAction("فواتير المبيعات", Icons.Default.ReceiptLong, AppScreen.SALES, null, Color(0xFF0061A4)),
                QuickAction("فواتير المشتريات", Icons.Default.LocalShipping, AppScreen.PURCHASES, null, Color(0xFFC62828)),
                QuickAction("الأصناف والمخازن", Icons.Default.Inventory, AppScreen.INVENTORY, null, Color(0xFF7B1FA2)),
                QuickAction("العملاء والموردين", Icons.Default.ContactPage, AppScreen.CONTACTS, null, Color(0xFF0288D1)),
                QuickAction("الصندوق والبنك", Icons.Default.AccountBalanceWallet, AppScreen.TREASURY, null, Color(0xFF00796B)),
                QuickAction("دليل الحسابات", Icons.Default.AccountTree, AppScreen.JOURNAL_ENTRIES, null, Color(0xFFEF6C00)),
                QuickAction("التقارير الشاملة", Icons.Default.Assessment, AppScreen.REPORTS, null, Color(0xFF455A64)),
                QuickAction("إعدادات المؤسسة", Icons.Default.Settings, AppScreen.SETTINGS, null, Color(0xFF00897B)),
                QuickAction("إدارة المستخدمين", Icons.Default.ManageAccounts, AppScreen.USER_MANAGEMENT, null, Color(0xFFD81B60)),
                QuickAction("المساعد الذكي", Icons.Default.SmartToy, AppScreen.AI_ASSISTANT, null, Color(0xFF3F51B5))
            )

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(actions.size) { index ->
                    val action = actions[index]
                    NavigationDrawerItem(
                        icon = { Icon(action.icon, contentDescription = null, tint = action.color) },
                        label = { Text(action.title, fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = {
                            closeDrawer()
                            if (action.screen != null) {
                                onNavigate(action.screen)
                            } else if (action.actionKey != null) {
                                onCustomAction(action.actionKey)
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = action.color,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImmersiveHeader(viewModel: AppViewModel, onNavigate: (AppScreen) -> Unit, onOpenDrawer: () -> Unit, onOpenCalculator: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val appName = settings?.name ?: "آفاق محاسب"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // السطر الأول: اسم المؤسسة واسم المدير (يمين) والأزرار الثلاثة (يسار)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // اسم المؤسسة واسم المدير
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = appName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "مرحباً، المدير العام 👋",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // الأزرار (مقابل اسم المستخدم في أقصى اليسار)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Calculator
                    IconButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "آلة حاسبة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // License (Shown only for Admin users)
                    val currentUserState by viewModel.currentUser.collectAsState()
                    val isAdminUser = currentUserState?.username.equals("admin", ignoreCase = true) || currentUserState?.permSettings == true

                    if (isAdminUser) {
                        IconButton(
                            onClick = { onNavigate(AppScreen.LICENSE) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "الترخيص",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Logout
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "تسجيل الخروج",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Menu Button (القائمة الجانبية)
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "القائمة الجانبية",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImmersiveKeyMetricsCard(todaySales: Double, salesGrowth: Double, netProfit: Double, boxBalance: Double, viewModel: AppViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary,
                            Color(0xFF004A7E),
                            Color(0xFF002F56)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "إجمالي المبيعات (اليوم)",
                            fontSize = 11.sp,
                            color = Color(0xFFD1E4FF),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCurrency(todaySales),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    // Growth Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val growthText = if (salesGrowth > 0) "+${salesGrowth.toInt()}%" else "${salesGrowth.toInt()}%"
                            val color = if (salesGrowth >= 0) Color(0xFF4ADE80) else Color(0xFFEF4444)
                            Text(
                                text = growthText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                text = "نمو",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Divider(
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "صافي الأرباح",
                            fontSize = 11.sp,
                            color = Color(0xFFD1E4FF).copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatCurrency(netProfit),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "رصيد الصندوق",
                            fontSize = 11.sp,
                            color = Color(0xFFD1E4FF).copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatCurrency(boxBalance),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardAiCard(onNavigate: (AppScreen) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF0061A4), Color(0xFF9333EA))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 14.sp, color = Color.White)
                    }
                    
                    Text(
                        text = "المساعد الذكي (AI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFDBEAFE))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "تحليل مباشر",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "\"انخفضت مبيعات 'أصناف الإلكترونيات' بنسبة 5% هذا الأسبوع. أقترح تفعيل عرض ترويجي للعملاء الدائمين.\"",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 16.sp
                )
            }
            
            Button(
                onClick = { onNavigate(AppScreen.AI_ASSISTANT) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "اسأل المساعد الذكي الآن",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AlertsSection(
    lowStock: List<Item>,
    overCreditContacts: List<com.example.data.model.Contact>,
    expiringSoon: List<Item> = emptyList()
) {
    if (lowStock.isNotEmpty() || overCreditContacts.isNotEmpty() || expiringSoon.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "تنبيهات عاجلة (${lowStock.size + overCreditContacts.size + expiringSoon.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (expiringSoon.isNotEmpty()) {
                var expandedExpiring by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { expandedExpiring = !expandedExpiring },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFEA580C), CircleShape)
                            )
                            Text(
                                text = "⚠️ تنبيه: ${expiringSoon.size} أصناف شارفت تاريخ الانتهاء أو منتهية الصلاحية!",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9A3412),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (expandedExpiring) "إخفاء التفاصيل ▲" else "عرض التفاصيل ▼",
                                fontSize = 10.sp,
                                color = Color(0xFFC2410C),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (expandedExpiring) {
                            Divider(color = Color(0xFFFED7AA))
                            expiringSoon.forEach { item ->
                                val daysLeft = try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val exp = sdf.parse(item.expiryDate.trim())
                                    if (exp != null) {
                                        val diff = exp.time - java.util.Date().time
                                        diff / (1000 * 60 * 60 * 24)
                                    } else 0L
                                } catch (e: Exception) { 0L }

                                val statusText = when {
                                    daysLeft < 0 -> "منتهي الصلاحية منذ ${Math.abs(daysLeft)} يوم!"
                                    daysLeft == 0L -> "ينتهي اليوم!"
                                    else -> "متبقي $daysLeft يوم على الانتهاء"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF431407))
                                        Text("الباركود: ${item.barcode.ifEmpty { item.code }} | الانتهاء: ${item.expiryDate}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(statusText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (daysLeft <= 0) Color(0xFFDC2626) else Color(0xFFD97706))
                                        Text("المخزون: ${item.currentQuantity} ${item.unit}", fontSize = 10.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (lowStock.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = "${lowStock.size} أصناف وصلت للحد الأدنى من المخزون!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (overCreditContacts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = "${overCreditContacts.size} عملاء تجاوزوا الحد الائتماني المسموح به!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FinancialGrid(
    sales: Double, purchases: Double, profit: Double, expenses: Double, dailyExpenses: Double,
    box: Double, bank: Double, stockValue: Double, itemsCount: Int,
    clients: Int, suppliers: Int
) {
    val itemsList = listOf(
        FinancialItem("إجمالي المبيعات", sales, Icons.Default.TrendingUp, Color(0xFF2E7D32)),
        FinancialItem("إجمالي المشتريات", purchases, Icons.Default.ShoppingCart, Color(0xFFC62828)),
        FinancialItem("إجمالي الأرباح", profit, Icons.Default.AttachMoney, Color(0xFF0061A4)),
        FinancialItem("إجمالي حساب المصروفات", expenses, Icons.Default.MoneyOff, Color(0xFFE65100)),
        FinancialItem("المصروفات اليومية (سندات)", dailyExpenses, Icons.Default.ReceiptLong, Color(0xFFD84315)),
        FinancialItem("رصيد الصندوق", box, Icons.Default.AccountBalanceWallet, Color(0xFF00796B)),
        FinancialItem("رصيد البنك", bank, Icons.Default.AccountBalance, Color(0xFF0288D1)),
        FinancialItem("قيمة المخزون", stockValue, Icons.Default.Inventory2, Color(0xFF7B1FA2)),
        FinancialItem("عدد الأصناف", itemsCount.toDouble(), Icons.Default.Category, Color(0xFF455A64), isInteger = true),
        FinancialItem("إجمالي العملاء", clients.toDouble(), Icons.Default.People, Color(0xFF0097A7), isInteger = true),
        FinancialItem("إجمالي الموردين", suppliers.toDouble(), Icons.Default.Groups, Color(0xFFE64A19), isInteger = true)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val chunked = itemsList.chunked(2)
        for (pair in chunked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (item in pair) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Beautiful circular background for icon
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(item.color.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = item.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (item.isInteger) item.value.toInt().toString() else formatCurrency(item.value),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessPerformanceChart(salesData: List<Float>, profitsData: List<Float>) {
    val daysLabels = listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
    val salesColor = ProfitGreen
    val profitColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Chart Header & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 الأداء الأسبوعي (المبيعات والأرباح)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(salesColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("المبيعات", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = salesColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(profitColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الأرباح", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = profitColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart Area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height * 0.82f // Leave room for day labels

                    // Draw background horizontal Grid lines
                    for (i in 0..3) {
                        val y = height * (i / 3f)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.15f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    if (salesData.isEmpty() || profitsData.isEmpty()) return@Canvas

                    val maxSale = salesData.maxOrNull() ?: 1f
                    val maxProfit = profitsData.maxOrNull() ?: 1f
                    val maxVal = maxOf(maxSale, maxProfit).coerceAtLeast(1f)

                    val stepX = width / (salesData.size - 1).coerceAtLeast(1)

                    fun getPoint(index: Int, value: Float): Offset {
                        val x = index * stepX
                        val y = height - (value / maxVal) * (height * 0.85f)
                        return Offset(x, y)
                    }

                    // Build Smooth Bezier Path for Sales
                    val salesPath = Path()
                    val salesFillPath = Path()
                    salesData.forEachIndexed { index, value ->
                        val p = getPoint(index, value)
                        if (index == 0) {
                            salesPath.moveTo(p.x, p.y)
                            salesFillPath.moveTo(p.x, height)
                            salesFillPath.lineTo(p.x, p.y)
                        } else {
                            val prevP = getPoint(index - 1, salesData[index - 1])
                            val controlX1 = prevP.x + (p.x - prevP.x) / 2f
                            val controlY1 = prevP.y
                            val controlX2 = prevP.x + (p.x - prevP.x) / 2f
                            val controlY2 = p.y
                            salesPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p.x, p.y)
                            salesFillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p.x, p.y)
                        }
                    }
                    val lastSalesP = getPoint(salesData.size - 1, salesData.last())
                    salesFillPath.lineTo(lastSalesP.x, height)
                    salesFillPath.close()

                    // Build Smooth Bezier Path for Profit
                    val profitPath = Path()
                    val profitFillPath = Path()
                    profitsData.forEachIndexed { index, value ->
                        val p = getPoint(index, value)
                        if (index == 0) {
                            profitPath.moveTo(p.x, p.y)
                            profitFillPath.moveTo(p.x, height)
                            profitFillPath.lineTo(p.x, p.y)
                        } else {
                            val prevP = getPoint(index - 1, profitsData[index - 1])
                            val controlX1 = prevP.x + (p.x - prevP.x) / 2f
                            val controlY1 = prevP.y
                            val controlX2 = prevP.x + (p.x - prevP.x) / 2f
                            val controlY2 = p.y
                            profitPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p.x, p.y)
                            profitFillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p.x, p.y)
                        }
                    }
                    val lastProfitP = getPoint(profitsData.size - 1, profitsData.last())
                    profitFillPath.lineTo(lastProfitP.x, height)
                    profitFillPath.close()

                    // Draw Gradient Fills
                    drawPath(
                        path = salesFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(salesColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )
                    drawPath(
                        path = profitFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(profitColor.copy(alpha = 0.20f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // Draw Smooth Strokes
                    drawPath(path = salesPath, color = salesColor, style = Stroke(width = 3.5f))
                    drawPath(path = profitPath, color = profitColor, style = Stroke(width = 3.5f))

                    // Draw Glowing Dots
                    salesData.forEachIndexed { index, value ->
                        val p = getPoint(index, value)
                        drawCircle(color = salesColor, radius = 5f, center = p)
                        drawCircle(color = Color.White, radius = 2.5f, center = p)
                    }
                    profitsData.forEachIndexed { index, value ->
                        val p = getPoint(index, value)
                        drawCircle(color = profitColor, radius = 5f, center = p)
                        drawCircle(color = Color.White, radius = 2.5f, center = p)
                    }
                }
            }

            // Days Labels Row at Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysLabels.take(salesData.size).forEach { day ->
                    Text(
                        text = day,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsList(
    viewModel: AppViewModel,
    onNavigate: (AppScreen) -> Unit,
    onCustomAction: (String) -> Unit
) {
    val actions = listOf(
        QuickAction("فاتورة مبيعات", Icons.Default.PointOfSale, AppScreen.SALES, null, Color(0xFF0061A4)),
        QuickAction("فاتورة مشتريات", Icons.Default.ShoppingCart, AppScreen.PURCHASES, null, Color(0xFFC62828)),
        QuickAction("مرتجع مبيعات", Icons.Default.AssignmentReturn, AppScreen.SALES_RETURN, null, Color(0xFFE65100)),
        QuickAction("مرتجع مشتريات", Icons.Default.AssignmentReturned, AppScreen.PURCHASES_RETURN, null, Color(0xFFD84315)),
        QuickAction("سند قبض (استلام)", Icons.Default.AddCard, null, "RECEIPT", Color(0xFF1B5E20)),
        QuickAction("سند صرف (دفع)", Icons.Default.MoneyOff, null, "PAYMENT", Color(0xFFB71C1C)),
        QuickAction("قيد يومي", Icons.Default.AccountTree, AppScreen.JOURNAL_ENTRIES, "NEW_ENTRY", Color(0xFF455A64)),
        QuickAction("إضافة عميل", Icons.Default.PersonAdd, AppScreen.CONTACTS, "NEW_CUSTOMER", Color(0xFF0288D1)),
        QuickAction("إضافة مورد", Icons.Default.DomainAdd, AppScreen.CONTACTS, "NEW_SUPPLIER", Color(0xFF00796B)),
        QuickAction("إضافة صنف", Icons.Default.AddBox, AppScreen.INVENTORY, "NEW_ITEM", Color(0xFF7B1FA2)),
        QuickAction("سند توريد مخزني", Icons.Default.Inventory, null, "STOCK_SUPPLY", Color(0xFF7B1FA2)),
        QuickAction("سند تحويل مخزني", Icons.Default.LocalShipping, null, "STOCK_TRANSFER", Color(0xFF0288D1)),
        QuickAction("سند صرف مخزني", Icons.Default.Output, null, "STOCK_ISSUE", Color(0xFFE65100))
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowActions.forEach { action ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clickable {
                                if (action.screen != null && action.actionKey != null) {
                                    viewModel.navigateWithDialog(action.screen, action.actionKey, onNavigate)
                                } else if (action.actionKey == "RECEIPT" || action.actionKey == "PAYMENT") {
                                    viewModel.navigateWithDialog(AppScreen.TREASURY, action.actionKey, onNavigate)
                                } else if (action.actionKey?.startsWith("STOCK_") == true) {
                                    viewModel.navigateWithDialog(AppScreen.INVENTORY, action.actionKey, onNavigate)
                                } else if (action.screen != null) {
                                    onNavigate(action.screen)
                                } else if (action.actionKey != null) {
                                    onCustomAction(action.actionKey)
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(action.color)
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(action.color.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = action.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("ar", "YE"))
    return format.format(amount).replace("YER", "ر.ي")
}

