package com.bapegg.routinlog.body

import java.math.BigDecimal
import java.time.LocalDate

data class PutBodyMeasurement(val weightKg: BigDecimal? = null, val waistCm: BigDecimal? = null, val version: Long? = null, val memo: String? = null)
data class BodyMeasurementDto(val date: LocalDate, val weightKg: BigDecimal?, val waistCm: BigDecimal?, val version: Long, val memo: String? = null)
data class BodyMeasurementList(val items: List<BodyMeasurementDto>)
