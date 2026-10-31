package com.example.dairy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast

object Sms {
    fun text(c: Context, r: Record): String = c.fmt(
        R.string.sms_body,
        Store.SHOP_NAME, r.date, r.sessionMr, r.typeName, Fonts.safeName(c, r.name, r.id),
        r.liter, r.fat, r.snf, r.degree, r.rate, r.total
    )

    // Sends through the phone's default SMS SIM (uses the SIM's own SMS pack)
    fun send(a: Activity, mobile: String, msg: String): Boolean {
        try {
            @Suppress("DEPRECATION")
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) a.getSystemService(SmsManager::class.java)
            else SmsManager.getDefault()
            sm.sendMultipartTextMessage(mobile, null, sm.divideMessage(msg), null, null)
            Toast.makeText(a, a.fmt(R.string.sms_sent, mobile), Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(a, a.fmt(R.string.sms_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            return false
        }
    }

    // Fallback: open the phone's Messages app with the text ready (no SMS permission needed)
    fun openComposer(a: Activity, mobile: String, msg: String) {
        try {
            a.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$mobile")).putExtra("sms_body", msg))
        } catch (e: Exception) {
            Toast.makeText(a, a.getString(R.string.no_msg_app), Toast.LENGTH_LONG).show()
        }
    }
}
