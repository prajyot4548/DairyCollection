package com.example.dairy

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class UploadActivity : AppCompatActivity() {
    private lateinit var tvInfo: TextView
    private lateinit var lv: ListView

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_upload)
        tvInfo = findViewById(R.id.tvInfo)
        lv = findViewById(R.id.lvCustomers)

        findViewById<TextView>(R.id.tvFontStatus).text =
            getString(if (Fonts.available(this)) R.string.font_found else R.string.font_missing)
        val cb = findViewById<CheckBox>(R.id.cbKiran)
        cb.isChecked = Fonts.kiranMode(this)
        cb.setOnCheckedChangeListener { _, v ->
            Fonts.setKiranMode(this, v)
            refresh()
        }

        findViewById<Button>(R.id.btnPick).setOnClickListener { pickFile.launch(arrayOf("text/plain", "text/*")) }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_cust_msg))
                .setPositiveButton(getString(R.string.delete)) { _, _ -> Store.clearCustomers(this); refresh() }
                .setNegativeButton(getString(R.string.cancel), null).show()
        }
        refresh()
    }

    private fun importFile(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val list = Store.parseCustomers(Legacy.decode(bytes).lines())
            if (list.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_valid_lines), Toast.LENGTH_LONG).show()
                return
            }
            Store.saveCustomers(this, list)
            refresh()
            Toast.makeText(this, fmt(R.string.n_uploaded, list.size), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.read_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun refresh() {
        val list = Store.loadCustomers(this)
        tvInfo.text = if (list.isEmpty()) getString(R.string.no_cust_yet) else fmt(R.string.n_loaded, list.size)
        lv.adapter = CustomerAdapter(this, list)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
