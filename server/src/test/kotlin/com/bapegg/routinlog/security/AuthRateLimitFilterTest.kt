package com.bapegg.routinlog.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class AuthRateLimitFilterTest {
    @Test
    fun `auth traffic is bounded by direct source IP and forwarded headers do not bypass it`() {
        val filter = AuthRateLimitFilter()
        var accepted = 0
        val chain = FilterChain { _, _ -> accepted++ }
        repeat(60) { index ->
            val request = MockHttpServletRequest("POST", "/api/v1/auth/google/challenge").apply {
                remoteAddr = "203.0.113.8"
                servletPath = "/api/v1/auth/google/challenge"
                addHeader("X-Forwarded-For", "198.51.100.$index")
            }
            filter.doFilter(request, MockHttpServletResponse(), chain)
        }
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("POST", "/api/v1/auth/google/challenge").apply {
            remoteAddr = "203.0.113.8"; servletPath = "/api/v1/auth/google/challenge"
        }, response, chain)
        assertEquals(60, accepted)
        assertEquals(429, response.status)
        assertEquals("60", response.getHeader("Retry-After"))
        assertEquals("no-store", response.getHeader("Cache-Control"))
        filter.doFilter(MockHttpServletRequest("GET", "/actuator/health").apply { remoteAddr = "203.0.113.8" }, MockHttpServletResponse(), chain)
        assertEquals(61, accepted)
    }

    @Test
    fun `encoded authentication paths share the decoded route limit`() {
        val filter = AuthRateLimitFilter()
        var accepted = 0
        val chain = FilterChain { _, _ -> accepted++ }
        repeat(60) {
            filter.doFilter(MockHttpServletRequest("POST", "/api/v1/auth/google").apply {
                remoteAddr = "203.0.113.9"; servletPath = "/api/v1/auth/google"
            }, MockHttpServletResponse(), chain)
        }
        val encodedResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("POST", "/api/v1/auth/%67oogle").apply {
            remoteAddr = "203.0.113.9"; servletPath = "/api/v1/auth/google"
        }, encodedResponse, chain)
        assertEquals(60, accepted)
        assertEquals(429, encodedResponse.status)
        val deletionResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("DELETE", "/api/v1/%6de").apply {
            remoteAddr = "203.0.113.9"; servletPath = "/api/v1/me"
        }, deletionResponse, chain)
        assertEquals(429, deletionResponse.status)
    }
}
