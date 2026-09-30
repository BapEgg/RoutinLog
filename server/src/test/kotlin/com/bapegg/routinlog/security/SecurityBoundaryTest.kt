package com.bapegg.routinlog.security

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.blankOrNullString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBoundaryTest @Autowired constructor(
    private val mvc: MockMvc,
    private val context: ApplicationContext,
) {
    @Test
    fun `health returns status without component details`() {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"status":"UP"}"""))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `system status only exposes service readiness and build version`() {
        val response = mvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.service").value("routinlog"))
            .andExpect(jsonPath("$.status").value("ready"))
            .andExpect(jsonPath("$.version", not(blankOrNullString())))
            .andExpect(jsonPath("$.authentication").doesNotExist())
            .andExpect(jsonPath("$.database").doesNotExist())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andReturn()
        assertNull(response.request.getSession(false))
    }

    @Test
    fun `all non-public routes are denied without creating a login session`() {
        listOf("/api/v1/body-measurements", "/api/v1/future-feature", "/actuator", "/actuator/env", "/actuator/health/db", "/login", "/error")
            .forEach { path ->
                val result = mvc.perform(get(path))
                    .andExpect(status().isUnauthorized)
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andReturn()
                assertNull(result.request.getSession(false))
            }
    }

    @Test
    fun `public path names do not permit other request methods`() {
        listOf("/actuator/health", "/api/v1/system/status").forEach { path ->
            mvc.perform(post(path)).andExpect(status().isUnauthorized)
            mvc.perform(head(path)).andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `unverified tokens never open record routes`() {
        mvc.perform(get("/api/v1/body-measurements").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `even a test-authenticated identity cannot bypass the closed API boundary`() {
        mvc.perform(get("/api/v1/body-measurements").with(user("test-only-principal").roles("ADMIN")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `no default password user or login page is installed`() {
        assertTrue(context.getBeansOfType(UserDetailsService::class.java).isEmpty())
        mvc.perform(get("/login"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().string(not(containsString("<form"))))
    }
}
