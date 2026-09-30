package com.bapegg.routinlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BodyMeasurementInputTest {
    private val date = LocalDate.of(2026, 9, 30)

    @Test fun `missing measurement is not converted to zero`() {
        val result = BodyMeasurementInput.validate(date, "83.2", "")
        assertEquals(BigDecimal("83.2"), result.measurement?.weightKg)
        assertNull(result.measurement?.waistCm)
    }

    @Test fun `empty form cannot create a record`() {
        val result = BodyMeasurementInput.validate(date, " ", "")
        assertNull(result.measurement)
        assertNotNull(result.formError)
    }

    @Test fun `invalid numeric forms are rejected instead of coerced`() {
        for (value in listOf("0", "-1", "NaN", "Infinity", "1e3", "83.25", "83,2.1")) {
            val result = BodyMeasurementInput.validate(date, value, "80")
            assertNull("$value must not create a record", result.measurement)
            assertNotNull("$value must have a field error", result.weightError)
        }
    }

    @Test fun `decimal comma and the given record date are preserved correctly`() {
        val result = BodyMeasurementInput.validate(date, "", "80,1")
        assertEquals(BigDecimal("80.1"), result.measurement?.waistCm)
        assertEquals(date, result.measurement?.date)
        assertNull(result.measurement?.weightKg)
    }

    @Test fun `server storage limits are inclusive`() {
        val result = BodyMeasurementInput.validate(date, "1000.0", "500.0")
        assertEquals(BigDecimal("1000.0"), result.measurement?.weightKg)
        assertEquals(BigDecimal("500.0"), result.measurement?.waistCm)
        assertNull(result.weightError)
        assertNull(result.waistError)
    }

    @Test fun `weight just above storage limit is rejected`() {
        val result = BodyMeasurementInput.validate(date, "1000.1", "80")
        assertNull(result.measurement)
        assertNotNull(result.weightError)
        assertNull(result.waistError)
    }

    @Test fun `waist just above storage limit is rejected`() {
        val result = BodyMeasurementInput.validate(date, "83.2", "500.1")
        assertNull(result.measurement)
        assertNull(result.weightError)
        assertNotNull(result.waistError)
    }
}
