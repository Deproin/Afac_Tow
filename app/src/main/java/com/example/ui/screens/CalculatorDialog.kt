package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var expression by remember { mutableStateOf("") }
    var resultDisplay by remember { mutableStateOf("0") }

    fun calculateResult(expStr: String): String {
        if (expStr.isEmpty()) return "0"
        try {
            var cleanExp = expStr.replace("×", "*").replace("÷", "/")
            cleanExp = cleanExp.replace("%", "/100.0")

            val evalResult = evaluateSimpleExpression(cleanExp)
            return if (evalResult % 1.0 == 0.0) {
                evalResult.toLong().toString()
            } else {
                String.format("%.4f", evalResult).trimEnd('0').trimEnd('.')
            }
        } catch (e: Exception) {
            return "خطأ"
        }
    }

    fun onKeyClick(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                resultDisplay = "0"
            }
            "⌫" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    resultDisplay = calculateResult(expression)
                }
            }
            "=" -> {
                val res = calculateResult(expression)
                if (res != "خطأ") {
                    resultDisplay = res
                    expression = res
                }
            }
            "%" -> {
                if (expression.isNotEmpty() && !expression.last().isWhitespace() && expression.last() != '%') {
                    expression += "%"
                    resultDisplay = calculateResult(expression)
                }
            }
            "+", "-", "×", "÷" -> {
                if (expression.isNotEmpty()) {
                    val lastChar = expression.last()
                    if (lastChar == '+' || lastChar == '-' || lastChar == '×' || lastChar == '÷') {
                        expression = expression.dropLast(1) + key
                    } else {
                        expression += key
                    }
                } else if (key == "-") {
                    expression = "-"
                }
            }
            else -> {
                if (expression == "0" && key != ".") {
                    expression = key
                } else {
                    expression += key
                }
                resultDisplay = calculateResult(expression)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                            Icons.Default.Calculate,
                            contentDescription = "آلة حاسبة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "الآلة الحاسبة الذكية 🧮",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display Screen
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (expression.isEmpty()) "0" else expression,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )

                        Text(
                            text = resultDisplay,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Keypad Buttons Grid
                val buttons = listOf(
                    listOf("C", "⌫", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "-"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", "00", ".", "=")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                CalculatorButton(
                                    label = key,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKeyClick(key) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("CalcResult", resultDisplay)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "📋 تم نسخ النتيجة ($resultDisplay)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ النتيجة", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تم", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isOperator = label in listOf("+", "-", "×", "÷", "%")
    val isAction = label in listOf("C", "⌫")
    val isEquals = label == "="

    val containerColor = when {
        isEquals -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        isAction -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isEquals -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isAction -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (label == "⌫") {
            Icon(Icons.Default.Backspace, contentDescription = "تراجع", modifier = Modifier.size(20.dp))
        } else {
            Text(
                text = label,
                fontSize = if (isOperator || isEquals) 20.sp else 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Simple Parser for basic math expressions (+, -, *, /)
private fun evaluateSimpleExpression(expression: String): Double {
    if (expression.isBlank()) return 0.0
    
    val tokens = mutableListOf<String>()
    var numberBuffer = StringBuilder()

    for (i in expression.indices) {
        val c = expression[i]
        if (c.isDigit() || c == '.') {
            numberBuffer.append(c)
        } else if (c == '+' || c == '-' || c == '*' || c == '/') {
            if (numberBuffer.isNotEmpty()) {
                tokens.add(numberBuffer.toString())
                numberBuffer = StringBuilder()
            } else if (c == '-' && (tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/"))) {
                numberBuffer.append(c)
                continue
            }
            tokens.add(c.toString())
        }
    }
    if (numberBuffer.isNotEmpty()) {
        tokens.add(numberBuffer.toString())
    }

    if (tokens.isEmpty()) return 0.0

    // Step 1: Multiply and Divide
    var i = 0
    while (i < tokens.size) {
        if (tokens[i] == "*" || tokens[i] == "/") {
            val left = tokens[i - 1].toDoubleOrNull() ?: 0.0
            val right = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: 1.0
            val res = if (tokens[i] == "*") left * right else if (right != 0.0) left / right else 0.0
            
            tokens[i - 1] = res.toString()
            tokens.removeAt(i)
            if (i < tokens.size) tokens.removeAt(i)
            i--
        } else {
            i++
        }
    }

    // Step 2: Add and Subtract
    var result = tokens.firstOrNull()?.toDoubleOrNull() ?: 0.0
    i = 1
    while (i < tokens.size) {
        val op = tokens[i]
        val right = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0
        if (op == "+") result += right
        if (op == "-") result -= right
        i += 2
    }

    return result
}
