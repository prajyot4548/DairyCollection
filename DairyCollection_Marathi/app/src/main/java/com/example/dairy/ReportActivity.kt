package com.example.dairy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged

class ReportActivity : BtActivity() {
    private lateinit var etDate: EditText
    private lateinit var tvSummary: TextView
    private lateinit var lv: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_report)
        etDate = findViewById(R.id.etDate)
        tvSummary = findViewById(R.id.tvSummary)
        lv = findViewById(R.id.lvEntries)

        etDate.setOnClickListener { Store.pickDate(this, etDate) }
        etDate.doAfterTextChanged { refresh() }
        etDate.setText(Store.today())

        findViewById<Button>(R.id.btnPrintReport).setOnClickListener {
            val l = entries()
            if (l.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_entries_date), Toast.LENGTH_SHORT).show()
            } else {
                withBt { Printer.printOrChoose(this, Printer.report(this, etDate.text.toString(), l)) }
            }
        }
    }

    private fun entries() = Store.sorted(Store.loadRecords(this).filter { it.date == etDate.text.toString() })

    private fun refresh() {
        val l = entries()
        fun part(label: String, x: List<Record>) =
            fmt(R.string.rep_part, label, x.sumOf { it.liter }, x.sumOf { it.total })
        tvSummary.text = listOf(
            fmt(R.string.rep_entries, l.size),
            part(getString(R.string.rep_total), l),
            part(getString(R.string.morning), l.filter { it.session == "Morning" }),
            part(getString(R.string.evening), l.filter { it.session == "Evening" }),
            part(getString(R.string.cow_c), l.filter { it.milkType == "C" }),
            part(getString(R.string.buffalo_b), l.filter { it.milkType == "B" }),
            fmt(R.string.rep_avg, Store.avg(l) { it.fat }, Store.avg(l) { it.snf })
        ).joinToString("\n")
        lv.adapter = EntryAdapter(this, l)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
