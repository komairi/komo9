package com.komo.nota

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class Item(val nama: String, val banyak: Int, val harga: Long) {
    val total: Long get() = banyak * harga
}

class MainActivity : AppCompatActivity() {
    private val items = mutableListOf<Item>()
    private var printer: BluetoothDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var tvDaftar: TextView
    private lateinit var tvTotal: TextView
    private lateinit var etNama: EditText
    private lateinit var etBanyak: EditText
    private lateinit var etHarga: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDaftar = findViewById(R.id.tvDaftar)
        tvTotal = findViewById(R.id.tvTotal)
        etNama = findViewById(R.id.etNama)
        etBanyak = findViewById(R.id.etBanyak)
        etHarga = findViewById(R.id.etHarga)

        findViewById<Button>(R.id.btnTambah).setOnClickListener { tambahBarang() }
        findViewById<Button>(R.id.btnBluetooth).setOnClickListener { pilihPrinter() }
        findViewById<Button>(R.id.btnCetak).setOnClickListener { cetakNota() }
        findViewById<Button>(R.id.btnBaru).setOnClickListener {
            items.clear()
            updateView()
            etNama.text.clear()
            etBanyak.setText("1")
            etHarga.text.clear()
        }
    }

    private fun tambahBarang() {
        val nama = etNama.text.toString().trim()
        val banyak = etBanyak.text.toString().toIntOrNull() ?: 0
        val harga = etHarga.text.toString().toLongOrNull() ?: 0L
        if (nama.isEmpty() || banyak <= 0 || harga < 0) {
            Toast.makeText(this, "Isi nama, banyak, dan harga.", Toast.LENGTH_SHORT).show()
            return
        }
        items.add(Item(nama, banyak, harga))
        etNama.text.clear()
        etBanyak.setText("1")
        etHarga.text.clear()
        updateView()
    }

    private fun updateView() {
        if (items.isEmpty()) {
            tvDaftar.text = "Belum ada barang."
        } else {
            val sb = StringBuilder("No  Nama Barang              Banyak   Harga       Total\n")
            items.forEachIndexed { i, x ->
                sb.append("${i + 1}.  ${x.nama.take(20)}  ${x.banyak}   ${rupiah(x.harga)}   ${rupiah(x.total)}\n")
            }
            tvDaftar.text = sb.toString()
        }
        tvTotal.text = "TOTAL: ${rupiah(items.sumOf { it.total })}"
    }

    private fun rupiah(n: Long) = "Rp " + String.format(Locale.US, "%,d", n).replace(',', '.')

    private fun pilihPrinter() {
        if (adapter == null) {
            Toast.makeText(this, "HP tidak mendukung Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 100)
            return
        }

        val paired = adapter.bondedDevices.toList()
        if (paired.isEmpty()) {
            Toast.makeText(this, "Pairing printer dulu di pengaturan Bluetooth HP.", Toast.LENGTH_LONG).show()
            return
        }

        val names = paired.map { "${it.name ?: "Printer"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih Printer")
            .setItems(names) { _, which ->
                printer = paired[which]
                Toast.makeText(this, "Printer dipilih: ${printer?.name}", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun cetakNota() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Belum ada barang.", Toast.LENGTH_SHORT).show()
            return
        }
        if (printer == null) {
            Toast.makeText(this, "Pilih printer Bluetooth terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }

        Thread {
            try {
                if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread { Toast.makeText(this, "Izin Bluetooth belum diberikan.", Toast.LENGTH_LONG).show() }
                    return@Thread
                }

                val socket: BluetoothSocket =
                    printer!!.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket.connect()
                val out: OutputStream = socket.outputStream

                val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val sb = StringBuilder()
                sb.append("\u001B\u0040")
                sb.append("\u001B\u0061\u0001")
                sb.append("KoMo\n")
                sb.append("Dsn Wonosari Wetan\n")
                sb.append("085 232 636 024\n")
                sb.append("------------------------------\n")
                sb.append("\u001B\u0061\u0000")
                items.forEachIndexed { i, x ->
                    sb.append("${i + 1}. ${x.nama}\n")
                    sb.append("   ${x.banyak} x ${rupiah(x.harga)} = ${rupiah(x.total)}\n")
                }
                sb.append("------------------------------\n")
                sb.append("TOTAL: ${rupiah(items.sumOf { it.total })}\n")
                sb.append("\n${now}\n")
                sb.append("\n\n\n")
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.write(byteArrayOf(0x1D, 0x56, 0x00))
                out.flush()
                socket.close()

                runOnUiThread { Toast.makeText(this, "Nota berhasil dikirim ke printer.", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal cetak: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
