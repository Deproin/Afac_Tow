package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.model.Currency
import com.example.data.model.EnterpriseSetting
import com.example.ui.viewmodel.AppViewModel
import com.example.util.BackupManager
import com.example.util.GoogleDriveManager
import com.example.util.DriveBackupFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import android.content.Intent
import kotlinx.coroutines.launch
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val currencies by viewModel.currencies.collectAsState()

    var name by remember { mutableStateOf(settings.name) }
    var activity by remember { mutableStateOf(settings.activity) }
    var address by remember { mutableStateOf(settings.address) }
    var phone by remember { mutableStateOf(settings.phone) }
    var whatsapp by remember { mutableStateOf(settings.whatsapp) }
    var taxId by remember { mutableStateOf(settings.taxId) }
    var crId by remember { mutableStateOf(settings.crId) }
    var currencySymbol by remember { mutableStateOf(settings.currency) }
    var footer by remember { mutableStateOf(settings.invoiceFooter) }

    var paperSize by remember { mutableStateOf("80mm") } // "58mm" or "80mm"
    var isWifiPrint by remember { mutableStateOf(true) }

    var backupStatus by remember { mutableStateOf("") }
    var isBackupProcessing by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var showRestoreOptionsDialog by remember { mutableStateOf(false) }
    var showCloudRestoreListDialog by remember { mutableStateOf(false) }
    var cloudBackups by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var cloudBackupAction by remember { mutableStateOf("") } // "UPLOAD" or "DOWNLOAD_LIST"

    val googleDriveManager = remember { GoogleDriveManager(context) }
    var activeContactField by remember { mutableStateOf("") }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numberIndex != -1) {
                            val pickedNumber = it.getString(numberIndex) ?: ""
                            if (activeContactField == "phone") phone = pickedNumber
                            else if (activeContactField == "whatsapp") whatsapp = pickedNumber
                        }
                    }
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isBackupProcessing = true
                val success = BackupManager.exportDatabaseToUri(context, uri)
                isBackupProcessing = false
                if (success) {
                    backupStatus = "✅ تم تصدير وحفظ النسخة الاحتياطية بنجاح في المكان الذي اخترته!"
                    Toast.makeText(context, "✅ تم تصدير النسخة الاحتياطية بنجاح", Toast.LENGTH_LONG).show()
                } else {
                    backupStatus = "❌ حدث خطأ أثناء إنشاء وتصدير النسخة الاحتياطية."
                    Toast.makeText(context, "❌ فشل تصدير الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isBackupProcessing = true
                val success = BackupManager.importDatabaseFromUri(context, uri)
                isBackupProcessing = false
                if (success) {
                    backupStatus = "✅ تم استعادة قاعدة البيانات بنجاح! سيتم إعادة تشغيل التطبيق الآن."
                    Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                    
                    // Restart App
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                } else {
                    backupStatus = "❌ فشل استعادة النسخة الاحتياطية. تأكد من صحة الملف."
                    Toast.makeText(context, "❌ الملف المحدد غير صالح كنسخة احتياطية", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                coroutineScope.launch {
                    val driveService = googleDriveManager.getDriveService(account)
                    if (cloudBackupAction == "UPLOAD") {
                        isBackupProcessing = true
                        backupStatus = "جاري تجهيز النسخة الاحتياطية..."
                        val dbFile = BackupManager.getPreparedDbFile(context)
                        if (dbFile != null) {
                            val result = googleDriveManager.uploadBackup(driveService, dbFile) { progress ->
                                backupStatus = "جاري الرفع إلى Google Drive... $progress%"
                            }
                            if (result.first) {
                                backupStatus = "✅ تم رفع النسخة الاحتياطية إلى Google Drive بنجاح."
                                Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                            } else {
                                backupStatus = "❌ فشل الرفع: ${result.second}"
                            }
                        } else {
                            backupStatus = "❌ فشل الوصول لقاعدة البيانات."
                        }
                        isBackupProcessing = false
                    } else if (cloudBackupAction == "DOWNLOAD_LIST") {
                        isBackupProcessing = true
                        backupStatus = "جاري جلب قائمة النسخ من Google Drive..."
                        cloudBackups = googleDriveManager.listBackups(driveService)
                        isBackupProcessing = false
                        if (cloudBackups.isNotEmpty()) {
                            showCloudRestoreListDialog = true
                            backupStatus = ""
                        } else {
                            backupStatus = "لا توجد أي نسخ احتياطية في مساحة Google Drive الخاصة بك."
                            Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: ApiException) {
            e.printStackTrace()
            backupStatus = "❌ فشل تسجيل الدخول بحساب Google: ${e.statusCode}"
        }
    }

    fun handleGoogleDriveAction(action: String) {
        cloudBackupAction = action
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            // Already signed in with proper scopes, directly execute
            coroutineScope.launch {
                val driveService = googleDriveManager.getDriveService(account)
                if (action == "UPLOAD") {
                    isBackupProcessing = true
                    backupStatus = "جاري تجهيز النسخة الاحتياطية..."
                    val dbFile = BackupManager.getPreparedDbFile(context)
                    if (dbFile != null) {
                        val result = googleDriveManager.uploadBackup(driveService, dbFile) { progress ->
                            backupStatus = "جاري الرفع إلى Google Drive... $progress%"
                        }
                        if (result.first) {
                            backupStatus = "✅ تم رفع النسخة الاحتياطية إلى Google Drive بنجاح."
                            Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                        } else {
                            backupStatus = "❌ فشل الرفع: ${result.second}"
                        }
                    } else {
                        backupStatus = "❌ فشل الوصول لقاعدة البيانات."
                    }
                    isBackupProcessing = false
                } else if (action == "DOWNLOAD_LIST") {
                    isBackupProcessing = true
                    backupStatus = "جاري جلب قائمة النسخ من Google Drive..."
                    cloudBackups = googleDriveManager.listBackups(driveService)
                    isBackupProcessing = false
                    if (cloudBackups.isNotEmpty()) {
                        showCloudRestoreListDialog = true
                        backupStatus = ""
                    } else {
                        backupStatus = "لا توجد أي نسخ احتياطية في مساحة Google Drive الخاصة بك."
                        Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // Not signed in, launch sign in intent
            val intent = googleDriveManager.getSignInClient().signInIntent
            googleSignInLauncher.launch(intent)
        }
    }



    // Multi-currency dialog states
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    var editingCurrency by remember { mutableStateOf<Currency?>(null) }
    var currCodeInput by remember { mutableStateOf("") }
    var currNameInput by remember { mutableStateOf("") }
    var currSymbolInput by remember { mutableStateOf("") }
    var currRateInput by remember { mutableStateOf("1.0") }

    // Personal Account & Security states
    val currentUser by viewModel.currentUser.collectAsState()
    var myUsername by remember(currentUser) { mutableStateOf(currentUser?.username ?: "") }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var accountMsg by remember { mutableStateOf<String?>(null) }



    // Sales & Stock Policy states
    var allowSellBelowCost by remember { mutableStateOf(settings.allowSellBelowCost) }
    var allowNegativeStock by remember { mutableStateOf(settings.allowNegativeStock) }

    // Update form states when settings collect changes
    LaunchedEffect(settings) {
        name = settings.name
        activity = settings.activity
        address = settings.address
        phone = settings.phone
        whatsapp = settings.whatsapp
        taxId = settings.taxId
        crId = settings.crId
        currencySymbol = settings.currency
        footer = settings.invoiceFooter
        allowSellBelowCost = settings.allowSellBelowCost
        allowNegativeStock = settings.allowNegativeStock
    }


    // VAT Validation check (ZATCA standard 15 digits)
    val isVatIdValid = remember(taxId) {
        taxId.length == 15 && taxId.all { it.isDigit() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المؤسسة وتعدد العملات والملف الضريبي", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enterprise profile details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "هوية المنشأة والملف التجاري",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { showPreviewDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("معاينة الفاتورة", fontSize = 11.sp, color = Color.White, maxLines = 1)
                }
            }

            // ─── مظهر التطبيق (الثيم الليلي والنهاري) ───────────────────
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text("مظهر التطبيق (النمط الليلي / النهاري)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (isDarkMode) "النمط الليلي مفعّل (مريح للعينين)" else "النمط النهاري مفعّل",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المنشأة/المحل التجاري *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = activity,
                        onValueChange = { activity = it },
                        label = { Text("النشاط التجاري (مثال: تجارة المواد الغذائية، مقاولات، الخ)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = crId,
                            onValueChange = { crId = it },
                            label = { Text("رقم السجل التجاري CR") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = taxId,
                            onValueChange = { taxId = it },
                            label = { Text("الرقم الضريبي VAT (15 رقم)") },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            isError = taxId.isNotEmpty() && !isVatIdValid
                        )
                    }

                    // VAT Validation Alert
                    if (taxId.isNotEmpty() && !isVatIdValid) {
                        Text(
                            text = "⚠️ تنبيه ضريبي: الرقم الضريبي الرسمي يتكون من 15 رقم مطابق لمعايير هيئة الزكاة والضريبة والجمارك.",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان الرئيسي والفرع") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("الهاتف الأرضي") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            trailingIcon = {
                                IconButton(onClick = {
                                    activeContactField = "phone"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                    contactPickerLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختر من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        OutlinedTextField(
                            value = whatsapp,
                            onValueChange = { whatsapp = it },
                            label = { Text("واتساب المبيعات") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            trailingIcon = {
                                IconButton(onClick = {
                                    activeContactField = "whatsapp"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                    contactPickerLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختر من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text("رمز العملة الرئيسية للنظام") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = footer,
                            onValueChange = { footer = it },
                            label = { Text("ملاحظة أسفل الفواتير") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "سياسات وقيود البيع والمخزون",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("السماح بالبيع بأقل من سعر التكلفة 🏷️", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (allowSellBelowCost) "مسموح: يمكن البيع بأي سعر أقل من التكلفة" else "ممنوع: يتم إيقاف حظر أي فاتورة بسعر أقل من تكلفة الشراء",
                                fontSize = 11.sp,
                                color = if (allowSellBelowCost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = allowSellBelowCost,
                            onCheckedChange = { allowSellBelowCost = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("السماح بالبيع بالكمية السالبة (تجاوز نفاد المخزون) 📦", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (allowNegativeStock) "مسموح: يمكن البيع وتجاوز نفاد الكمية بالمخزن" else "ممنوع: يمنع النظام البيع فور وصول كمية الصنف بالمخزن لـ 0",
                                fontSize = 11.sp,
                                color = if (allowNegativeStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = allowNegativeStock,
                            onCheckedChange = { allowNegativeStock = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                EnterpriseSetting(
                                    name = name.trim(),
                                    activity = activity.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    whatsapp = whatsapp.trim(),
                                    taxId = taxId.trim(),
                                    crId = crId.trim(),
                                    currency = currencySymbol.trim(),
                                    invoiceFooter = footer.trim(),
                                    allowSellBelowCost = allowSellBelowCost,
                                    allowNegativeStock = allowNegativeStock
                                )
                            )
                            Toast.makeText(context, "✅ تم حفظ وتحديث بيانات وسياسات المنشأة بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ التحديثات والملف التجاري والسياسات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                }
            }

            // --- Personal Account & Security Section ---
            Text(
                "إعدادات الحساب الشخصي والأمان",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "يمكنك تحديث اسم المستخدم الخاص بك وتعديل كلمة المرور للوصول الآمن للنظام.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = myUsername,
                        onValueChange = { myUsername = it },
                        label = { Text("اسم المستخدم (Username) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("كلمة المرور الحالية *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("كلمة المرور الجديدة (تأكيد الحفظ)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                    )

                    if (accountMsg != null) {
                        Text(
                            text = accountMsg!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (accountMsg!!.contains("بنجاح")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.changeMyAccountCredentials(oldPass, myUsername, newPass) { success, msg ->
                                accountMsg = msg
                                if (success) {
                                    oldPass = ""
                                    newPass = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تحديث اسم المستخدم وكلمة المرور 🔑", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }


            // --- Partners Management Section ---
            Text(
                "إدارة الشركاء (Partners)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "إضافة وإدارة الشركاء ونسبهم لتوزيع الأرباح والتكاليف بشكل آلي وإدارة حساباتهم الجارية.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.PARTNERS) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إدارة الشركاء ونسب التوزيع 👥", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }


            // --- Multi-Currency Management Section ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "إدارة العملات وأسعار الصرف (Multi-Currency)",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = {
                        currCodeInput = ""
                        currNameInput = ""
                        currSymbolInput = ""
                        currRateInput = "1.0"
                        showAddCurrencyDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة عملة +", fontSize = 11.sp, color = Color.White, maxLines = 1)
                    }
                }
            }


            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "تتيح لك هذه الشاشة إضافة عملات أجنبية ومحلية متعددة، ضبط أسعار الصرف، وتحديد العملة الرئيسية المعتمدة بالحسابات والتقارير.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    for (curr in currencies) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (curr.isDefault) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(curr.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${curr.code})", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        if (curr.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("العملة الرئيسية 👑", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Text("رمز العملة: ${curr.symbol.ifEmpty { curr.code }} | سعر الصرف: ${curr.exchangeRate}", fontSize = 11.sp, color = Color.Gray)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!curr.isDefault) {
                                        TextButton(onClick = { viewModel.setDefaultCurrency(curr) }) {
                                            Text("تعيين كرئيسية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    IconButton(onClick = {
                                        editingCurrency = curr
                                        currCodeInput = curr.code
                                        currNameInput = curr.name
                                        currSymbolInput = curr.symbol
                                        currRateInput = curr.exchangeRate.toString()
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (!curr.isDefault) {
                                        IconButton(onClick = { viewModel.deleteCurrency(curr) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Print configuration details
            Text("إعدادات طابعات الفواتير الحرارية والورق", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مقاس ورق الطباعة المعتمد:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = paperSize == "58mm", onClick = { paperSize = "58mm" }, label = { Text("ورق فواتير 58 مم (كاشير صغير)") })
                        FilterChip(selected = paperSize == "80mm", onClick = { paperSize = "80mm" }, label = { Text("ورق فواتير 80 مم (قياسي)") })
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الطباعة اللاسلكية عبر شبكة Wi-Fi / البلوتوث", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Switch(checked = isWifiPrint, onCheckedChange = { isWifiPrint = it })
                    }
                }
            }

            // Safe Backup and Restore utilities
            Text("إدارة قواعد البيانات والنسخ الاحتياطي", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("احفظ قاعدة بياناتك المحاسبية محلياً بملف مشفر لتتمكن من استعادة بياناتك بأي وقت في حال تلف الجهاز.", fontSize = 12.sp, color = Color.Gray)

                    var isDailyAutoBackup by remember { mutableStateOf(viewModel.isAutoBackupEnabled()) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("النسخ الاحتياطي التلقائي اليومي ⏰", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (isDailyAutoBackup) "مُفعل: يتم إنشاء نسخة احتياطية آلياً كل 24 ساعة في الخلفية وقوقل درايف" else "مُعطل: يتطلب أخذ النسخة الاحتياطية يدوياً",
                                fontSize = 11.sp,
                                color = if (isDailyAutoBackup) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        Switch(
                            checked = isDailyAutoBackup,
                            onCheckedChange = {
                                isDailyAutoBackup = it
                                viewModel.setAutoBackupEnabled(it)
                                Toast.makeText(context, if (it) "✅ تم تفعيل النسخ الاحتياطي التلقائي اليومي" else "تم إيقاف النسخ الاحتياطي التلقائي", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showBackupOptionsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f),
                            enabled = !isBackupProcessing
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = "نسخ", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("أخذ نسخة احتياطية 📤", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRestoreOptionsDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isBackupProcessing
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "استعادة")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة النسخة 📥", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isBackupProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("جاري معالجة وقراءة قاعدة البيانات...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }


                    if (backupStatus.isNotEmpty()) {
                        Text(backupStatus, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Real-Time Synchronization Card (Supabase Cloud)
            val isSupabaseEnabled by viewModel.supabaseSyncManager.isSyncEnabled.collectAsState()
            val supabaseStatusMessage by viewModel.supabaseSyncManager.syncStatusMessage.collectAsState()

            Text("التزامن السحابي اللحظي بين الأجهزة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "تزامن العمليات والفواتير والأصناف والعملاء فورياً عبر السحابة بين أجهزة المتجر أو الفروع المختلفة بدون إعادة تحميل.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مفتاح تشغيل التزامن اللحظي ⚡", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                supabaseStatusMessage,
                                fontSize = 11.sp,
                                color = if (isSupabaseEnabled) Color(0xFF2E7D32) else Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(
                            checked = isSupabaseEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.supabaseSyncManager.isSyncEnabled.value = enabled
                                if (enabled) {
                                    val syncCode = viewModel.licenseManager.getCompanySyncCode()
                                    viewModel.supabaseSyncManager.initialize(
                                        url = com.example.BuildConfig.SUPABASE_URL,
                                        key = com.example.BuildConfig.SUPABASE_ANON_KEY,
                                        companyId = if (syncCode.isNotEmpty()) syncCode else "company-default"
                                    )
                                    Toast.makeText(context, "تم تفعيل التزامن السحابي اللحظي 🟢", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.supabaseSyncManager.syncStatusMessage.value = "التزامن موقوف 🔴"
                                    Toast.makeText(context, "تم إيقاف التزامن اللحظي 🔴", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Gemini AI Configuration Card
            Text("مساعد آفاق الذكي (Gemini 3.5 Flash)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "ذكاء اصطناعي", tint = MaterialTheme.colorScheme.primary)
                        Text("المحرك المحاسبي مفعّل وجاهز تلقائياً ⚡", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "يقوم المساعد الذكي المدمج بتحليل مبيعاتك ومخزونك وأرباحك فوراً ومباشرة دون الحاجة لإدخال أي مفاتيح أو إعدادات معقدة.",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }

        }

        // Add New Currency Dialog
        if (showAddCurrencyDialog) {
            AlertDialog(
                onDismissRequest = { showAddCurrencyDialog = false },
                title = { Text("إضافة عملة جديدة بالنظام", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currNameInput,
                            onValueChange = { currNameInput = it },
                            label = { Text("اسم العملة (مثال: ريال يمني، دولار أمريكي)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = currCodeInput,
                                onValueChange = { currCodeInput = it.uppercase() },
                                label = { Text("الكود (YER, USD, SAR)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currSymbolInput,
                                onValueChange = { currSymbolInput = it },
                                label = { Text("الرمز (ر.ي، $)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = currRateInput,
                            onValueChange = { currRateInput = it },
                            label = { Text("سعر الصرف مقابل العملة الرئيسية") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (currCodeInput.isNotEmpty() && currNameInput.isNotEmpty()) {
                                val rate = currRateInput.toDoubleOrNull() ?: 1.0
                                viewModel.addCurrency(
                                    Currency(
                                        code = currCodeInput.trim(),
                                        name = currNameInput.trim(),
                                        symbol = currSymbolInput.trim(),
                                        exchangeRate = rate,
                                        isDefault = currencies.isEmpty()
                                    )
                                )
                                showAddCurrencyDialog = false
                            }
                        }
                    ) {
                        Text("حفظ العملة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCurrencyDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // Edit Currency Dialog
        if (editingCurrency != null) {
            val curr = editingCurrency!!
            AlertDialog(
                onDismissRequest = { editingCurrency = null },
                title = { Text("تعديل بيانات العملة: ${curr.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currNameInput,
                            onValueChange = { currNameInput = it },
                            label = { Text("اسم العملة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = currCodeInput,
                                onValueChange = { currCodeInput = it.uppercase() },
                                label = { Text("كود العملة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currSymbolInput,
                                onValueChange = { currSymbolInput = it },
                                label = { Text("رمز العملة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = currRateInput,
                            onValueChange = { currRateInput = it },
                            label = { Text("سعر الصرف") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (currCodeInput.isNotEmpty() && currNameInput.isNotEmpty()) {
                                val rate = currRateInput.toDoubleOrNull() ?: curr.exchangeRate
                                viewModel.updateCurrency(
                                    curr.copy(
                                        code = currCodeInput.trim(),
                                        name = currNameInput.trim(),
                                        symbol = currSymbolInput.trim(),
                                        exchangeRate = rate
                                    )
                                )
                                editingCurrency = null
                            }
                        }
                    ) {
                        Text("تحديث العملة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingCurrency = null }) { Text("إلغاء") }
                }
            )
        }

        // Invoice Receipt Live Preview Dialog
        if (showPreviewDialog) {
            AlertDialog(
                onDismissRequest = { showPreviewDialog = false },
                title = { Text("معاينة ترويسة الفاتورة المطبوعة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(name.ifEmpty { "اسم المؤسسة" }, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                            Text("${activity.ifEmpty { "النشاط التجاري" }} | ${address.ifEmpty { "العنوان" }}", fontSize = 12.sp, color = Color.Gray)
                            Text("الرقم الضريبي: ${taxId.ifEmpty { "300000000000003" }} | السجل: ${crId.ifEmpty { "1010000000" }}", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            Divider(modifier = Modifier.padding(vertical = 6.dp))

                            Text("فاتورة مبيعات ضريبية مبسطة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("رقم الفاتورة: INV-10029384", fontSize = 11.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("صنف عينة تجريبي x 1", fontSize = 12.sp)
                                Text("100.00 $currencySymbol", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Divider(modifier = Modifier.padding(vertical = 6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الإجمالي النهائي (شامل 15% ضريبة):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("115.00 $currencySymbol", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(footer.ifEmpty { "شكراً لتعاملكم معنا!" }, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showPreviewDialog = false }) { Text("إغلاق المعاينة", color = Color.White) }
                }
            )
        }

        // Backup Options Dialog
        if (showBackupOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showBackupOptionsDialog = false },
                title = { Text("خيارات الحفظ الاحتياطي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = { Text("أين تريد حفظ النسخة الاحتياطية؟") },
                confirmButton = {
                    Button(onClick = {
                        showBackupOptionsDialog = false
                        createDocumentLauncher.launch(BackupManager.generateBackupFileName())
                    }) { Text("في الجهاز (محلي)", color = Color.White) }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showBackupOptionsDialog = false
                            handleGoogleDriveAction("UPLOAD")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) { Text("في Google Drive", color = Color.White) }
                }
            )
        }

        // Restore Options Dialog
        if (showRestoreOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreOptionsDialog = false },
                title = { Text("خيارات الاستعادة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = { Text("من أين تريد استعادة النسخة الاحتياطية؟\nتحذير: سيتم استبدال بياناتك الحالية.") },
                confirmButton = {
                    Button(onClick = {
                        showRestoreOptionsDialog = false
                        openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
                    }) { Text("من الجهاز (محلي)", color = Color.White) }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showRestoreOptionsDialog = false
                            handleGoogleDriveAction("DOWNLOAD_LIST")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) { Text("من Google Drive", color = Color.White) }
                }
            )
        }

        // Cloud Restore List Dialog
        if (showCloudRestoreListDialog) {
            AlertDialog(
                onDismissRequest = { showCloudRestoreListDialog = false },
                title = { Text("نسخ Google Drive", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                        if (cloudBackups.isEmpty()) {
                            Text("لا توجد نسخ احتياطية.", modifier = Modifier.padding(16.dp))
                        } else {
                            cloudBackups.forEach { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    onClick = {
                                        showCloudRestoreListDialog = false
                                        coroutineScope.launch {
                                            isBackupProcessing = true
                                            backupStatus = "جاري التنزيل والاستعادة..."
                                            val account = GoogleSignIn.getLastSignedInAccount(context)
                                            if (account != null) {
                                                val driveService = googleDriveManager.getDriveService(account)
                                                val tempFile = java.io.File(context.cacheDir, "temp_restore.db")
                                                val downloadSuccess = googleDriveManager.downloadBackup(driveService, file.id, tempFile) { progress ->
                                                    backupStatus = "جاري التنزيل... $progress%"
                                                }
                                                if (downloadSuccess) {
                                                    val restoreSuccess = BackupManager.importDatabaseFromFile(context, tempFile)
                                                    if (restoreSuccess) {
                                                        backupStatus = "✅ تم استعادة البيانات بنجاح من سحابة درايف! سيتم إعادة تشغيل التطبيق الآن."
                                                        Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
                                                        
                                                        // Restart App
                                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                                        if (intent != null) {
                                                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                            context.startActivity(intent)
                                                            Runtime.getRuntime().exit(0)
                                                        }
                                                    } else {
                                                        backupStatus = "❌ فشل دمج قاعدة البيانات."
                                                    }
                                                } else {
                                                    backupStatus = "❌ فشل تنزيل النسخة من السحابة."
                                                }
                                            }
                                            isBackupProcessing = false
                                        }
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(file.createdTime))
                                        val sizeKb = file.size / 1024
                                        Text("التاريخ: $dateStr | الحجم: $sizeKb KB", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCloudRestoreListDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}
