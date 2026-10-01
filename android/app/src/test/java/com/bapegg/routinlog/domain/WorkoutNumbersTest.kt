package com.bapegg.routinlog.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class WorkoutNumbersTest {
    @Test fun `metric conversion preserves kg and uses three-place half-up rounding`() {
        assertEquals(d("80.000"), WorkoutNumbers.kgToDisplay(d("80"), "METRIC"))
        assertEquals(d("80.124"), WorkoutNumbers.displayToKg(d("80.1235"), "METRIC"))
        assertEquals(d("80.123"), WorkoutNumbers.displayToKg(d("80.1234"), "METRIC"))
    }

    @Test fun `pounds convert through exact international pound definition`() {
        assertEquals(d("45.359"), WorkoutNumbers.displayToKg(d("100"), "IMPERIAL"))
        assertEquals(d("81.647"), WorkoutNumbers.displayToKg(d("180"), "IMPERIAL"))
        assertEquals(d("220.462"), WorkoutNumbers.kgToDisplay(d("100"), "IMPERIAL"))
        assertEquals(d("1.000"), WorkoutNumbers.kgToDisplay(d("0.45359237"), "IMPERIAL"))
    }

    @Test fun `zero external load is known zero rather than a missing value`() {
        for (units in listOf("METRIC", "IMPERIAL")) {
            assertEquals(d("0.000"), WorkoutNumbers.displayToKg(BigDecimal.ZERO, units))
            assertEquals(d("0.000"), WorkoutNumbers.kgToDisplay(BigDecimal.ZERO, units))
        }
    }

    @Test fun `canonical three-decimal weights survive kg lb display round trip`() {
        for (value in listOf("0.001", "2.500", "16.125", "80.123", "100.000", "2000.000")) {
            val kg = d(value)
            assertEquals(kg, WorkoutNumbers.displayToKg(WorkoutNumbers.kgToDisplay(kg, "IMPERIAL"), "IMPERIAL"))
        }
    }

    @Test fun `conversion does not turn invalid negative or unknown units into valid loads`() {
        assertInvalid { WorkoutNumbers.kgToDisplay(d("-1"), "METRIC") }
        assertInvalid { WorkoutNumbers.displayToKg(d("-1"), "IMPERIAL") }
        assertInvalid { WorkoutNumbers.kgToDisplay(d("10"), "lb") }
        assertInvalid { WorkoutNumbers.displayToKg(d("10"), "UNKNOWN") }
    }

    private fun d(value: String) = BigDecimal(value)
    private fun assertInvalid(block: () -> Any?) {
        try { block(); fail("Expected invalid unit conversion") } catch (_: IllegalArgumentException) { }
    }
}
