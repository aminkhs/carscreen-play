package com.cartvlab.automotive

import com.cartvlab.core.model.*

interface AndroidAutoCapabilityRepository { fun capabilities(): List<Capability> }
class PublicAndroidAutoCapabilityRepository : AndroidAutoCapabilityRepository {
    override fun capabilities() = listOf(
        Capability("Android Auto video screen", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Car App Library navigation-template surface (personal / sideloaded use). Android Auto 17+ shows it only when the app is recorded as installed by Google Play; check Android Auto Setup."),
        Capability("Android Auto media entry", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Media3 MediaLibraryService. Shown with Android Auto Unknown sources; its card next to the map shows sound, a still cover frame and controls (no moving video)."),
        Capability("Driving lock", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Phone GPS (and car speed sensor when available). Above 30 km/h the car screen becomes audio only. Always on in the public build; optional in the personal build."),
        Capability("Vehicle connection", CapabilityStatus.UNKNOWN,
            "This screen has no authoritative head-unit connection signal. Package presence and displays are not connection evidence."),
        Capability("Wired / wireless support", CapabilityStatus.UNKNOWN,
            "Depends on the phone, Android Auto version and head unit. Test each mode physically."),
        Capability("Phone video playback", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Media3 player is implemented; codec, stream and rendering results must be measured on this device."),
        Capability("MediaProjection capture", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Requires fresh system consent, foreground service and an unprotected capture surface."),
        Capability("External receiver", CapabilityStatus.POTENTIALLY_SUPPORTED,
            "Requires the separate Lab Receiver Android app, pairing code and a trusted private LAN. Not Android Auto.")
    )
}
object ParkedVideoExperiment {
    const val MESSAGE = "Android Auto video playback is not currently available through the verified public integration on this device."
    /** A feature flag cannot override category eligibility or missing motion restrictions. */
    fun canLaunch(featureFlag: Boolean, officialPathVerified: Boolean = false, parkedVerified: Boolean = false): Boolean =
        featureFlag && officialPathVerified && parkedVerified
}
