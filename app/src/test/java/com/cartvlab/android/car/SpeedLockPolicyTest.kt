package com.cartvlab.android.car

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedLockPolicyTest {
    private val policy = SpeedLockPolicy()
    private fun at(ms: Long, kmh: Float?, permission: Boolean = true, locationOn: Boolean = true) =
        policy.evaluate(ms, permission, locationOn, kmh)

    @Test fun allowsVideoBeforeFirstReadingAndAtOrBelowLimit() {
        assertEquals(LockState.CLEAR, at(0, null))
        assertEquals(LockState.CLEAR, at(1_000, 0f))
        assertEquals(LockState.CLEAR, at(2_000, 30f)) // exactly 30 km/h is allowed
    }

    @Test fun hidesVideoAboveLimit() {
        assertEquals(LockState.MOVING, at(0, 30.5f))
    }

    @Test fun videoReturnsOnlyAfterTenSecondsAtOrBelowLimit() {
        at(0, 50f)
        assertEquals(LockState.MOVING, at(1_000, 10f))
        assertEquals(LockState.MOVING, at(9_999, 10f))
        assertEquals(LockState.CLEAR, at(10_000, 10f))
    }

    @Test fun anotherFastReadingRestartsTheTimer() {
        at(0, 50f)
        at(8_000, 31f)
        assertEquals(LockState.MOVING, at(17_000, 0f))
        assertEquals(LockState.CLEAR, at(18_000, 0f))
    }

    @Test fun staleFastReadingKeepsVideoHidden() {
        // GPS lost in a tunnel: the last reading (80 km/h) stays the latest reading.
        at(0, 80f)
        assertEquals(LockState.MOVING, at(60_000, 80f))
    }

    @Test fun failsClosedWithoutPermissionOrLocation() {
        assertEquals(LockState.NEED_PERMISSION, at(0, 0f, permission = false))
        assertEquals(LockState.LOCATION_OFF, at(0, 0f, locationOn = false))
    }

    @Test fun resetClearsState() {
        at(0, 90f)
        policy.reset()
        assertEquals(LockState.CLEAR, at(1_000, 0f))
    }
}
