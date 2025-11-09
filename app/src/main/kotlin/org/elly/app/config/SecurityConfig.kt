package org.elly.app.config

import org.elly.app.security.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwt: JwtService
) {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.csrf { it.disable() }
            .cors { }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.pathMatchers("/actuator/**", "/health").permitAll()
                it.pathMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                it.pathMatchers(HttpMethod.GET, "/api/onboarding/*/events").permitAll() // <-- SSE
                it.anyExchange().authenticated()
            }
            // You can keep/remove the manager; it's a no-op here.
            .authenticationManager { auth -> Mono.just(auth) }
            .addFilterAt(
                WebFilter { exchange, chain ->
                    val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                    if (authHeader?.startsWith("Bearer ") == true) {
                        val token = authHeader.removePrefix("Bearer ").trim()
                        val parsed = runCatching { jwt.parse(token) }.getOrNull()
                        if (parsed != null && parsed.aud == "access") {
                            val authorities: List<GrantedAuthority> =
                                (parsed.roles ?: "ROLE_USER")
                                    .split(",")
                                    .asSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { SimpleGrantedAuthority(it) as GrantedAuthority }
                                    .toList()

                            val authentication = object : AbstractAuthenticationToken(authorities) {
                                override fun getCredentials(): Any = token
                                override fun getPrincipal(): Any = parsed.userId
                            }.apply { isAuthenticated = true }

                            // Put auth into the Reactor context and continue the chain.
                            return@WebFilter chain.filter(exchange)
                                .contextWrite(
                                    ReactiveSecurityContextHolder.withAuthentication(authentication)
                                )
                        }
                    }
                    chain.filter(exchange)
                },
                SecurityWebFiltersOrder.AUTHENTICATION
            )
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration().apply {
            // Явно перечисляем фронтовые origin'ы (для credentials это обязательно)
            allowedOriginPatterns = listOf(
                "http://localhost:*",     // локалхост на любых портах (5173, 3000, и т.д.)
                "http://127.0.0.1:*",
                "http://192.168.*.*:*",   // любая локальная подсеть 192.168.x.y на любом порту
                "http://10.*.*.*:*"       // частная сеть 10/8
                // при желании добавь: "http://*.local:*"
            )
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With")
            exposedHeaders = listOf("Authorization") // если читаешь токен из ответа
            allowCredentials = true                  // т.к. включаем креды/cookie
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", cfg)
        }
    }
}
