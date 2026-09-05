package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Contact
import com.example.data.model.Item
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object ExcelImportExportUtil {

    // UTF-8 BOM byte array to ensure Excel opens Arabic text correctly
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun exportItemsToCsv(context: Context, items: List<Item>): Uri {
        val fileName = "items_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(UTF8_BOM)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            // Header
            writer.write("الكود,الباركود,الاسم,الفئة,الوحدة,الماركة,اللون,الحجم,الموقع,الحد الأدنى,الحد الأقصى,سعر الشراء,سعر البيع,سعر الجملة,سعر خاص,الكمية الحالية,الملاحظات\n")
            items.forEach { item ->
                val line = listOf(
                    escapeCsv(item.code),
                    escapeCsv(item.barcode),
                    escapeCsv(item.name),
                    escapeCsv(item.category),
                    escapeCsv(item.unit),
                    escapeCsv(item.brand),
                    escapeCsv(item.color),
                    escapeCsv(item.size),
                    escapeCsv(item.location),
                    item.minLimit.toString(),
                    item.maxLimit.toString(),
                    item.purchasePrice.toString(),
                    item.salePrice.toString(),
                    item.wholesalePrice.toString(),
                    item.specialPrice.toString(),
                    item.currentQuantity.toString(),
                    escapeCsv(item.notes)
                ).joinToString(",")
                writer.write(line + "\n")
            }
            writer.flush()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportContactsToCsv(context: Context, contacts: List<Contact>, typeFilter: String? = null): Uri {
        val filtered = if (typeFilter != null) contacts.filter { it.type == typeFilter } else contacts
        val prefix = when (typeFilter) {
            "CUSTOMER" -> "customers"
            "SUPPLIER" -> "suppliers"
            else -> "contacts"
        }
        val fileName = "${prefix}_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(UTF8_BOM)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            // Header
            writer.write("النوع,الاسم,الهاتف,العنوان,الرصيد,حد الائتمان,الملاحظات\n")
            filtered.forEach { c ->
                val line = listOf(
                    escapeCsv(c.type),
                    escapeCsv(c.name),
                    escapeCsv(c.phone),
                    escapeCsv(c.address),
                    c.balance.toString(),
                    c.creditLimit.toString(),
                    escapeCsv(c.notes)
                ).joinToString(",")
                writer.write(line + "\n")
            }
            writer.flush()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun generateItemsTemplateUri(context: Context): Uri {
        val fileName = "نموذج_استيراد_الأصناف.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(UTF8_BOM)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            writer.write("الكود,الباركود,الاسم,الفئة,الوحدة,الماركة,اللون,الحجم,الموقع,الحد الأدنى,الحد الأقصى,سعر الشراء,سعر البيع,سعر الجملة,سعر خاص,الكمية الحالية,الملاحظات\n")
            writer.write("ITEM001,123456789,قهوة بن يمني,مواد غذائية,حبة,ماركة الفخامة,بني,كبير,مستودع 1,5,100,5000,7000,6500,6000,50,مثال صنف 1\n")
            writer.write("ITEM002,987654321,شاي أحمر فاخر,مواد غذائية,علبة,ماركة الرواد,أحمر,وسط,مستودع 1,10,200,1200,1800,1600,1500,30,مثال صنف 2\n")
            writer.flush()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun generateContactsTemplateUri(context: Context, defaultType: String = "CUSTOMER"): Uri {
        val fileName = "نموذج_استيراد_جهات_الاتصال.csv"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(UTF8_BOM)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            writer.write("النوع,الاسم,الهاتف,العنوان,الرصيد,حد الائتمان,الملاحظات\n")
            if (defaultType == "CUSTOMER") {
                writer.write("CUSTOMER,علي محمد أحمد,777123456,صنعاء - شارع الستين,0,50000,عميل تجزئة\n")
                writer.write("CUSTOMER,مؤسسة النور التجاري,733987654,عدن - المعلا,10000,100000,عميل جملة\n")
            } else {
                writer.write("SUPPLIER,شركة الأمل للتجارة,777000111,صنعاء - التحرير,0,0,مورد رئيسي\n")
                writer.write("SUPPLIER,مؤسسة الخليج للمستوردات,733222333,الحديدة,5000,0,مورد مواد خامات\n")
            }
            writer.flush()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun parseItemsFromCsv(context: Context, uri: Uri): List<Item> {
        val items = mutableListOf<Item>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var line: String? = reader.readLine()
            if (line != null && line.startsWith("\uFEFF")) {
                line = line.substring(1)
            }
            var isFirstLine = true
            while (line != null) {
                if (isFirstLine) {
                    isFirstLine = false
                    line = reader.readLine()
                    continue
                }
                if (line.trim().isNotEmpty()) {
                    val tokens = parseCsvLine(line)
                    if (tokens.size >= 3 && tokens[2].trim().isNotEmpty()) {
                        val item = Item(
                            code = tokens.getOrNull(0)?.trim() ?: "",
                            barcode = tokens.getOrNull(1)?.trim() ?: "",
                            name = tokens.getOrNull(2)?.trim() ?: "صنف مستورد",
                            category = tokens.getOrNull(3)?.trim() ?: "",
                            unit = tokens.getOrNull(4)?.trim().let { if (it.isNullOrEmpty()) "حبة" else it },
                            brand = tokens.getOrNull(5)?.trim() ?: "",
                            color = tokens.getOrNull(6)?.trim() ?: "",
                            size = tokens.getOrNull(7)?.trim() ?: "",
                            location = tokens.getOrNull(8)?.trim() ?: "",
                            minLimit = tokens.getOrNull(9)?.toDoubleOrNull() ?: 0.0,
                            maxLimit = tokens.getOrNull(10)?.toDoubleOrNull() ?: 9999.0,
                            purchasePrice = tokens.getOrNull(11)?.toDoubleOrNull() ?: 0.0,
                            salePrice = tokens.getOrNull(12)?.toDoubleOrNull() ?: 0.0,
                            wholesalePrice = tokens.getOrNull(13)?.toDoubleOrNull() ?: 0.0,
                            specialPrice = tokens.getOrNull(14)?.toDoubleOrNull() ?: 0.0,
                            currentQuantity = tokens.getOrNull(15)?.toDoubleOrNull() ?: 0.0,
                            notes = tokens.getOrNull(16)?.trim() ?: ""
                        )
                        items.add(item)
                    }
                }
                line = reader.readLine()
            }
        }
        return items
    }

    fun parseContactsFromCsv(context: Context, uri: Uri, fallbackType: String = "CUSTOMER"): List<Contact> {
        val contacts = mutableListOf<Contact>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var line: String? = reader.readLine()
            if (line != null && line.startsWith("\uFEFF")) {
                line = line.substring(1)
            }
            var isFirstLine = true
            while (line != null) {
                if (isFirstLine) {
                    isFirstLine = false
                    line = reader.readLine()
                    continue
                }
                if (line.trim().isNotEmpty()) {
                    val tokens = parseCsvLine(line)
                    val rawType = tokens.getOrNull(0)?.trim()?.uppercase() ?: ""
                    val type = when {
                        rawType == "CUSTOMER" || rawType == "عميل" || rawType.contains("عميل") -> "CUSTOMER"
                        rawType == "SUPPLIER" || rawType == "مورد" || rawType.contains("مورد") -> "SUPPLIER"
                        else -> fallbackType
                    }
                    val name = tokens.getOrNull(1)?.trim() ?: ""
                    if (name.isNotEmpty()) {
                        val contact = Contact(
                            type = type,
                            name = name,
                            phone = tokens.getOrNull(2)?.trim() ?: "",
                            address = tokens.getOrNull(3)?.trim() ?: "",
                            balance = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                            creditLimit = tokens.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
                            notes = tokens.getOrNull(6)?.trim() ?: ""
                        )
                        contacts.add(contact)
                    }
                }
                line = reader.readLine()
            }
        }
        return contacts
    }

    private fun escapeCsv(text: String): String {
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\""
        }
        return text
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    fun shareFile(context: Context, uri: Uri, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
