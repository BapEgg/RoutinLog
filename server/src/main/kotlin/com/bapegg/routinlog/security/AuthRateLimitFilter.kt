package com.bapegg.routinlog.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Bounded single-instance protection. A public deployment should also limit traffic at its trusted proxy. */
class AuthRateLimitFilter : OncePerRequestFilter() {
    private class Window(val started: Long, var requests: Int = 0)
    private val windows = ConcurrentHashMap<String, Window>()
    private val lastCleanup = AtomicLong(0)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !(
        // Tomcat's servletPath is decoded; raw requestURI retains aliases such as /auth/%67oogle.
        request.method == "POST" && request.servletPath in setOf(
            "/api/v1/auth/google/challenge", "/api/v1/auth/google", "/api/v1/auth/refresh",
        ) || request.method == "DELETE" && request.servletPath == "/api/v1/me"
    )

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val now = System.nanoTime()
        val previous = lastCleanup.get()
        if (now - previous > WINDOW && lastCleanup.compareAndSet(previous, now)) {
            windows.entries.removeIf { now - it.value.started >= WINDOW }
        }
        // Do not trust client-controlled forwarded headers for a rate-limit identity.
        val key = request.remoteAddr
        if (windows.size >= 10_000 && !windows.containsKey(key)) { reject(response); return }
        var allowed = false
        windows.compute(key) { _, previousWindow ->
            val current = if (previousWindow == null || now - previousWindow.started >= WINDOW) Window(now) else previousWindow
            current.requests++
            allowed = current.requests <= 60
            current
        }
        if (allowed) chain.doFilter(request, response) else reject(response)
    }

    private fun reject(response: HttpServletResponse) {
        response.setHeader("Retry-After", "60")
        SecurityErrorWriter.write(response, 429, "AUTH_RATE_LIMITED", "요청이 많아요. 잠시 후 다시 시도해 주세요.")
    }

    private companion object { const val WINDOW = 60_000_000_000L }
}
