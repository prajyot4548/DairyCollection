package com.example.dairy

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.annotation.StringRes
import android.widget.EditText
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Marathi (India) locale with western digits, used for dates and numbers shown on screen
val MR: Locale = Locale.forLanguageTag("mr-IN-u-nu-latn")

// getString + String.format with US number formatting (keeps 0-9 digits)
fun Context.fmt(@StringRes id: Int, vararg a: Any): String = String.format(Locale.US, getString(id), *a)

data class Customer(val name: String, val id: String, val mobile: String = "")

data class Record(
    val date: String, val session: String, val id: String, val name: String,
    val liter: Double, val fat: Double, val snf: Double, val degree: Double,
    val rate: Double, val total: Double, val milkType: String = "C"
) {
    // internal values stay "Morning"/"Evening" and "C"/"B" (data file format); these are for display in Marathi
    val typeName: String get() = if (milkType == "B") "म्हैस" else "गाय"
    val sessionMr: String get() = if (session == "Morning") "सकाळ" else "संध्याकाळ"
    val shiftMr: String get() = if (session == "Morning") "स" else "सं"
    val typeShortMr: String get() = if (milkType == "B") "म्ह" else "गा"
    val shift: String get() = if (session == "Morning") "M" else "E"

    // internal storage line (tab separated, so commas inside Marathi names are safe)
    fun toLine() = listOf(
        date, session, id, name, liter, fat, snf, degree, rate,
        String.format(Locale.US, "%.2f", total), milkType
    ).joinToString("\t")

    // export line: Name is the LAST column so commas inside the name never break other columns
    fun exportLine() = listOf(
        date, shift, milkType, id, liter, fat, snf, degree, rate,
        String.format(Locale.US, "%.2f", total), name
    ).joinToString(",")
}

object Store {
    const val SHOP_NAME = "दुग्ध संकलन केंद्र"   // printed on the receipt and SMS (change to your dairy name)
    const val EXPORT_HEADER = "Date,Shift(M/E),Type(C/B),CustomerId,Liter,Fat,SNF,Degree,Rate,Amount,Name"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = dateFmt.format(Date())
    fun monthStart(): String {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        return dateFmt.format(c.time)
    }

    // ---------- login flag ----------
    private fun prefs(c: Context) = c.getSharedPreferences("dairy", Context.MODE_PRIVATE)
    fun isLoggedIn(c: Context) = prefs(c).getBoolean("in", false)
    fun setLoggedIn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("in", v).apply()

    // ---------- customers ----------
    private fun customersFile(c: Context) = File(c.filesDir, "customers.txt")

    private fun String.trimAscii() = this.trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' }

    private fun isMobile(s: String) =
        s.all { it.isDigit() || it == '+' || it == '-' || it == ' ' } && s.count { it.isDigit() } >= 10

    // Line formats: Name<TAB>Id<TAB>Mobile   or   Name,Id,Mobile   (Mobile optional).
    // Id and Mobile are read from the right, so a comma inside a Marathi name is fine.
    private fun parseCustomerLine(raw: String): Customer? {
        val line = raw.trimAscii()
        if (line.isEmpty()) return null
        val tab = line.contains('\t')
        val parts = (if (tab) line.split('\t') else line.split(',')).map { it.trimAscii() }.dropLastWhile { it.isEmpty() }
        if (parts.size < 2) return null
        var end = parts.size
        var mobile = ""
        if (end >= 3 && isMobile(parts[end - 1])) {
            mobile = parts[end - 1].filter { it.isDigit() || it == '+' }
            end--
        }
        val id = parts[end - 1]
        val name = parts.subList(0, end - 1).joinToString(if (tab) "\t" else ",")
        if (name.isEmpty() || id.isEmpty()) return null
        return Customer(name, id, mobile)
    }

    fun parseCustomers(lines: List<String>): List<Customer> = lines.mapNotNull { parseCustomerLine(it) }

    fun saveCustomers(c: Context, list: List<Customer>) {
        customersFile(c).writeText(list.joinToString("\n") { "${it.name}\t${it.id}\t${it.mobile}" })
    }

    fun loadCustomers(c: Context): List<Customer> =
        if (customersFile(c).exists()) parseCustomers(customersFile(c).readLines()) else emptyList()

    fun clearCustomers(c: Context) { customersFile(c).delete() }

    // ---------- records ----------
    private fun recordsFile(c: Context) = File(c.filesDir, "collection.txt")

    private fun parseRecord(line: String): Record? {
        if (line.isBlank()) return null
        val p = if (line.contains('\t')) line.split("\t") else line.split(",")
        if (p.size < 10) return null
        fun d(i: Int) = p[i].toDoubleOrNull() ?: 0.0
        val type = p.getOrNull(10)?.trim().orEmpty().ifEmpty { "C" }
        return Record(p[0], p[1], p[2], p[3], d(4), d(5), d(6), d(7), d(8), d(9), type)
    }

    fun loadRecords(c: Context): List<Record> =
        if (recordsFile(c).exists()) recordsFile(c).readLines().mapNotNull { parseRecord(it) } else emptyList()

    fun saveRecords(c: Context, list: List<Record>) {
        recordsFile(c).writeText(list.joinToString("\n") { it.toLine() } + if (list.isEmpty()) "" else "\n")
    }

    fun sorted(list: List<Record>) = list.sortedWith(
        compareBy({ it.date }, { it.session != "Morning" }, { it.id.toLongOrNull() ?: Long.MAX_VALUE }, { it.id }, { it.milkType })
    )

    fun avg(list: List<Record>, sel: (Record) -> Double): Double {
        val t = list.sumOf { it.liter }
        return if (t == 0.0) 0.0 else list.sumOf { sel(it) * it.liter } / t
    }

    // ---------- export to Downloads ----------
    fun exportToDownloads(c: Context, list: List<Record>): String {
        val name = "dairy_collection_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".txt"
        val content = (listOf(EXPORT_HEADER) + sorted(list).map { it.exportLine() }).joinToString("\r\n")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }
        val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        // same 8-bit encoding as the KF-Kiran import file, so the font shows the names correctly
        c.contentResolver.openOutputStream(uri)!!.use { it.write(Legacy.encode(content)) }
        return name
    }

    // ---------- date picker ----------
    fun pickDate(c: Context, et: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(c, { _, y, m, d ->
            et.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
}
