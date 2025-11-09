package org.elly.app.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtService(
    @Value("\${security.jwt.secret}") secret: String,
    @Value("\${security.jwt.issuer}") private val issuer: String,
    @Value("\${security.jwt.access-ttl-min}") private val accessTtlMin: Long,
    @Value("\${security.jwt.refresh-ttl-days}") private val refreshTtlDays: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createAccess(userId: Long, email: String, roles: String): String {
        val now = Instant.now()
        val exp = now.plusSeconds(accessTtlMin * 60)
        return Jwts.builder()
            .setIssuer(issuer)
            .setSubject(userId.toString())
            .setAudience("access")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .claim("email", email)
            .claim("roles", roles)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun createRefresh(userId: Long): String {
        val now = Instant.now()
        val exp = now.plusSeconds(refreshTtlDays * 24 * 3600)
        return Jwts.builder()
            .setIssuer(issuer)
            .setSubject(userId.toString())
            .setAudience("refresh")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    data class Parsed(val userId: Long, val email: String?, val roles: String?, val aud: String)

    fun parse(token: String): Parsed {
        val jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
        val claims = jws.body
        return Parsed(
            userId = claims.subject.toLong(),
            email = claims["email"] as String?,
            roles = claims["roles"] as String?,
            aud = claims.audience
        )
    }
}
