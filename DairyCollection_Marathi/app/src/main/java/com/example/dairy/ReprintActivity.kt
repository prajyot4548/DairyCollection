package com.example.dairy

import android.os.Bundle
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged

class ReprintActivity : BtActivity() {
    private lateinit var etDate: EditText
    private lateinit var tvInfo: TextView
    private lateinit var lv: ListView
    private var shown = listOf<Record>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reprint)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_reprint)
        etDate = findViewById(R.id.etDate)
        tvInfo = findViewById(R.id.tvInfo)
        lv = findViewById(R.id.lvEntries)

        etDate.setOnClickListener { Store.pickDate(this, etDate) }
        etDate.doAfterTextChanged { refresh() }
        etDate.setText(Store.today())

        lv.setOnItemClickListener { _, _, pos, _ ->
            val r = shown[pos]
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.reprint_confirm, Fonts.safeName(this, r.name, r.id), r.sessionMr, r.typeName, r.date))
                .setPositiveButton(getString(R.string.print)) { _, _ ->
                    withBt { Printer.printOrChoose(this, Printer.receipt(this, r, reprint = true)) }
                }
                .setNegativeButton(getString(R.string.cancel), null).show()
        }
    }

    private fun refresh() {
        shown = Store.sorted(Store.loadRecords(this).filter { it.date == etDate.text.toString() })
        tvInfo.text = if (shown.isEmpty()) getString(R.string.no_entries_date) else fmt(R.string.reprint_n, shown.size)
        lv.adapter = EntryAdapter(this, shown)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
