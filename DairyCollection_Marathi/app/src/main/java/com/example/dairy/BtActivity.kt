package com.example.dairy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Base activity that asks for Bluetooth (Android 12+) and SMS permissions before running an action
open class BtActivity : AppCompatActivity() {
    private var pendingBt: (() -> Unit)? = null
    private var pendingSms: (() -> Unit)? = null
    private var smsDenied: (() -> Unit)? = null

    fun withBt(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingBt = action
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 77)
        }
    }

    fun withSms(onDenied: () -> Unit, action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingSms = action
            smsDenied = onDenied
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 78)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val ok = results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (code) {
            77 -> {
                if (ok) pendingBt?.invoke()
                else Toast.makeText(this, getString(R.string.bt_perm), Toast.LENGTH_LONG).show()
                pendingBt = null
            }
            78 -> {
                if (ok) pendingSms?.invoke() else smsDenied?.invoke()
                pendingSms = null
                smsDenied = null
            }
        }
    }
}
