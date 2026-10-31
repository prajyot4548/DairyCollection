package com.example.dairy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged

class EntryActivity : BtActivity() {
    private lateinit var spCustomer: Spinner
    private lateinit var etDate: EditText
    private lateinit var rbMorning: RadioButton
    private lateinit var rbCow: RadioButton
    private lateinit var etLiter: EditText
    private lateinit var etFat: EditText
    private lateinit var etSnf: EditText
    private lateinit var etDegree: EditText
    private lateinit var etRate: EditText
    private lateinit var tvTotal: TextView
    private lateinit var tvCount: TextView
    private var customers = listOf<Customer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_entry)

        spCustomer = findViewById(R.id.spCustomer)
        etDate = findViewById(R.id.etDate)
        rbMorning = findViewById(R.id.rbMorning)
        rbCow = findViewById(R.id.rbCow)
        etLiter = findViewById(R.id.etLiter)
        etFat = findViewById(R.id.etFat)
        etSnf = findViewById(R.id.etSnf)
        etDegree = findViewById(R.id.etDegree)
        etRate = findViewById(R.id.etRate)
        tvTotal = findViewById(R.id.tvTotal)
        tvCount = findViewById(R.id.tvCount)

        customers = Store.loadCustomers(this)
        spCustomer.adapter = CustomerAdapter(this, customers)
        if (customers.isEmpty()) toast(getString(R.string.no_customers))

        etDate.setText(Store.today())
        etDate.setOnClickListener { Store.pickDate(this, etDate) }
        etDate.doAfterTextChanged { updateCount() }
        etLiter.doAfterTextChanged { updateTotal() }
        etRate.doAfterTextChanged { updateTotal() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save(false) }
        findViewById<Button>(R.id.btnSavePrint).setOnClickListener { save(true) }
        updateCount()
    }

    private fun num(e: EditText) = e.text.toString().trim().toDoubleOrNull() ?: 0.0

    private fun updateTotal() {
        tvTotal.text = fmt(R.string.total_fmt, num(etLiter) * num(etRate))
    }

    private fun updateCount() {
        val n = Store.loadRecords(this).count { it.date == etDate.text.toString() }
        tvCount.text = fmt(R.string.count_fmt, n)
    }

    private fun save(print: Boolean) {
        if (customers.isEmpty()) return toast(getString(R.string.upload_first))
        if (etLiter.text.isBlank()) return toast(getString(R.string.enter_liter))
        val c = customers[spCustomer.selectedItemPosition]
        val rec = Record(
            etDate.text.toString(), if (rbMorning.isChecked) "Morning" else "Evening",
            c.id, c.name, num(etLiter), num(etFat), num(etSnf), num(etDegree),
            num(etRate), num(etLiter) * num(etRate), if (rbCow.isChecked) "C" else "B"
        )
        val all = Store.loadRecords(this).toMutableList()
        val dup = all.indexOfFirst {
            it.date == rec.date && it.session == rec.session && it.id == rec.id && it.milkType == rec.milkType
        }
        if (dup >= 0) {
            AlertDialog.Builder(this)
                .setMessage(fmt(R.string.dup_msg, Fonts.safeName(this, c.name, c.id), rec.sessionMr, rec.typeName, rec.date))
                .setPositiveButton(getString(R.string.replace)) { _, _ -> all[dup] = rec; commit(all, rec, print) }
                .setNegativeButton(getString(R.string.cancel), null).show()
        } else {
            all.add(rec)
            commit(all, rec, print)
        }
    }

    private fun commit(list: List<Record>, rec: Record, print: Boolean) {
        Store.saveRecords(this, list)
        toast(getString(R.string.saved))
        listOf(etLiter, etFat, etSnf, etDegree).forEach { it.text.clear() }
        updateCount()
        if (print) withBt { Printer.printOrChoose(this, Printer.receipt(this, rec)) }
        askSms(rec)
    }

    // Optional: ask whether to SMS the customer (only when the customer has a mobile number)
    private fun askSms(rec: Record) {
        val cust = customers.firstOrNull { it.id == rec.id }
        val mobile = cust?.mobile ?: ""
        if (mobile.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.send_sms_title))
            .setMessage(fmt(R.string.send_sms_msg, Fonts.safeName(this, rec.name, rec.id), mobile))
            .setPositiveButton(getString(R.string.send_sms_yes)) { _, _ ->
                val msg = Sms.text(this, rec)
                withSms({ smsBlocked(mobile, msg) }) {
                    if (!Sms.send(this, mobile, msg)) smsBlocked(mobile, msg)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    // Android can block SMS permission for apps installed from an APK file
    private fun smsBlocked(mobile: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sms_blocked_title))
            .setMessage(getString(R.string.sms_blocked_msg))
            .setPositiveButton(getString(R.string.open_messages)) { _, _ -> Sms.openComposer(this, mobile, msg) }
            .setNeutralButton(getString(R.string.app_settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
