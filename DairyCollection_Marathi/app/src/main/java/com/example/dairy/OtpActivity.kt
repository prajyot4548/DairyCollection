package com.example.dairy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject

class OtpActivity : BtActivity() {
    private lateinit var etOtp: EditText
    private lateinit var btnVerify: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)
        etOtp = findViewById(R.id.etOtp)
        btnVerify = findViewById(R.id.btnVerify)
        btnResend = findViewById(R.id.btnResend)
        tvTimer = findViewById(R.id.tvTimer)
        val how = if (Auth.emailMode()) getString(R.string.otp_how_email) else getString(R.string.otp_how_sms, Auth.OWNER_MOBILE)
        findViewById<TextView>(R.id.tvOtpInfo).text = getString(
            R.string.otp_info_fmt,
            if (Auth.isRenewal(this)) getString(R.string.otp_renew_prefix) else "",
            how
        )

        btnVerify.setOnClickListener { verify() }
        btnResend.setOnClickListener { sendOtp() }
        if (Auth.otpActive(this)) startTimer() else sendOtp()
    }

    private fun sendOtp() {
        val wait = Auth.resendWaitSeconds(this)
        if (wait > 0) {
            Toast.makeText(this, fmt(R.string.wait_sec, wait), Toast.LENGTH_SHORT).show()
            return
        }
        if (Auth.emailMode()) sendEmailOtp() else sendSmsOtp()
    }

    // ---------- OTP by email (Google Apps Script) ----------
    private fun sendEmailOtp() {
        btnResend.isEnabled = false
        tvTimer.text = getString(R.string.sending_otp)
        val renew = Auth.isRenewal(this)
        val (name, code, mobile) = Auth.details(this)
        Thread {
            var id: String? = null
            var err: String? = null
            try {
                val r = OtpServer.call(
                    JSONObject().put("action", "send").put("renew", renew)
                        .put("name", name).put("code", code).put("mobile", mobile)
                )
                if (r.optBoolean("ok")) id = r.getString("id") else err = r.optString("error", "failed")
            } catch (e: Exception) {
                err = e.message ?: "नेटवर्क अडचण"
            }
            val fid = id
            val ferr = err
            runOnUiThread {
                if (fid != null) {
                    Auth.saveOtpId(this, fid)
                    Toast.makeText(this, getString(R.string.otp_sent_email), Toast.LENGTH_LONG).show()
                    startTimer()
                } else {
                    tvTimer.text = getString(R.string.otp_send_fail, errText(ferr))
                    btnResend.isEnabled = true
                }
            }
        }.start()
    }

    private fun verifyEmail(otp: String) {
        val id = Auth.otpId(this)
        if (id.isEmpty() || !Auth.otpActive(this)) { etOtp.error = getString(R.string.otp_expired); return }
        btnVerify.isEnabled = false
        Thread {
            var res: JSONObject? = null
            var err: String? = null
            try {
                res = OtpServer.call(JSONObject().put("action", "verify").put("id", id).put("otp", otp))
            } catch (e: Exception) {
                err = e.message ?: "नेटवर्क अडचण"
            }
            val fres = res
            val ferr = err
            runOnUiThread {
                btnVerify.isEnabled = true
                when {
                    ferr != null || fres == null -> etOtp.error = getString(R.string.net_problem, ferr ?: "")
                    fres.optBoolean("ok") -> { Auth.completeRegistration(this); finishOk() }
                    fres.optString("error") == "wrong" -> etOtp.error = fmt(R.string.otp_wrong, fres.optInt("left"))
                    fres.optString("error") == "locked" -> etOtp.error = getString(R.string.otp_locked)
                    else -> etOtp.error = getString(R.string.otp_expired)
                }
            }
        }.start()
    }

    // ---------- OTP by SMS (used only while OTP_URL is empty) ----------
    private fun sendSmsOtp() {
        withSms({ smsBlocked() }) {
            val otp = Auth.newOtp(this)
            if (Sms.send(this, Auth.OWNER_MOBILE, Auth.otpMessage(this, otp))) {
                startTimer()
            } else {
                Auth.clearOtp(this)
                smsBlocked()
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        val s = Auth.resendWaitSeconds(this)
        if (s <= 0) {
            btnResend.isEnabled = true
            tvTimer.text = getString(R.string.resend_prompt)
            return
        }
        btnResend.isEnabled = false
        timer = object : CountDownTimer(s * 1000L, 1000L) {
            override fun onTick(ms: Long) { tvTimer.text = fmt(R.string.resend_in, (ms / 1000 + 1).toInt()) }
            override fun onFinish() {
                btnResend.isEnabled = true
                tvTimer.text = getString(R.string.resend_prompt)
            }
        }.start()
    }

    private fun verify() {
        val otp = etOtp.text.toString().trim()
        if (otp.length != 6) { etOtp.error = getString(R.string.otp_enter6); return }
        if (Auth.emailMode()) { verifyEmail(otp); return }
        when (Auth.verifyOtp(this, otp)) {
            OtpResult.OK -> finishOk()
            OtpResult.WRONG -> etOtp.error = fmt(R.string.otp_wrong, Auth.triesLeft(this))
            OtpResult.EXPIRED -> etOtp.error = getString(R.string.otp_expired)
            OtpResult.LOCKED -> etOtp.error = getString(R.string.otp_locked)
        }
    }

    private fun finishOk() {
        Toast.makeText(this, getString(R.string.reg_done), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun smsBlocked() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sms_blocked_title))
            .setMessage(getString(R.string.otp_sms_blocked_msg))
            .setPositiveButton(getString(R.string.app_settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // turns the short error code from the Google script into a readable Marathi message
    private fun errText(e: String?): String =
        if (e == "unauthorized") getString(R.string.otp_unauthorized) else (e ?: "")

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
