package com.bapegg.routinlog.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** No body, query string, token, email or IP is written. Production log storage must remain private. */
class AccessAuditFilter:OncePerRequestFilter() {
    private val log=LoggerFactory.getLogger("routinlog.access")
    override fun doFilterInternal(request:HttpServletRequest,response:HttpServletResponse,chain:FilterChain) {
        val owner=(SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)?.userId ?: return chain.doFilter(request,response)
        val started=System.nanoTime()
        try { chain.doFilter(request,response) }finally {
            val resource=when {
                request.requestURI=="/api/v1/features/export"->"record-export"
                request.requestURI=="/api/v1/me"->"account"
                request.requestURI.startsWith("/api/v1/features/photos/")->"thumbnail"
                request.requestURI.startsWith("/api/v1/features/")->"features"
                else->"records"
            }
            log.info("account={} resource={} method={} status={} durationMs={}",owner,resource,request.method,response.status,(System.nanoTime()-started)/1_000_000)
        }
    }
}
