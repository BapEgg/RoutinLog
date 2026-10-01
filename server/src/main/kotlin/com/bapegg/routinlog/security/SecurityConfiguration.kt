package com.bapegg.routinlog.security

import com.bapegg.routinlog.auth.AuthSessionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration(private val sessions: AuthSessionService) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/system/status").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/google/challenge", "/api/v1/auth/google", "/api/v1/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").access(::accountAccess)
                    .requestMatchers(HttpMethod.GET, "/api/v1/me/profile", "/api/v1/body-measurements").access(::accountAccess)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/me/profile", "/api/v1/body-measurements/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/me", "/api/v1/body-measurements/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.GET, "/api/v1/foods", "/api/v1/meal-templates", "/api/v1/meal-plan", "/api/v1/meal-records").access(::accountAccess)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/foods/*", "/api/v1/meal-templates/*", "/api/v1/meal-plan", "/api/v1/meal-records/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/foods/*", "/api/v1/meal-templates/*", "/api/v1/meal-records/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.GET, "/api/v1/workout-exercises", "/api/v1/workout-routines", "/api/v1/workout-plan", "/api/v1/workout-days", "/api/v1/workout-history").access(::accountAccess)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/workout-exercises/*", "/api/v1/workout-routines/*", "/api/v1/workout-plan", "/api/v1/workout-overrides/*", "/api/v1/workout-sessions/*", "/api/v1/workout-sessions/*/start").access(::accountAccess)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/workout-exercises/*", "/api/v1/workout-routines/*", "/api/v1/workout-overrides/*", "/api/v1/workout-sessions/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.GET, "/api/v1/conditions").access(::accountAccess)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/conditions/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/conditions/*").access(::accountAccess)
                    .requestMatchers(HttpMethod.GET, "/api/v1/step-connection", "/api/v1/steps").access(::accountAccess)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/step-connection", "/api/v1/step-connections/*/days").access(::accountAccess)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/step-connections/*").access(::accountAccess)
                    .anyRequest().denyAll()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Credentials are explicit Authorization headers; no cookie/session authentication is accepted.
            .csrf { it.disable() }
            .addFilterBefore(OpaqueTokenFilter(sessions), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(AuthRateLimitFilter(), OpaqueTokenFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> SecurityErrorWriter.write(response, 401, "AUTH_REQUIRED", "로그인이 필요해요.") }
                it.accessDeniedHandler { _, response, _ -> SecurityErrorWriter.write(response, 403, "ACCESS_DENIED", "이 요청을 처리할 수 없어요.") }
            }
        return http.build()
    }

    private fun accountAccess(
        authentication: java.util.function.Supplier<out org.springframework.security.core.Authentication>,
        @Suppress("UNUSED_PARAMETER") context: org.springframework.security.web.access.intercept.RequestAuthorizationContext,
    ): AuthorizationDecision {
        val identity = authentication.get()
        return AuthorizationDecision(identity.isAuthenticated && identity.principal is AuthenticatedUser)
    }
}
