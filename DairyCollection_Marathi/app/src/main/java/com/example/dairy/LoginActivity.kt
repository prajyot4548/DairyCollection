package com.example.dairy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Auth.isRegistered(this)) { // first install: register with OTP
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
            return
        }
        val expired = Auth.isExpired(this)
        if (!expired && Store.isLoggedIn(this)) { goHome(); return }
        if (expired) Store.setLoggedIn(this, false)

        setContentView(R.layout.activity_login)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            if (Auth.isExpired(this)) { showExpired(); return@setOnClickListener }
            if (Auth.login(this, etUser.text.toString(), etPass.text.toString())) {
                Store.setLoggedIn(this, true)
                goHome()
            } else {
                Toast.makeText(this, getString(R.string.invalid_login), Toast.LENGTH_SHORT).show()
            }
        }
        if (expired) showExpired()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun showExpired() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.expired_title))
            .setMessage(getString(R.string.expired_msg, Auth.expiryText(this)))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.renew_btn)) { _, _ ->
                Auth.beginRenewal(this)
                startActivity(Intent(this, OtpActivity::class.java))
            }
            .show()
    }
}
