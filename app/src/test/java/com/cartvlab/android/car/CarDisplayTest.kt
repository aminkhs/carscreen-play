package com.cartvlab.android.car

import org.junit.Assert.assertEquals
import org.junit.Test

class CarDisplayTest {
    @Test fun roundTripsAllFields() {
        val d = CarDisplay(CarLayout.RIGHT, 60, CarFit.CROP, browserBeside = true, buttonsHidden = true)
        assertEquals(d, CarDisplay.decode(d.encode()))
    }
    @Test fun badOrMissingValuesFallBackToDefaults() {
        assertEquals(CarDisplay(), CarDisplay.decode(null))
        assertEquals(CarDisplay(), CarDisplay.decode("layout=SIDEWAYS;panel=999;fit=?;junk"))
    }
    @Test fun cyclesWrapAround() {
        assertEquals(CarLayout.FULL, CarLayout.entries.after(CarLayout.LEFT))
        assertEquals(CarFit.CROP, CarFit.entries.after(CarFit.FIT))
        assertEquals(40, CarDisplay.PANEL_SIZES.after(70))
    }
}
