package com.example.dairy

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

// One column of text on a printed row
private class Cell(val text: String, val x: Float, val right: Boolean)

// Draws the receipt/report as a picture (so KF-Kiran Marathi names print correctly)
private class Sheet(private val w: Int) {
    private class Op(val h: Int, val draw: (Canvas, Float) -> Unit)

    private val ops = ArrayList<Op>()

    private fun paint(size: Float, bold: Boolean = false, tf: Typeface? = null, align: Paint.Align = Paint.Align.LEFT): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.BLACK
        p.textSize = size
        p.textAlign = align
        p.typeface = tf ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (tf != null && bold) p.isFakeBoldText = true
        return p
    }

    private fun height(p: Paint) = (p.fontMetrics.descent - p.fontMetrics.ascent).toInt() + 6

    private fun base(p: Paint, y: Float) = y - p.fontMetrics.ascent + 3f

    fun center(text: String, size: Float, bold: Boolean = false) {
        val p = paint(size, bold, null, Paint.Align.CENTER)
        while (p.measureText(text) > w && p.textSize > 12f) p.textSize = p.textSize - 1f // long names shrink to fit the paper
        ops.add(Op(height(p)) { c, y -> c.drawText(text, w / 2f, base(p, y), p) })
    }

    fun sep() {
        val p = Paint()
        p.color = Color.BLACK
        p.strokeWidth = 2f
        ops.add(Op(14) { c, y -> c.drawLine(0f, y + 7f, w.toFloat(), y + 7f, p) })
    }

    fun gap(h: Int) { ops.add(Op(h) { _, _ -> }) }

    fun kv(label: String, value: String, tf: Typeface? = null, bold: Boolean = false, size: Float = 22f) {
        val pl = paint(size, bold)
        val pv = paint(if (tf != null) size * 1.25f else size, bold, tf)
        val x0 = w * 0.34f
        while (pv.measureText(value) > w - x0 && pv.textSize > 12f) pv.textSize = pv.textSize - 1f
        val h = maxOf(height(pl), height(pv))
        ops.add(Op(h) { c, y ->
            val b = y - minOf(pl.fontMetrics.ascent, pv.fontMetrics.ascent) + 3f
            c.drawText(label, 0f, b, pl)
            c.drawText(value, x0, b, pv)
        })
    }

    // customer name (own font) on the left, ID on the right
    fun nameLine(name: String, id: String, tf: Typeface?) {
        val pn = paint(if (tf != null) 26f else 22f, false, tf)
        val pi = paint(18f, false, null, Paint.Align.RIGHT)
        val idText = "क्र. $id"
        val avail = w - pi.measureText(idText) - 8f
        while (pn.measureText(name) > avail && pn.textSize > 12f) pn.textSize = pn.textSize - 1f
        val h = maxOf(height(pn), height(pi))
        ops.add(Op(h) { c, y ->
            val b = y - minOf(pn.fontMetrics.ascent, pi.fontMetrics.ascent) + 3f
            c.drawText(name, 0f, b, pn)
            c.drawText(idText, w.toFloat(), b, pi)
        })
    }

    fun table(cells: List<Cell>, size: Float, bold: Boolean = false) {
        val pl = paint(size, bold)
        val pr = paint(size, bold, null, Paint.Align.RIGHT)
        ops.add(Op(height(pl)) { c, y ->
            val b = base(pl, y)
            for (cell in cells) c.drawText(cell.text, cell.x, b, if (cell.right) pr else pl)
        })
    }

    fun render(): Bitmap {
        val total = ops.sumOf { it.h } + 8
        val bmp = Bitmap.createBitmap(w, total, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = 0f
        for (op in ops) {
            op.draw(c, y)
            y += op.h
        }
        return bmp
    }
}

@SuppressLint("MissingPermission")
object Printer {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val DOTS = 384 // printable width in dots: 384 for 58mm paper, 576 for 80mm paper

