package com.example.dairy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

class ExportActivity : AppCompatActivity() {
    private lateinit var etFrom: EditText
    private lateinit var etTo: EditText
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_export)
        etFrom = findViewById(R.id.etFrom)
        etTo = findViewById(R.id.etTo)
        tvSummary = findViewById(R.id.tvSummary)

        etFrom.setOnClickListener { Store.pickDate(this, etFrom) }
        etTo.setOnClickListener { Store.pickDate(this, etTo) }
        etFrom.doAfterTextChanged { refresh() }
        etTo.doAfterTextChanged { refresh() }
        etFrom.setText(Store.monthStart())
        etTo.setText(Store.today())

        findViewById<Button>(R.id.btnDownload).setOnClickListener {
            val list = filtered()
            if (list.isEmpty()) return@setOnClickListener toast(getString(R.string.no_data_range))
            try {
                toast(getString(R.string.saved_to, Store.exportToDownloads(this, list)))
            } catch (e: Exception) {
                toast(getString(R.string.export_failed, e.message ?: ""))
            }
        }
    }

    private fun filtered(): List<Record> {
        val f = etFrom.text.toString()
        val t = etTo.text.toString()
        return Store.sorted(Store.loadRecords(this).filter { it.date >= f && it.date <= t })
    }

    private fun refresh() {
        val l = filtered()
        tvSummary.text = fmt(R.string.export_summary, l.size, l.sumOf { it.liter }, l.sumOf { it.total })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
