package com.cartvlab.receiver

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.cartvlab.core.common.MirrorProtocol
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ReceiverServer(private val surface: Surface, private val code: String, private val status: (String) -> Unit) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private val worker = thread(name = "lab-receiver", isDaemon = true) {
        var decoder: MediaCodec? = null
        try {
            val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(MirrorProtocol.PORT)) }
            server = listener
            status("Listening · video only")
            while (active.get()) {
                val client = listener.accept()
                socket = client
                client.soTimeout = 8000
                client.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val authenticated = runCatching { input.readInt() == MirrorProtocol.MAGIC && input.readUTF() == code }.getOrDefault(false)
                client.getOutputStream().apply { write(if (authenticated) 1 else 0); flush() }
                if (!authenticated) { client.close(); continue }
                var frames = 0L
                try {
                    while (active.get()) {
                        val packet = MirrorProtocol.read(input)
                        if (packet.type == MirrorProtocol.FORMAT) {
                            decoder?.let { runCatching { it.stop() }; it.release() }
                            val header = DataInputStream(ByteArrayInputStream(packet.data))
                            val width = header.readInt(); val height = header.readInt()
                            require(width in 2..4096 && height in 2..4096)
                            val format = MediaFormat.createVideoFormat("video/avc", width, height)
                            for (key in listOf("csd-0", "csd-1")) {
                                val size = header.readInt(); require(size in 0..65536)
                                val bytes = ByteArray(size).also { header.readFully(it) }
                                if (bytes.isNotEmpty()) format.setByteBuffer(key, ByteBuffer.wrap(bytes))
                            }
                            decoder = MediaCodec.createDecoderByType("video/avc").apply { configure(format, surface, null, 0); start() }
                            status("Connected · ${width} × ${height}")
                        } else {
                            val codec = decoder ?: error("Missing video format")
                            val index = codec.dequeueInputBuffer(20000)
                            require(index >= 0) { "Decoder input stalled" }
                            val buffer = codec.getInputBuffer(index)!!
                            buffer.clear(); require(packet.data.size <= buffer.remaining())
                            buffer.put(packet.data)
                            codec.queueInputBuffer(index, 0, packet.data.size, packet.pts, packet.flags)
                            val info = MediaCodec.BufferInfo()
                            var output = codec.dequeueOutputBuffer(info, 10000)
                            while (output >= 0) {
                                codec.releaseOutputBuffer(output, true)
                                frames++
                                output = codec.dequeueOutputBuffer(info, 0)
                            }
                            if (frames % 30L == 0L) status("Receiving · $frames decoded frames · end-to-end latency unmeasured")
                        }
                    }
                } catch (_: Exception) { if (active.get()) status("Sender disconnected. Waiting for a new session.") }
                finally {
                    decoder?.let { runCatching { it.stop() }; runCatching { it.release() } }; decoder = null
                    client.close()
                }
            }
        } catch (_: Exception) { if (active.get()) status("Receiver stopped: port unavailable or network interrupted. Reopen this screen.") }
        finally {
            decoder?.let { runCatching { it.stop() }; runCatching { it.release() } }
            runCatching { socket?.close() }; runCatching { server?.close() }
        }
    }
    override fun close() {
        active.set(false)
        runCatching { socket?.close() }; runCatching { server?.close() }; worker.interrupt()
    }
}
