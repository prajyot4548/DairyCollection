package com.example.dairy

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object Fonts {
    private var kiran: Typeface? = null
    private var loaded = false

    // KF-Kiran font file is read from app/src/main/assets/fonts/
    fun kiran(c: Context): Typeface? {
        if (!loaded) {
            loaded = true
            kiran = try {
                val f = c.assets.list("fonts")?.firstOrNull { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                if (f != null) Typeface.createFromAsset(c.assets, "fonts/$f") else null
            } catch (e: Exception) {
                null
            }
        }
        return kiran
    }

    fun available(c: Context) = kiran(c) != null

    private fun prefs(c: Context) = c.getSharedPreferences("dairy", Context.MODE_PRIVATE)
    fun kiranMode(c: Context) = prefs(c).getBoolean("names_kiran", true)
    fun setKiranMode(c: Context, v: Boolean) = prefs(c).edit().putBoolean("names_kiran", v).apply()

    fun hasDevanagari(s: String) = s.any { it.code in 0x0900..0x097F }

    // Font to use for a customer name (null = normal font)
    fun nameTypeface(c: Context, s: String): Typeface? =
        if (hasDevanagari(s) || !kiranMode(c)) null else kiran(c)

    fun apply(tv: TextView, c: Context, text: String, baseSp: Float) {
        val tf = nameTypeface(c, text)
        tv.text = text
        tv.typeface = tf ?: Typeface.DEFAULT
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (tf != null) baseSp * 1.3f else baseSp)
    }

    // Name that is readable in plain text (dialogs, SMS). KF-Kiran text is not readable without its font.
    fun safeName(c: Context, name: String, id: String): String =
        if (hasDevanagari(name) || !kiranMode(c)) name else c.fmt(R.string.cust_no_fmt, id)
}

// Legacy 8-bit text (Windows-1252, used by KF-Kiran files) <-> String, without losing any byte
object Legacy {
    private val table: CharArray = CharArray(256) { i ->
        if (i < 0x80 || i >= 0xA0) i.toChar()
        else {
            val ch = String(byteArrayOf(i.toByte()), Charset.forName("windows-1252"))[0]
            if (ch == '\uFFFD') i.toChar() else ch
        }
    }
    private val reverse: Map<Char, Byte> = HashMap<Char, Byte>().also { m ->
        for (i in 0..255) m[table[i]] = i.toByte()
    }

    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && ((b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())))
            return String(b, Charsets.UTF_16)
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            // not UTF-8: old-style ANSI file (KF-Kiran)
        }
        return String(CharArray(b.size) { table[b[it].toInt() and 0xFF] })
    }

    fun encode(s: String): ByteArray =
        if (s.all { reverse.containsKey(it) }) ByteArray(s.length) { reverse[s[it]]!! }
        else s.toByteArray(Charsets.UTF_8)
}
