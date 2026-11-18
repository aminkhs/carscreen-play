package com.cartvlab.mirroring

import com.cartvlab.core.common.MirrorProtocol
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

interface ReceiverTransport : AutoCloseable {
    val queued: Int
    fun send(packet: MirrorProtocol.Packet): Boolean
}
class TcpReceiverTransport(host: String, token: String, private val failure: (String) -> Unit) : ReceiverTransport {
    private val queue = ArrayBlockingQueue<MirrorProtocol.Packet>(16)
    private val open = AtomicBoolean(true)
    private var socket: Socket? = null
    private val worker = thread(name = "mirror-transport", isDaemon = true) {
        try {
            val address = InetAddress.getByName(host)
            require(address.isSiteLocalAddress || address.isLoopbackAddress) { "Only private LAN receivers are supported" }
            val connected = Socket()
            socket = connected
            connected.connect(InetSocketAddress(address, MirrorProtocol.PORT), 4000)
            connected.tcpNoDelay = true
            connected.soTimeout = 4000
            val out = DataOutputStream(connected.getOutputStream())
            out.writeInt(MirrorProtocol.MAGIC); out.writeUTF(token); out.flush()
            require(connected.getInputStream().read() == 1) { "Pairing rejected" }
            while (open.get()) MirrorProtocol.write(out, queue.take())
        } catch (_: InterruptedException) { }
        catch (_: Exception) { if (open.get()) failure("Receiver connection failed. Verify LAN address, pairing code and receiver screen.") }
        finally { runCatching { socket?.close() }; open.set(false) }
    }
    override val queued get() = queue.size
    override fun send(packet: MirrorProtocol.Packet): Boolean = open.get() && queue.offer(packet)
    override fun close() { open.set(false); runCatching { socket?.close() }; worker.interrupt(); queue.clear() }
}
