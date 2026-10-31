package com.example.dairy

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Talks to the Google Apps Script web app that emails the OTP and checks it
object OtpServer {
    fun call(json: JSONObject): JSONObject {
        json.put("secret", Auth.OTP_SECRET)
        val conn = URL(Auth.OTP_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            try {
                return JSONObject(text)
            } catch (e: Exception) {
                throw Exception("OTP सेवा नीट सेट केलेली नाही (वेब ॲप URL व access तपासा)")
            }
        } finally {
            conn.disconnect()
        }
    }
}
