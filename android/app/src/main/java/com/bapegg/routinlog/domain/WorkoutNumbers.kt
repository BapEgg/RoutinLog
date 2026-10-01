package com.bapegg.routinlog.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure display conversion. One international avoirdupois pound is exactly 0.45359237 kg. */
object WorkoutNumbers {
    private val kilogramsPerPound = BigDecimal("0.45359237")

    fun kgToDisplay(value: BigDecimal, units: String): BigDecimal {
        require(value.signum() >= 0) { "Weight cannot be negative" }
        return when (units) {
            "METRIC" -> value.setScale(3, RoundingMode.HALF_UP)
            "IMPERIAL" -> value.divide(kilogramsPerPound, 3, RoundingMode.HALF_UP)
            else -> throw IllegalArgumentException("Unknown measurement units")
        }
    }

    fun displayToKg(value: BigDecimal, units: String): BigDecimal {
        require(value.signum() >= 0) { "Weight cannot be negative" }
        return when (units) {
            "METRIC" -> value.setScale(3, RoundingMode.HALF_UP)
            "IMPERIAL" -> value.multiply(kilogramsPerPound).setScale(3, RoundingMode.HALF_UP)
            else -> throw IllegalArgumentException("Unknown measurement units")
        }
    }
}
