package com.example.wt80bprinter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wt80bprinter.databinding.ActivityMainBinding
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val printerManager = BluetoothPrinterManager()

    // 576 dots = 72mm printable width at 203dpi, matches an 80mm-paper printer
    // like the WT-80B. Change to 384 if your unit is a narrower 58mm variant.
    private val printWidthDots = 576

    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    private val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadPairedDevices()
            } else {
                toast("ต้องอนุญาตสิทธิ์ Bluetooth ก่อนจึงจะใช้งานได้")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.refreshDevicesButton.setOnClickListener { ensurePermissionThenLoadDevices() }
        binding.connectButton.setOnClickListener { connectToSelectedDevice() }
        binding.printTextButton.setOnClickListener { printCustomText() }
        binding.printSampleReceiptButton.setOnClickListener { printSampleReceipt() }
        binding.printBarcodeButton.setOnClickListener { printBarcode() }
        binding.feedPaperButton.setOnClickListener { sendRaw(EscPosBuilder.feed(4)) }

        binding.deviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDevice = pairedDevices.getOrNull(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDevice = null
            }
        }

        ensurePermissionThenLoadDevices()
    }

    // ---------- Bluetooth permission & device list ----------

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissionThenLoadDevices() {
        if (!printerManager.isBluetoothAvailable()) {
            toast("อุปกรณ์นี้ไม่มี Bluetooth")
            return
        }
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        loadPairedDevices()
    }

    private fun loadPairedDevices() {
        if (!printerManager.isBluetoothEnabled()) {
            toast("กรุณาเปิด Bluetooth ของโทรศัพท์ก่อน")
            return
        }
        pairedDevices = printerManager.getBondedDevices()
        if (pairedDevices.isEmpty()) {
            toast("ไม่พบอุปกรณ์ที่จับคู่ไว้ กรุณาจับคู่ (Pair) เครื่องพิมพ์ผ่านตั้งค่า Bluetooth ของโทรศัพท์ก่อน")
        }
        val names = pairedDevices.map { device ->
            val name = try {
                device.name ?: "ไม่ทราบชื่อ"
            } catch (e: SecurityException) {
                "ไม่ทราบชื่อ"
            }
            "$name (${device.address})"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        binding.deviceSpinner.setAdapter(adapter)
        selectedDevice = pairedDevices.firstOrNull()
    }

    // ---------- Connection ----------

    private fun connectToSelectedDevice() {
        val device = selectedDevice
        if (device == null) {
            toast("กรุณาเลือกเครื่องพิมพ์ก่อน")
            return
        }
        setStatus("กำลังเชื่อมต่อ...", connected = false)
        thread {
            try {
                printerManager.connect(device)
                runOnUiThread { setStatus("สถานะ: เชื่อมต่อแล้ว", connected = true) }
            } catch (e: IOException) {
                runOnUiThread {
                    setStatus("สถานะ: เชื่อมต่อไม่สำเร็จ", connected = false)
                    toast("เชื่อมต่อไม่สำเร็จ: ${e.message}")
                }
            } catch (e: SecurityException) {
                runOnUiThread {
                    setStatus("สถานะ: ไม่มีสิทธิ์เชื่อมต่อ", connected = false)
                    toast("ไม่มีสิทธิ์ Bluetooth: ${e.message}")
                }
            }
        }
    }

    private fun setStatus(text: String, connected: Boolean) {
        binding.statusText.text = text
        binding.statusText.setTextColor(if (connected) Color.parseColor("#2E7D32") else Color.parseColor("#B00020"))
    }

    // ---------- Printing ----------

    private fun printCustomText() {
        val text = binding.customTextInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            toast("กรุณาพิมพ์ข้อความก่อน")
            return
        }
        val bitmap = renderTextToBitmap(text, printWidthDots)
        sendRaw(
            EscPosBuilder.initPrinter() +
                EscPosBuilder.bitmapToRaster(bitmap) +
                EscPosBuilder.feed(4)
        )
    }

    private fun printSampleReceipt() {
        val sample = buildString {
            appendLine("ร้านค้าตัวอย่าง")
            appendLine("ใบเสร็จรับเงิน / Receipt")
            appendLine("------------------------------")
            appendLine("สินค้า A          x1     50.00")
            appendLine("สินค้า B          x2    120.00")
            appendLine("------------------------------")
            appendLine("รวมทั้งสิ้น              170.00")
            appendLine("ขอบคุณที่ใช้บริการ")
        }
        val bitmap = renderTextToBitmap(sample.trim(), printWidthDots)
        sendRaw(
            EscPosBuilder.initPrinter() +
                EscPosBuilder.bitmapToRaster(bitmap) +
                EscPosBuilder.feed(4)
        )
    }

    private fun printBarcode() {
        val data = binding.barcodeInput.text?.toString()?.trim().orEmpty()
        if (data.isEmpty()) {
            toast("กรุณากรอกข้อมูลบาร์โค้ดก่อน")
            return
        }
        sendRaw(
            EscPosBuilder.initPrinter() +
                EscPosBuilder.code128Barcode(data) +
                EscPosBuilder.feed(4)
        )
    }

    private fun sendRaw(data: ByteArray) {
        if (!printerManager.isConnected) {
            toast("กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน")
            return
        }
        thread {
            try {
                printerManager.write(data)
            } catch (e: IOException) {
                runOnUiThread { toast("พิมพ์ไม่สำเร็จ: ${e.message}") }
            }
        }
    }

    // ---------- Bitmap rendering (used so Thai text prints reliably) ----------

    private fun renderTextToBitmap(text: String, widthDots: Int): Bitmap {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT
        }

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, widthDots)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
            .setIncludePad(false)
            .build()

        val bitmap = Bitmap.createBitmap(
            widthDots,
            staticLayout.height + 16,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(0f, 8f)
        staticLayout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        printerManager.disconnect()
    }
}
