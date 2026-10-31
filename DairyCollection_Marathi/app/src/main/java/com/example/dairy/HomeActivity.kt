package com.example.dairy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BtActivity() {
    private lateinit var tvEntries: TextView
    private lateinit var tvLiters: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvInfoLine: TextView
    private lateinit var tvValidity: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        tvEntries = findViewById(R.id.tvEntries)
        tvLiters = findViewById(R.id.tvLiters)
        tvAmount = findViewById(R.id.tvAmount)
        tvInfoLine = findViewById(R.id.tvInfoLine)
        tvValidity = findViewById(R.id.tvValidity)
        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("EEEE, dd MMM yyyy", MR).format(Date())

        tile(R.id.tileUpload, "📤", getString(R.string.tile_upload_t), getString(R.string.tile_upload_s)) { go(UploadActivity::class.java) }
        tile(R.id.tileEntry, "🥛", getString(R.string.tile_entry_t), getString(R.string.tile_entry_s)) { go(EntryActivity::class.java) }
        tile(R.id.tileExport, "📁", getString(R.string.tile_export_t), getString(R.string.tile_export_s)) { go(ExportActivity::class.java) }
        tile(R.id.tileReport, "📊", getString(R.string.tile_report_t), getString(R.string.tile_report_s)) { go(ReportActivity::class.java) }
        tile(R.id.tileReprint, "🧾", getString(R.string.tile_reprint_t), getString(R.string.tile_reprint_s)) { go(ReprintActivity::class.java) }
        tile(R.id.tilePrinter, "🖨️", getString(R.string.tile_printer_t), getString(R.string.tile_printer_s)) {
            withBt { Printer.choose(this) { refresh() } }
        }

        findViewById<View>(R.id.btnLogout).setOnClickListener { logout() }
    }

    private fun tile(id: Int, icon: String, title: String, sub: String, onClick: () -> Unit) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.tvIcon).text = icon
        v.findViewById<TextView>(R.id.tvTitle).text = title
        v.findViewById<TextView>(R.id.tvSub).text = sub
        v.setOnClickListener { onClick() }
    }

    private fun logout() {
        Store.setLoggedIn(this, false)
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    override fun onResume() {
        super.onResume()
        if (Auth.isRegistered(this) && Auth.isExpired(this)) { // 1 year over: OTP renewal needed
            logout()
            return
        }
        refresh()
    }

    private fun refresh() {
        val today = Store.loadRecords(this).filter { it.date == Store.today() }
        tvEntries.text = today.size.toString()
        tvLiters.text = String.format(Locale.US, "%.1f", today.sumOf { it.liter })
        tvAmount.text = String.format(Locale.US, "%.2f", today.sumOf { it.total })
        tvInfoLine.text = fmt(R.string.home_info, Store.loadCustomers(this).size, Printer.savedName(this) ?: getString(R.string.printer_none))
        val left = Auth.daysLeft(this)
        tvValidity.text = fmt(R.string.validity_fmt, Auth.fullName(this), Auth.expiryText(this), left)
    }

    private fun go(c: Class<*>) = startActivity(Intent(this, c))
}
