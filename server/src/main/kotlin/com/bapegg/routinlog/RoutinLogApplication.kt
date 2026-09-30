package com.bapegg.routinlog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

// No generated password or development login while real Google authentication is pending.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class RoutinLogApplication

fun main(args: Array<String>) {
	runApplication<RoutinLogApplication>(*args)
}
