package com.cartvlab.core.common

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded equivalent of readNBytes, also available on Android 10-12. */
fun InputStream.readUpTo(limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit, 8192))
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count == 0) { val byte = read(); if (byte < 0) break; output.write(byte); remaining-- }
        else { output.write(buffer, 0, count); remaining -= count }
    }
    return output.toByteArray()
}