package com.cartvlab.core.common

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun limitsBytesWithoutConsumingTheRemainder() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(1, 2, 3), stream.readUpTo(3))
        assertEquals(4, stream.read())
    }
    @Test fun acceptsEarlyEofAndShortReads() {
        val stream = object : ByteArrayInputStream(byteArrayOf(5, 6, 7)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, minOf(length, 1))
        }
        assertArrayEquals(byteArrayOf(5, 6, 7), stream.readUpTo(100))
    }
    @Test fun makesProgressIfBulkReadReturnsZero() {
        val stream = object : InputStream() {
            var next = 0
            override fun read(): Int = if (next < 3) next++ else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertArrayEquals(byteArrayOf(0, 1, 2), stream.readUpTo(10))
    }
}
