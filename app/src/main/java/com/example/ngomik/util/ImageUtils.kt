package com.example.ngomik.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

object ImageUtils {
    fun downloadAndDownsample(urlStr: String, reqWidth: Int): Bitmap? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doInput = true
        conn.instanceFollowRedirects = true
        conn.connect()
        val input = conn.inputStream
        val bytes = input.readBytes()
        input.close()
        conn.disconnect()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth)
        options.inJustDecodeBounds = false
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return bmp
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
        val width = options.outWidth
        var inSampleSize = 1
        if (width > reqWidth && reqWidth > 0) {
            val halfWidth = width / 2
            while ((halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
