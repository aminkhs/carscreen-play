package com.cartvlab.core.model

enum class CapabilityStatus {
    SUPPORTED_AND_TESTED, POTENTIALLY_SUPPORTED, NOT_SUPPORTED_BY_CURRENT_PUBLIC_API, UNKNOWN, TEST_FAILED
}
data class Capability(val name: String, val status: CapabilityStatus, val evidence: String)
data class Settings(
    val theme: String = "Dark",
    val quality: Int = 1080,
    val mirrorHeight: Int = 720,
    val mirrorFps: Int = 30,
    val hardware: Boolean = true,
    val subtitleLanguage: String = "fa",
    val subtitleSize: Float = 1f,
    val subtitleEncoding: String = "UTF-8",
    val debug: Boolean = false,
    val car: String = "Not set",
    val testSource: String = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
)
data class TestResult(val timestamp: String, val test: String, val outcome: String, val evidence: String, val origin: String)
data class RenderStats(val frames: Long = 0, val fps: Double = 0.0, val width: Int = 0, val height: Int = 0)
data class CaptureState(
    val active: Boolean = false, val frames: Long = 0, val width: Int = 0, val height: Int = 0,
    val fps: Double = 0.0, val bitrate: Int = 0, val encoder: String = "",
    val transport: String = "Local preview", val error: String? = null
)
