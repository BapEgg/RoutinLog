package com.bapegg.routinlog.system

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SystemStatusController(
    @Value("\${spring.application.name}") private val serviceName: String,
    private val buildProperties: ObjectProvider<BuildProperties>,
) {
    @GetMapping("/api/v1/system/status")
    fun status(): ServiceStatus = ServiceStatus(
        service = serviceName,
        status = "ready",
        version = buildProperties.ifAvailable?.version ?: "development",
    )
}

data class ServiceStatus(val service: String, val status: String, val version: String)
