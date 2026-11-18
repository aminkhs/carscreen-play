package com.cartvlab.core.common

import java.io.DataInputStream
import java.io.DataOutputStream

/** Experimental, paired TCP framing, version 1. Video only; trusted local networks only. */
object MirrorProtocol {
    const val MAGIC = 0x41555031
    const val PORT = 52744
    const val MAX_PACKET = 4 * 1024 * 1024
    const val FORMAT = 1
    const val FRAME = 2
    data class Packet(val type: Int, val flags: Int, val pts: Long, val data: ByteArray)
    fun write(output: DataOutputStream, packet: Packet) {
        require(packet.data.size <= MAX_PACKET)
        output.writeInt(packet.type); output.writeInt(packet.flags); output.writeLong(packet.pts)
        output.writeInt(packet.data.size); output.write(packet.data); output.flush()
    }
    fun read(input: DataInputStream): Packet {
        val type = input.readInt()
        require(type == FORMAT || type == FRAME) { "Unknown packet type" }
        val flags = input.readInt()
        val pts = input.readLong()
        val size = input.readInt()
        require(size in 0..MAX_PACKET) { "Invalid packet size" }
        return Packet(type, flags, pts, ByteArray(size).also { input.readFully(it) })
    }
}
