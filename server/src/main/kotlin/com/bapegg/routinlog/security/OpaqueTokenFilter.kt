package com.bapegg.routinlog.security

import com.bapegg.routinlog.auth.AuthSessionService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class OpaqueTokenFilter(private val sessions: AuthSessionService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val values = request.getHeaders(HttpHeaders.AUTHORIZATION).toList()
        if (values.isEmpty()) { chain.doFilter(request, response); return }
        val header = values.singleOrNull()
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true) || header.length > 128) {
            SecurityErrorWriter.write(response, 401, "AUTH_INVALID", "다시 로그인해 주세요.")
            return
        }
        val user = try { sessions.authenticate(header.substring(7)) } catch (_: DataAccessException) {
            SecurityErrorWriter.write(response, 503, "SERVICE_UNAVAILABLE", "잠시 후 다시 시도해 주세요.")
            return
        }
        if (user == null) {
            SecurityErrorWriter.write(response, 401, "AUTH_INVALID", "다시 로그인해 주세요.")
            return
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, emptyList())
        SecurityContextHolder.setContext(context)
        chain.doFilter(request, response)
    }
}

internal object SecurityErrorWriter {
    fun write(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        // Both values are application constants; never include a token or client input.
        response.writer.write("""{"code":"$code","message":"$message"}""")
    }
}
