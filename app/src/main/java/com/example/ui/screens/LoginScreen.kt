package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }


    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val isFirstRunNeeded by viewModel.isFirstRunNeeded.collectAsState()

    // First-run setup states
    var setupStoreName by remember { mutableStateOf("") }
    var setupAdminUser by remember { mutableStateOf("admin") }
    var setupPassword by remember { mutableStateOf("") }
    var setupConfirmPassword by remember { mutableStateOf("") }
    var setupErrorMsg by remember { mutableStateOf<String?>(null) }
    
    // Join Company States
    var showJoinCompanyDialog by remember { mutableStateOf(false) }
    var joinCompanyCode by remember { mutableStateOf("") }
    var joinErrorMsg by remember { mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }

    val loginError by viewModel.loginError.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A2540),
                        Color(0xFF0061A4),
                        Color(0xFF1B3B6F)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1500f, 1500f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isFirstRunNeeded) {
                        // --- FIRST-RUN ONBOARDING SETUP WIZARD ---
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .padding(bottom = 8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.RocketLaunch,
                                    contentDescription = "تثبيت لأول مرة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = "إعداد آفاق محاسب لأول مرة 🚀",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "مرحباً بك! يرجى كتابة اسم المنشأة وتعيين كلمة المرور السرية لمدير النظام لتأمين بياناتك المالية:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 20.dp),
                            textAlign = TextAlign.Center
                        )

                        val modernTextFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )

                        OutlinedTextField(
                            value = setupStoreName,
                            onValueChange = { setupStoreName = it },
                            label = { Text("اسم المنشأة/المحل التجاري *") },
                            placeholder = { Text("مثال: سوبر ماركت البركة") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = modernTextFieldColors,
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = setupAdminUser,
                            onValueChange = { setupAdminUser = it },
                            label = { Text("اسم مستخدم المدير (Admin Username) *") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = modernTextFieldColors,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = setupPassword,
                            onValueChange = { setupPassword = it },
                            label = { Text("كلمة المرور السرية للمدير *") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = modernTextFieldColors,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = setupConfirmPassword,
                            onValueChange = { setupConfirmPassword = it },
                            label = { Text("تأكيد كلمة المرور *") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = modernTextFieldColors,
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                        )

                        if (setupErrorMsg != null) {
                            Text(
                                text = setupErrorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (setupStoreName.trim().isEmpty()) {
                                    setupErrorMsg = "يرجى كتابة اسم المنشأة/المحل التجاري."
                                } else if (setupAdminUser.trim().isEmpty()) {
                                    setupErrorMsg = "يرجى كتابة اسم مستخدم المدير."
                                } else if (setupPassword.trim().isEmpty()) {
                                    setupErrorMsg = "يرجى كتابة كلمة مرور للمدير."
                                } else if (setupPassword.trim() != setupConfirmPassword.trim()) {
                                    setupErrorMsg = "كلمتا المرور غير متطابقتين."
                                } else {
                                    setupErrorMsg = null
                                    viewModel.completeFirstRunSetup(setupStoreName, setupAdminUser, setupPassword) {}
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ وتأمين الحساب والبدء 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "أو",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = { showJoinCompanyDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("الانضمام لمؤسسة موجودة (مزامنة)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }

                    } else {
                        // --- NORMAL LOGIN SCREEN ---
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .padding(bottom = 8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = "آفاق محاسب",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = "آفاق محاسب",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )



                        Text(
                            text = "نظام المحاسبة الشامل وإدارة المخازن والقيود",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp),
                            textAlign = TextAlign.Center
                        )


                    val loginTextFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("اسم المستخدم") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = loginTextFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input")
                            .padding(bottom = 12.dp)
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock", tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "اخفاء" else "اظهار",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = loginTextFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                            .padding(bottom = 12.dp)
                    )

                    // Remember me & Forgot Password Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(text = "تذكر الحساب", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        TextButton(onClick = { showForgotPasswordDialog = true }) {
                            Text(text = "نسيت كلمة المرور؟", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Error Message Banner
                    if (loginError != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = loginError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Login Button
                    Button(
                        onClick = {
                            if (username.trim().isNotEmpty() && password.trim().isNotEmpty()) {
                                viewModel.login(username.trim(), password.trim(), rememberMe)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تسجيل الدخول إلى النظام",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }


            Spacer(modifier = Modifier.height(16.dp))
            Text("إصدار 2.5 • تطوير: شركة آفاق الذكاء للحلول التقنية © 2026", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)

        }

        // Forgot Password Dialog with Master Tech Support Reset
        if (showForgotPasswordDialog) {
            var masterCodeInput by remember { mutableStateOf("") }
            var newAdminPassInput by remember { mutableStateOf("") }
            var resetMsg by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = { Text("استعادة وتصفير كلمة المرور", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "إذا كنت كاشير أو محاسب، يرجى طلب إعادة تعيين كلمة المرور من مدير المحل (Admin) من شاشة الصلاحيات.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        Text("🔑 تصفير حساب المدير عبر الدعم الفني:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("أدخل رمز الدعم الفني الزود بك عبر الواتساب لتغيير كلمة المرور فوراً:", fontSize = 11.sp, color = Color.Gray)

                        OutlinedTextField(
                            value = masterCodeInput,
                            onValueChange = { masterCodeInput = it },
                            label = { Text("رمز الدعم الفني (Tech Code)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newAdminPassInput,
                            onValueChange = { newAdminPassInput = it },
                            label = { Text("كلمة المرور الجديدة المراد اعتمادها") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (resetMsg != null) {
                            Text(
                                text = resetMsg!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (resetMsg!!.contains("بنجاح")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        Text("📱 الواتساب والدعم الفني المعتمد لشركة آفاق الذكاء:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("• المبيعات والترخيص: 770460003 (967770460003+)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• الدعم المحاسبي والتقني: 736802204 (967736802204+)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (masterCodeInput.trim().isNotEmpty()) {
                                viewModel.resetAdminPasswordWithMasterCode(masterCodeInput, newAdminPassInput) { success, msg ->
                                    resetMsg = msg
                                    if (success) {
                                        username = "admin"
                                        password = newAdminPassInput.trim()
                                    }
                                }
                            } else {
                                resetMsg = "يرجى كتابة رمز الدعم الفني."
                            }
                        }
                    ) {
                        Text("تصفيرات وتحديث", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        val currentContext = androidx.compose.ui.platform.LocalContext.current
        var joinSuccessMsg by remember { mutableStateOf<String?>(null) }
        var joinCompanyName by remember { mutableStateOf("") }
        var joinUsername by remember { mutableStateOf("") }
        var joinPassword by remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

        // Join Company Dialog
        if (showJoinCompanyDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (!isJoining) showJoinCompanyDialog = false 
                },
                title = { 
                    Text("الانضمام لمؤسسة قائمة (مزامنة)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp) 
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "أدخل بيانات الحساب التي أنشأها لك مدير المؤسسة للانضمام وتنزيل البيانات سحابياً:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = joinCompanyName,
                            onValueChange = { joinCompanyName = it },
                            label = { Text("اسم المؤسسة (أو اسم الأدمن)") },
                            singleLine = true,
                            enabled = !isJoining,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = joinUsername,
                            onValueChange = { joinUsername = it },
                            label = { Text("اسم المستخدم الخاص بك") },
                            singleLine = true,
                            enabled = !isJoining,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = joinPassword,
                            onValueChange = { joinPassword = it },
                            label = { Text("كلمة المرور") },
                            singleLine = true,
                            enabled = !isJoining,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                        )

                        if (joinErrorMsg != null) {
                            Text(
                                text = joinErrorMsg!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (joinCompanyName.trim().isEmpty() || joinUsername.trim().isEmpty() || joinPassword.trim().isEmpty()) {
                                joinErrorMsg = "يرجى تعبئة جميع الحقول المطلوبة"
                            } else {
                                joinErrorMsg = null
                                isJoining = true
                                coroutineScope.launch {
                                    val syncManager = com.example.util.SupabaseSyncManager(currentContext)
                                    val passHash = com.example.util.sha256(joinPassword.trim())
                                    val (success, message) = syncManager.verifyAndDownloadCompanyData(
                                        joinCompanyName.trim(),
                                        joinUsername.trim(),
                                        joinPassword.trim()
                                    )

                                    isJoining = false
                                    if (success) {
                                        showJoinCompanyDialog = false
                                        joinSuccessMsg = message
                                        // Attempt local login with the synced user
                                        viewModel.login(joinUsername.trim(), joinPassword.trim(), true)
                                    } else {
                                        joinErrorMsg = message
                                    }
                                }
                            }
                        },
                        enabled = !isJoining
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("انضمام وتزامن", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showJoinCompanyDialog = false },
                        enabled = !isJoining
                    ) {
                        Text("إلغاء")
                    }
                }
            )
        }

    }
}

