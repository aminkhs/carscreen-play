package com.cartvlab.core.common
import java.io.*
import org.junit.Assert.*
import org.junit.Test
class MirrorProtocolTest {
    @Test fun frameRoundTripPreservesFlagsTimeAndPayload() {
        val bytes = ByteArrayOutputStream()
        MirrorProtocol.write(DataOutputStream(bytes), MirrorProtocol.Packet(2, 1, 500000, byteArrayOf(0,0,1,5)))
        val frame = MirrorProtocol.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals(500000L, frame.pts); assertEquals(1, frame.flags)
        assertArrayEquals(byteArrayOf(0,0,1,5), frame.data)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedUntrustedPacket() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeInt(2); writeInt(0); writeLong(0); writeInt(Int.MAX_VALUE) }
        MirrorProtocol.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownPacketType() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).writeInt(99)
        MirrorProtocol.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }
    @Test(expected = EOFException::class) fun truncatedPacketFailsClosed() {
        MirrorProtocol.read(DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0))))
    }
}
