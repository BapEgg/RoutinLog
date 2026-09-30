package com.bapegg.routinlog.domain

import java.math.BigDecimal
import java.time.LocalDate

data class BodyMeasurement(
    val date: LocalDate,
    val weightKg: BigDecimal?,
    val waistCm: BigDecimal?,
)

data class MeasurementInputResult(
    val measurement: BodyMeasurement? = null,
    val weightError: String? = null,
    val waistError: String? = null,
    val formError: String? = null,
)

object BodyMeasurementInput {
    private val decimal = Regex("^[0-9]{1,4}([.,][0-9])?$")
    // Match the server's technical storage limits; these are not medical recommendations.
    private val maxWeightKg = BigDecimal("1000")
    private val maxWaistCm = BigDecimal("500")

    fun validate(date: LocalDate, weight: String, waist: String): MeasurementInputResult {
        val weightText = weight.trim()
        val waistText = waist.trim()
        if (weightText.isEmpty() && waistText.isEmpty()) {
            return MeasurementInputResult(formError = "체중이나 허리둘레 중 하나를 입력해주세요.")
        }
        fun parse(value: String): BigDecimal? = if (decimal.matches(value)) {
            value.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        } else null
        val weightValue = parse(weightText)
        val waistValue = parse(waistText)
        val weightError = when {
            weightText.isNotEmpty() && weightValue == null -> "0보다 큰 숫자를 소수점 한 자리까지 입력해주세요."
            weightValue != null && weightValue > maxWeightKg -> "입력 가능한 최대 체중은 1,000kg이에요."
            else -> null
        }
        val waistError = when {
            waistText.isNotEmpty() && waistValue == null -> "0보다 큰 숫자를 소수점 한 자리까지 입력해주세요."
            waistValue != null && waistValue > maxWaistCm -> "입력 가능한 최대 허리둘레는 500cm예요."
            else -> null
        }
        return if (weightError != null || waistError != null) {
            MeasurementInputResult(weightError = weightError, waistError = waistError)
        } else {
            MeasurementInputResult(measurement = BodyMeasurement(date, weightValue, waistValue))
        }
    }
}
