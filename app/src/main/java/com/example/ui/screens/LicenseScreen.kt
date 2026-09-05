package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel
import com.example.util.LicenseStatus
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val totalOps by viewModel.totalOperationsCount.collectAsState()
    val licenseStatus by viewModel.licenseStatus.collectAsState()
    val licenseManager = viewModel.licenseManager

    var identifierInput by remember { mutableStateOf(licenseManager.getRegisteredIdentifier()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }


    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.username.equals("admin", ignoreCase = true) || currentUser?.permSettings == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة التراخيص والاشتراك", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "قفل الصلاحيات",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "صلاحية مقتصرة على المدير",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "عفواً، شاشة إدارة التراخيص والاشتراكات مخصصة لمالك المنشأة ومدير النظام فقط.",
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBack) {
                            Text("الرجوع للشاشة الرئيسية")
                        }
                    }
                }
            }
        } else {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (licenseStatus) {
                        is LicenseStatus.Active -> Color(0xFF1B5E20)
                        is LicenseStatus.FreeTrial -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (licenseStatus) {
                            is LicenseStatus.Active -> Icons.Default.VerifiedUser
                            is LicenseStatus.FreeTrial -> Icons.Default.Timer
                            else -> Icons.Default.Lock
                        },
                        contentDescription = "حالة الترخيص",
                        modifier = Modifier.size(48.dp),
                        tint = when (licenseStatus) {
                            is LicenseStatus.Active -> Color.White
                            is LicenseStatus.FreeTrial -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )

                    Text(
                        text = when (licenseStatus) {
                            is LicenseStatus.Active -> "الاشتراك نشط وموثق للجهاز ✅"
                            is LicenseStatus.FreeTrial -> "نظام آفاق محاسب المعتمد 🔑"
                            is LicenseStatus.LimitReached -> "يتطلب تفعيل الاشتراك للاستمرار 🛑"
                            is LicenseStatus.Expired -> "الاشتراك منتهي أو معطل ⚠️"
                            is LicenseStatus.Error -> (licenseStatus as LicenseStatus.Error).errText
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (licenseStatus) {
                            is LicenseStatus.Active -> Color.White
                            is LicenseStatus.FreeTrial -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        textAlign = TextAlign.Center
                    )

                    when (val status = licenseStatus) {
                        is LicenseStatus.Active -> {
                            Text("اسم العميل: ${status.customerName}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                            Text("تاريخ الانتهاء: ${status.expiryDate}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                        }
                        is LicenseStatus.FreeTrial -> {
                            Text(
                                "تطبيق آفاق محاسب يعمل بنجاح على هذا الجهاز. يمكنك تفعيل وتوثيق اشتراكك الرسمي في أي وقت عبر التواصل مع الدعم الفني.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is LicenseStatus.LimitReached -> {
                            Text(
                                "لقد انتهت فترة السماح لإنشاء العمليات. لحفظ فواتيرك وسنداتك الجديدة، يرجى تفعيل اشتراك النظام.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        else -> {
                            Text(
                                status.message,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Device & Activation Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "بيانات الجهاز وتفعيل الاشتراك",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Device ID Info Box
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("معرّف الجهاز الفريد (Device ID):", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    licenseManager.getDeviceId(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.PhoneAndroid, contentDescription = "جهاز", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Email or Phone Input
                    OutlinedTextField(
                        value = identifierInput,
                        onValueChange = { identifierInput = it },
                        label = { Text("البريد الإلكتروني أو رقم الهاتف المسجل") },
                        placeholder = { Text("مثال: 770000000 أو client@afaq.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = "هاتف") }
                    )



                    // Action Buttons
                    Button(
                        onClick = {
                            if (identifierInput.isBlank()) {
                                statusMessage = "يرجى كتابة البريد أو رقم الهاتف المسجل بسيرفر التراخيص."
                                isSuccessMessage = false
                                return@Button
                            }

                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val (success, msg) = licenseManager.activateSubscription(identifierInput)
                                isLoading = false
                                isSuccessMessage = success
                                statusMessage = msg
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "تفعيل")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تفعيل الاشتراك وتوثيق الجهاز", fontWeight = FontWeight.Bold)
                        }
                    }

                    // WhatsApp Support Contact Button
                    Button(
                        onClick = { showWhatsAppDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color.White)
                            Text("طلب الاشتراك والتفعيل عبر الواتساب 💬", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }

                    // Status Toast/Message Display

                    statusMessage?.let { msg ->
                        Surface(
                            color = if (isSuccessMessage) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSuccessMessage) Color(0xFF2E7D32) else Color(0xFFC62828))
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                fontSize = 12.sp,
                                color = if (isSuccessMessage) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Information & Help Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💡 كيف يعمل نظام التراخيص في آفاق محاسب؟", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "1. يتم ربط الترخيص بمعرف جهازك المحمول منعاً لاستخدام الاشتراك على أجهزة غير مصرحة.\n" +
                        "2. يمكنك استخدام التطبيق أوفلاين بدون إنترنت طوال فترة السماح، ويتم تجديد التراخيص تلقائياً بالخلفية بمجرد توفر الإنترنت.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color.DarkGray
                    )

                }
            }
        }
    }

        // WhatsApp Phone Numbers Selector Dialog
        if (showWhatsAppDialog) {
            val deviceId = licenseManager.getDeviceId()
            AlertDialog(
                onDismissRequest = { showWhatsAppDialog = false },
                title = { Text("اختر رقم الواتساب للتواصل والطلب 💬", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "سيتم فتح المحادثة مباشرة في الواتساب مع رسالة جاهزة تتضمن معرّف جوالك (Device ID) للتفعيل السريع:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // Option 1: 770460003
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWhatsAppDialog = false
                                    openWhatsAppChat(context, "770460003", deviceId)
                                },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("📱 خدمة التراخيص والمبيعات", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("واتساب: 770460003 (967770460003+)", fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = "انتقال", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Option 2: 736802204
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWhatsAppDialog = false
                                    openWhatsAppChat(context, "736802204", deviceId)
                                },
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("💬 الدعم الفني والاشتراكات", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text("واتساب: 736802204 (967736802204+)", fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = "انتقال", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWhatsAppDialog = false }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                }
            )
        }
    }
}

// Helper function to open WhatsApp direct chat with pre-filled message
private fun openWhatsAppChat(context: android.content.Context, phone: String, deviceId: String) {
    val message = "السلام عليكم، أرغب بتفعيل/تجديد ترخيص تطبيق آفاق محاسب للـ Device ID الخاص بي:\n$deviceId"
    val encodedMsg = android.net.Uri.encode(message)
    val formattedWa = com.example.util.PhoneUtils.formatForWhatsApp(phone)
    val url = "https://api.whatsapp.com/send?phone=$formattedWa&text=$encodedMsg"
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse(url)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "تعذر فتح الواتساب تلقائياً", android.widget.Toast.LENGTH_SHORT).show()
    }
}

