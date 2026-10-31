package com.example.dairy

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class OtpResult { OK, WRONG, EXPIRED, LOCKED }

object Auth {
    const val OWNER_MOBILE = "7276856584"   // used for the SMS OTP when OTP_URL is empty

    // Paste the Google Apps Script web app URL (ends with /exec) here to send the OTP to Gmail.
    // While this is empty the OTP is sent by SMS to OWNER_MOBILE instead.
    const val OTP_URL = ""
    const val OTP_SECRET = "5a236fc1d046134e0693dcff"   // must match SECRET in the Apps Script

    private const val DAY = 86_400_000L
    private const val OTP_VALID_MS = 10 * 60_000L
    private const val RESEND_MS = 60_000L
    private const val MAX_TRIES = 5

    private fun p(c: Context) = c.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun hash(text: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + text).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun newSalt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    // Clock that cannot be moved backwards (stops "change the phone date" tricks)
    fun now(c: Context): Long {
        val n = maxOf(System.currentTimeMillis(), p(c).getLong("last_seen", 0L))
        p(c).edit().putLong("last_seen", n).apply()
        return n
    }

    fun emailMode() = OTP_URL.startsWith("https://")

    // ---------- registration state ----------
    fun isRegistered(c: Context) = p(c).getLong("reg_time", 0L) > 0L && p(c).contains("user_code")
    fun fullName(c: Context): String = p(c).getString("full_name", "") ?: ""

    fun expiryTime(c: Context): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = p(c).getLong("reg_time", 0L)
        cal.add(Calendar.YEAR, 1)
        return cal.timeInMillis
    }

    fun isExpired(c: Context) = now(c) >= expiryTime(c)
    fun daysLeft(c: Context): Int = (((expiryTime(c) - now(c)) + DAY - 1) / DAY).toInt()
    fun expiryText(c: Context): String =
        SimpleDateFormat("dd MMM yyyy", MR).format(Date(expiryTime(c)))

    // ---------- pending registration / renewal ----------
    fun savePending(c: Context, name: String, code: String, pass: String, mobile: String) {
        val salt = newSalt()
        p(c).edit()
            .putString("pend_name", name).putString("pend_code", code)
            .putString("pend_salt", salt).putString("pend_hash", hash(pass, salt))
            .putString("pend_mobile", mobile).putBoolean("pend_renew", false)
            .remove("otp_hash").remove("otp_id").putLong("otp_time", 0L).apply()
    }

    fun beginRenewal(c: Context) {
        p(c).edit().putBoolean("pend_renew", true).remove("otp_hash").remove("otp_id").putLong("otp_time", 0L).apply()
    }

    fun isRenewal(c: Context) = p(c).getBoolean("pend_renew", false)

    // ---------- OTP ----------
    fun newOtp(c: Context): String {
        val otp = String.format(Locale.US, "%06d", SecureRandom().nextInt(1_000_000))
        val salt = newSalt()
        p(c).edit().putString("otp_salt", salt).putString("otp_hash", hash(otp, salt))
            .putLong("otp_time", now(c)).putInt("otp_tries", 0).apply()
        return otp
    }

    fun clearOtp(c: Context) { p(c).edit().remove("otp_hash").remove("otp_id").putLong("otp_time", 0L).apply() }

    // Email mode: the OTP lives on the Google side, the app only keeps the request id
    fun saveOtpId(c: Context, id: String) { p(c).edit().putString("otp_id", id).putLong("otp_time", now(c)).apply() }
    fun otpId(c: Context): String = p(c).getString("otp_id", "") ?: ""

    fun otpActive(c: Context) =
        (p(c).contains("otp_hash") || p(c).contains("otp_id")) && now(c) - p(c).getLong("otp_time", 0L) < OTP_VALID_MS

    fun resendWaitSeconds(c: Context): Int {
        val t = p(c).getLong("otp_time", 0L)
        if (t == 0L) return 0
        val left = RESEND_MS - (now(c) - t)
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    fun triesLeft(c: Context) = MAX_TRIES - p(c).getInt("otp_tries", 0)

    // name, user code, mobile of the registration or renewal in progress
    fun details(c: Context): Triple<String, String, String> {
        val sp = p(c)
        val renew = isRenewal(c)
        return Triple(
            (if (renew) sp.getString("full_name", "") else sp.getString("pend_name", "")) ?: "",
            (if (renew) sp.getString("user_code", "") else sp.getString("pend_code", "")) ?: "",
            (if (renew) sp.getString("mobile", "") else sp.getString("pend_mobile", "")) ?: ""
        )
    }

    fun otpMessage(c: Context, otp: String): String {
        val renew = isRenewal(c)
        val (name, code, mobile) = details(c)
        val kind = c.getString(if (renew) R.string.otp_kind_renew else R.string.otp_kind_new)
        return c.fmt(R.string.otp_msg_owner, otp, kind, name, code, mobile)
    }

    fun verifyOtp(c: Context, otp: String): OtpResult {
        val sp = p(c)
        if (!otpActive(c)) return OtpResult.EXPIRED
        if (triesLeft(c) <= 0) return OtpResult.LOCKED
        val ok = hash(otp, sp.getString("otp_salt", "") ?: "") == sp.getString("otp_hash", "")
        if (!ok) {
            sp.edit().putInt("otp_tries", sp.getInt("otp_tries", 0) + 1).apply()
            return if (triesLeft(c) <= 0) OtpResult.LOCKED else OtpResult.WRONG
        }
        completeRegistration(c)
        return OtpResult.OK
    }

    // Called after the OTP is confirmed: saves the new account (or renews the old one) for 1 year
    fun completeRegistration(c: Context) {
        val sp = p(c)
        val ed = sp.edit()
        if (!sp.getBoolean("pend_renew", false)) {
            ed.putString("full_name", sp.getString("pend_name", ""))
                .putString("user_code", sp.getString("pend_code", ""))
                .putString("salt", sp.getString("pend_salt", ""))
                .putString("pass_hash", sp.getString("pend_hash", ""))
                .putString("mobile", sp.getString("pend_mobile", ""))
        }
        ed.putLong("reg_time", now(c)).putBoolean("pend_renew", false)
            .remove("otp_hash").remove("otp_id").putLong("otp_time", 0L)
            .remove("pend_name").remove("pend_code").remove("pend_salt")
            .remove("pend_hash").remove("pend_mobile").apply()
    }

    // ---------- login ----------
    fun login(c: Context, code: String, pass: String): Boolean {
        val sp = p(c)
        return code.trim().equals(sp.getString("user_code", ""), ignoreCase = true) &&
            hash(pass, sp.getString("salt", "") ?: "") == sp.getString("pass_hash", "")
    }
}
