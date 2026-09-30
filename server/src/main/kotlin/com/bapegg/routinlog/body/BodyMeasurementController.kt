package com.bapegg.routinlog.body

import com.bapegg.routinlog.profile.principalUserId
import com.bapegg.routinlog.security.AuthenticatedUser
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/body-measurements")
class BodyMeasurementController(private val records: BodyMeasurementService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthenticatedUser?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<BodyMeasurementList> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(records.list(principalUserId(principal), from, to))

    @PutMapping("/{date}")
    fun put(
        @AuthenticationPrincipal principal: AuthenticatedUser?,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestBody request: PutBodyMeasurement,
    ): ResponseEntity<BodyMeasurementDto> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(records.put(principalUserId(principal), date, request))

    @DeleteMapping("/{date}")
    fun delete(
        @AuthenticationPrincipal principal: AuthenticatedUser?,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam version: Long,
    ): ResponseEntity<Void> {
        records.delete(principalUserId(principal), date, version)
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }
}
