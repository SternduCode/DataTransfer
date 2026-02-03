package com.sterndu.data.transfer

import java.io.IOException
import java.io.InputStream

@JvmOverloads
@Throws(IOException::class)
fun readXBytes(b: ByteArray, `is`: InputStream, amount: Int, timeout: Long = 1000L): Boolean {
    if (b.size < amount) return false
    if (amount == 0) return true
    var time = System.currentTimeMillis()
    var written = 0
    while (System.currentTimeMillis() - time < timeout && written < amount) try {
        if (`is`.available() > 0) {
            val w = `is`.read(b, written, (amount - written).coerceAtMost(`is`.available()))
            written += w
            if (w > 0) time = System.currentTimeMillis() else written -= w
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return written >= amount
}