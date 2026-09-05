package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val chatHistory by viewModel.aiChatHistory.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val quickQuestions = listOf(
        "كم أرباح هذا الشهر؟",
        "ما قيمة المخزون الحالي؟",
        "من هو أفضل عميل لدي؟",
        "هل أحتاج إلى شراء أصناف جديدة؟",
        "اعرض ملخص النشاط اليومي المحاسبي"
    )

    // Auto-scroll to bottom of chat when new message arrives
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مساعد آفاق المحاسبي الذكي", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main AI Chat Area



            // Main AI Chat Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(chatHistory) { (msg, isUser) ->
                        ChatBubble(message = msg, isUser = isUser)
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("جاري تحليل الدفاتر المالية وتوليد الإجابة...", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Quick suggested chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 8.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("أسئلة مالية مقترحة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickQuestions.forEach { question ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                viewModel.sendAiMessage(question)
                            }
                        ) {
                            Text(
                                text = question,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Input prompt bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Simulated voice recognition trigger (محادثة صوتية)
                IconButton(
                    onClick = {
                        messageText = "اعرض لي ملخص النشاط اليومي."
                    },
                    modifier = Modifier
                        .size(45.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "تحدث صوتي", tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("اسأل آفاق عن المبيعات أو الأرباح أو السلع...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotEmpty()) {
                            viewModel.sendAiMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier
                        .size(45.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    enabled = messageText.isNotEmpty()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White)
                }
            }
        }
    }

}

@Composable
fun ChatBubble(message: String, isUser: Boolean) {
    val cleanMessage = remember(message) {
        message
            .replace(Regex("\\*+"), "")
            .replace(Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E6}-\\x{1F1FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]"), "")
            .trim()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, contentDescription = "مساعد", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Text(
                text = cleanMessage,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}


@Composable
fun rememberScrollState(): androidx.compose.foundation.ScrollState {
    return androidx.compose.foundation.rememberScrollState()
}
