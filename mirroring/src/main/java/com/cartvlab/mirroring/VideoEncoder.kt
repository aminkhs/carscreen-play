package com.cartvlab.mirroring

import android.media.*
import android.os.Bundle
import android.os.Handler
import android.view.Surface
import com.cartvlab.core.common.MirrorProtocol
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class VideoEncoder(
    width: Int, height: Int, fps: Int, hardware: Boolean, handler: Handler,
    private val transport: ReceiverTransport, private val frame: (Int, String) -> Unit,
    private val failure: () -> Unit
) : AutoCloseable {
    private val info = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", true) } }
        .sortedByDescending { if (hardware) it.isHardwareAccelerated else it.isSoftwareOnly }
        .firstOrNull { it.getCapabilitiesForType("video/avc").colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) }
        ?: error("No H.264 surface encoder is available")
    private val codec = MediaCodec.createByCodecName(info.name)
    private var bitrate = 2_500_000
    private var count = 0
    private var closed = false
    val surface: Surface
    init {
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                val bytes = ByteArrayOutputStream()
                DataOutputStream(bytes).use { out ->
                    out.writeInt(width); out.writeInt(height)
                    for (key in listOf("csd-0", "csd-1")) {
                        val buffer = format.getByteBuffer(key)?.duplicate()
                        val data = if (buffer != null) ByteArray(buffer.remaining()).also { buffer.get(it) } else byteArrayOf()
                        out.writeInt(data.size); out.write(data)
                    }
                }
                if (!transport.send(MirrorProtocol.Packet(MirrorProtocol.FORMAT, 0, 0, bytes.toByteArray()))) failure()
            }
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, metadata: MediaCodec.BufferInfo) {
                if (closed) return
                try {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && metadata.size > 0 && metadata.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        buffer.position(metadata.offset); buffer.limit(metadata.offset + metadata.size)
                        val bytes = ByteArray(metadata.size).also { buffer.get(it) }
                        if (!transport.send(MirrorProtocol.Packet(MirrorProtocol.FRAME, metadata.flags, metadata.presentationTimeUs, bytes))) {
                            // A reliable stream must not silently lose reference frames.
                            failure()
                        }
                        count++
                        if (count % (fps * 2) == 0) {
                            bitrate = (if (transport.queued > 4) bitrate * 3 / 4 else bitrate + 150_000).coerceIn(500_000, 4_000_000)
                            codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate) })
                        }
                        frame(bitrate, info.name)
                    }
                } finally { runCatching { codec.releaseOutputBuffer(index, false) } }
            }
            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) { failure() }
        }, handler)
        try {
        codec.configure(MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
        } catch (error: Exception) { runCatching { codec.release() }; throw error }
    }
    override fun close() {
        if (closed) return
        closed = true
        runCatching { codec.stop() }; runCatching { codec.release() }; surface.release()
    }
}
