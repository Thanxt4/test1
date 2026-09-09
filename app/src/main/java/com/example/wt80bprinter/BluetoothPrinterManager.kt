package com.example.wt80bprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Handles the classic Bluetooth (SPP) connection to the WT-80B printer.
 *
 * WT-80B, like most generic ESC/POS thermal printers, exposes a Serial Port
 * Profile (SPP) service once paired through the phone's Bluetooth settings.
 * We connect to that service using the standard SPP UUID and stream raw
 * ESC/POS bytes to it.
 */
class BluetoothPrinterManager {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.toList().orEmpty()
    }

    fun isBluetoothAvailable(): Boolean = BluetoothAdapter.getDefaultAdapter() != null

    fun isBluetoothEnabled(): Boolean = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun connect(device: BluetoothDevice) {
        disconnect()
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            newSocket.connect()
        } catch (e: IOException) {
            // Fallback for some clone chipsets that don't support the standard
            // createRfcommSocketToServiceRecord path.
            try {
                val fallback = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                fallback.connect()
                socket = fallback
                outputStream = fallback.outputStream
                return
            } catch (fallbackError: Exception) {
                throw e
            }
        }
        socket = newSocket
        outputStream = newSocket.outputStream
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        val stream = outputStream ?: throw IOException("ยังไม่ได้เชื่อมต่อเครื่องพิมพ์")
        stream.write(data)
        stream.flush()
    }

    fun disconnect() {
        try {
            outputStream?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        outputStream = null
        socket = null
    }
}
