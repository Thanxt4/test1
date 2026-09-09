package com.example.wt80bprinter

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Small helper for building ESC/POS command bytes.
 *
 * WT-80B supports ESC/POS, TSPL and CPCL. We stick to ESC/POS here since it is
 * the most widely supported command set for receipts/labels on this class of
 * printer, and RawBT / most POS apps use it as well.
 *
 * Thai text is rendered to a bitmap and sent as a raster image (GS v 0) rather
 * than as raw text bytes, because relying on the printer's built-in Thai code
 * page requires knowing the exact code page number for this specific unit,
 * which varies by firmware batch. Printing pixels sidesteps that entirely.
 */
object EscPosBuilder {

    private const val ESC = 0x1B
    private const val GS = 0x1D

    fun initPrinter(): ByteArray = byteArrayOf(ESC.toByte(), '@'.code.toByte())

    fun feed(lines: Int): ByteArray =
        byteArrayOf(ESC.toByte(), 'd'.code.toByte(), lines.coerceIn(0, 255).toByte())

    fun lineFeed(): ByteArray = byteArrayOf(0x0A)

    /**
     * Converts a bitmap (already sized to the printer's dot width, e.g. 384
     * dots for 58mm or 576 dots for 80mm paper) into an ESC/POS raster bit
     * image command (GS v 0). White pixels are skipped, anything darker than
     * mid-gray is printed as black.
     */
    fun bitmapToRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val out = ByteArrayOutputStream()
        out.write(GS)
        out.write('v'.code)
        out.write('0'.code)
        out.write(0) // mode: normal (no scaling)
        out.write(widthBytes and 0xFF)
        out.write((widthBytes shr 8) and 0xFF)
        out.write(height and 0xFF)
        out.write((height shr 8) and 0xFF)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            var bitBuffer = 0
            var bitCount = 0
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = r * 0.299 + g * 0.587 + b * 0.114
                val isBlack = luminance < 128
                bitBuffer = (bitBuffer shl 1) or (if (isBlack) 1 else 0)
                bitCount++
                if (bitCount == 8) {
                    out.write(bitBuffer)
                    bitBuffer = 0
                    bitCount = 0
                }
            }
            if (bitCount > 0) {
                bitBuffer = bitBuffer shl (8 - bitCount)
                out.write(bitBuffer)
            }
        }
        return out.toByteArray()
    }

    /**
     * Builds a CODE128 barcode (code set B, i.e. standard printable ASCII)
     * using the GS k command with explicit length (m = 73).
     */
    fun code128Barcode(data: String, heightDots: Int = 80, moduleWidth: Int = 2): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(GS); out.write('h'.code); out.write(heightDots.coerceIn(1, 255))
        out.write(GS); out.write('w'.code); out.write(moduleWidth.coerceIn(2, 6))
        out.write(GS); out.write('H'.code); out.write(2) // print human-readable text below

        val payload = "{B$data"
        out.write(GS); out.write('k'.code); out.write(73); out.write(payload.length)
        out.write(payload.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}