    private fun prefs(c: Context) = c.getSharedPreferences("dairy", Context.MODE_PRIVATE)
    fun savedName(c: Context): String? = prefs(c).getString("printer_name", null)
    private fun savedAddress(c: Context): String? = prefs(c).getString("printer_addr", null)

    private fun adapter(c: Context): BluetoothAdapter? =
        (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // ---------- choose printer from paired devices ----------
    fun choose(a: Activity, onDone: () -> Unit = {}) {
        val ad = adapter(a)
        if (ad == null || !ad.isEnabled) {
            Toast.makeText(a, a.getString(R.string.bt_turn_on), Toast.LENGTH_LONG).show(); return
        }
        val devices: List<BluetoothDevice> = ad.bondedDevices.toList()
        if (devices.isEmpty()) {
            Toast.makeText(a, a.getString(R.string.no_paired), Toast.LENGTH_LONG).show()
            return
        }
        val labels = devices.map { "${it.name ?: a.getString(R.string.unknown)}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.select_printer))
            .setItems(labels) { _, i ->
                prefs(a).edit().putString("printer_name", devices[i].name ?: devices[i].address)
                    .putString("printer_addr", devices[i].address).apply()
                onDone()
            }
            .setNegativeButton(a.getString(R.string.cancel), null).show()
    }

    // ---------- print ----------
    fun printOrChoose(a: Activity, data: ByteArray) {
        if (savedAddress(a) == null) choose(a) { print(a, data) } else print(a, data)
    }

    private fun print(a: Activity, data: ByteArray) {
        val addr = savedAddress(a) ?: return
        Toast.makeText(a, a.getString(R.string.printing), Toast.LENGTH_SHORT).show()
        Thread {
            var err: String? = null
            var sock: BluetoothSocket? = null
            try {
                val ad = adapter(a)
                if (ad == null || !ad.isEnabled) throw Exception(a.getString(R.string.bt_off))
                ad.cancelDiscovery()
                sock = ad.getRemoteDevice(addr).createRfcommSocketToServiceRecord(SPP)
                sock.connect()
                val os = sock.outputStream
                var i = 0
                while (i < data.size) { // send in small pieces so cheap printers do not lose data
                    val n = minOf(1024, data.size - i)
                    os.write(data, i, n)
                    os.flush()
                    i += n
                    Thread.sleep(25)
                }
                Thread.sleep(1500) // let the printer finish before closing
            } catch (e: Exception) {
                err = e.message ?: a.getString(R.string.print_failed_plain)
            } finally {
                try { sock?.close() } catch (_: Exception) {}
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(a, err?.let { a.getString(R.string.print_failed, it) } ?: a.getString(R.string.printed), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ---------- picture -> ESC/POS raster ----------
    private fun escpos(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val bpr = (w + 7) / 8
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40)) // initialise
        var y = 0
        while (y < h) {
            val band = minOf(128, h - y)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (bpr and 0xFF).toByte(), (bpr shr 8).toByte(), (band and 0xFF).toByte(), (band shr 8).toByte()))
            for (r in 0 until band) {
                for (bx in 0 until bpr) {
                    var b = 0
                    for (bit in 0..7) {
                        val x = bx * 8 + bit
                        if (x < w) {
                            val p = px[(y + r) * w + x]
                            val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                            if (lum < 140) b = b or (0x80 shr bit)
                        }
                    }
                    out.write(b)
                }
            }
            y += band
        }
        out.write(byteArrayOf(0x1B, 0x64, 0x04)) // feed 4 lines
        return out.toByteArray()
    }

    private fun f(fmt: String, vararg a: Any) = String.format(Locale.US, fmt, *a)

    // ---------- receipt ----------
    fun receipt(c: Context, r: Record, reprint: Boolean = false): ByteArray {
        val s = Sheet(DOTS)
        s.center(Store.SHOP_NAME, 30f, true)
        s.center(c.getString(R.string.r_title), 22f)
        if (reprint) s.center(c.getString(R.string.r_dup), 22f, true)
        s.sep()
        s.kv(c.getString(R.string.date), r.date)
        s.kv(c.getString(R.string.r_shift), r.sessionMr)
        s.kv(c.getString(R.string.r_milk), "${r.typeName} (${r.milkType})")
        s.kv(c.getString(R.string.r_cust), r.name, Fonts.nameTypeface(c, r.name))
        s.kv(c.getString(R.string.r_custid), r.id)
        s.sep()
        s.kv(c.getString(R.string.liter), f("%.2f", r.liter))
        s.kv(c.getString(R.string.fat), f("%.1f", r.fat))
        s.kv(c.getString(R.string.snf), f("%.1f", r.snf))
        s.kv(c.getString(R.string.degree), f("%.1f", r.degree))
        s.kv(c.getString(R.string.r_rate), f("%.2f", r.rate))
        s.sep()
        s.kv(c.getString(R.string.r_total), f("%.2f", r.total), null, true, 28f)
        s.sep()
        s.center(c.getString(R.string.r_thanks), 22f)
        s.gap(10)
        return escpos(s.render())
    }

    // ---------- daily collection report ----------
    private fun cells(w: Float, a: String, b: String, ltr: String, fat: String, snf: String, deg: String, rate: String, amt: String) = listOf(
        Cell(a, 0f, false), Cell(b, 34f, false),
        Cell(ltr, w * 0.325f, true), Cell(fat, w * 0.44f, true), Cell(snf, w * 0.55f, true),
        Cell(deg, w * 0.67f, true), Cell(rate, w * 0.83f, true), Cell(amt, w, true)
    )

    fun report(c: Context, date: String, list: List<Record>): ByteArray {
        val w = DOTS.toFloat()
        val s = Sheet(DOTS)
        s.center(Store.SHOP_NAME, 30f, true)
        s.center(c.getString(R.string.title_report), 22f)
        s.kv(c.getString(R.string.date), date)
        s.sep()
        s.table(
            cells(
                w, c.getString(R.string.rp_ms), c.getString(R.string.rp_cb), c.getString(R.string.rp_ltr),
                c.getString(R.string.fat), c.getString(R.string.snf), c.getString(R.string.rp_deg),
                c.getString(R.string.r_rate), c.getString(R.string.rp_amt)
            ), 17f, true
        )
        s.sep()
        val rows = list.sortedWith(compareBy({ it.session != "Morning" }, { it.id.toLongOrNull() ?: Long.MAX_VALUE }, { it.id }, { it.milkType }))
        for (r in rows) {
            s.nameLine(r.name, r.id, Fonts.nameTypeface(c, r.name))
            s.table(cells(w, r.shiftMr, r.typeShortMr, f("%.2f", r.liter), f("%.1f", r.fat), f("%.1f", r.snf), f("%.1f", r.degree), f("%.2f", r.rate), f("%.2f", r.total)), 17f)
        }
        s.sep()
        fun tot(label: String, l: List<Record>, bold: Boolean = false) = s.table(
            listOf(Cell(label, 0f, false), Cell(f("%.2f", l.sumOf { it.liter }) + " " + c.getString(R.string.rp_ltr), w * 0.62f, true), Cell(f("%.2f", l.sumOf { it.total }), w, true)),
            19f, bold
        )
        tot(c.getString(R.string.morning), list.filter { it.session == "Morning" })
        tot(c.getString(R.string.evening), list.filter { it.session == "Evening" })
        tot(c.getString(R.string.rp_cow), list.filter { it.milkType == "C" })
        tot(c.getString(R.string.rp_buf), list.filter { it.milkType == "B" })
        s.sep()
        tot(c.getString(R.string.r_total), list, true)
        s.kv(c.getString(R.string.rp_entries), list.size.toString(), null, false, 20f)
        s.kv(c.getString(R.string.rp_avgfat), f("%.2f", Store.avg(list) { it.fat }), null, false, 20f)
        s.kv(c.getString(R.string.rp_avgsnf), f("%.2f", Store.avg(list) { it.snf }), null, false, 20f)
        s.sep()
        s.gap(10)
        return escpos(s.render())
    }
}
