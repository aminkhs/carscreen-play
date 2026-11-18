package com.cartvlab.automotive
import com.cartvlab.core.model.CapabilityStatus
import org.junit.Assert.*
import org.junit.Test
class CapabilityPolicyTest {
    @Test fun flagAloneNeverUnlocksCarLaunch() {
        assertFalse(ParkedVideoExperiment.canLaunch(true))
        assertFalse(ParkedVideoExperiment.canLaunch(true, true, false))
        assertFalse(ParkedVideoExperiment.canLaunch(false, true, true))
    }
    @Test fun noVehicleCapabilityStartsVerified() {
        val capabilities = PublicAndroidAutoCapabilityRepository().capabilities()
        assertFalse(capabilities.any { it.status == CapabilityStatus.SUPPORTED_AND_TESTED })
        // Car features are implemented but must be verified in a real car before they count as tested.
        assertEquals("Android Auto video screen", capabilities.first().name)
        assertEquals(CapabilityStatus.POTENTIALLY_SUPPORTED, capabilities.first().status)
    }
    @Test fun connectionIsUnknown() { assertEquals("UNKNOWN", UnknownCarConnectionDiagnostics().observableConnection()) }
}
