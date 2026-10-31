package com.example.dairy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        val etName = findViewById<EditText>(R.id.etFullName)
        val etCode = findViewById<EditText>(R.id.etUserCode)
        val etPass = findViewById<EditText>(R.id.etRegPass)
        val etMobile = findViewById<EditText>(R.id.etRegMobile)

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val name = etName.text.toString().trim()
            val code = etCode.text.toString().trim()
            val pass = etPass.text.toString()
            val mobile = etMobile.text.toString().trim()
            var ok = true
            if (name.isEmpty()) { etName.error = getString(R.string.err_name); ok = false }
            if (!Regex("^[A-Za-z0-9_]{3,20}$").matches(code)) { etCode.error = getString(R.string.err_code); ok = false }
            if (pass.length < 4) { etPass.error = getString(R.string.err_pass); ok = false }
            if (!Regex("^[6-9][0-9]{9}$").matches(mobile)) { etMobile.error = getString(R.string.err_mobile); ok = false }
            if (ok) {
                Auth.savePending(this, name, code, pass, mobile)
                startActivity(Intent(this, OtpActivity::class.java))
            }
        }
    }
}
