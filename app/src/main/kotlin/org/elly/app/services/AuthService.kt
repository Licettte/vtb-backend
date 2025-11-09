package org.elly.app.services

import org.elly.app.security.JwtService
import org.elly.storage.repos.UsersRepo
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val users: UsersRepo,
    private val jwt: JwtService
) {
    private val encoder = BCryptPasswordEncoder()

    suspend fun register(email: String, password: String): Tokens {
        val existing = users.findByEmail(email)
        require(existing == null) { "Email already registered" }
        val user = users.create(email, encoder.encode(password))
        return Tokens(jwt.createAccess(user.id, user.email, user.roles), jwt.createRefresh(user.id))
    }

    suspend fun login(email: String, password: String): Tokens {
        val user = users.findByEmail(email) ?: error("Invalid credentials")
        require(encoder.matches(password, user.passwordHash)) { "Invalid credentials" }
        return Tokens(jwt.createAccess(user.id, user.email, user.roles), jwt.createRefresh(user.id))
    }

    suspend fun refresh(refreshToken: String): Tokens {
        val parsed = jwt.parse(refreshToken)
        require(parsed.aud == "refresh") { "Invalid token audience" }
        // email/roles можно подтянуть из БД при желании
        val user = users.findByEmail(parsed.email ?: "")
        return Tokens(jwt.createAccess(parsed.userId, user?.email ?: "", user?.roles ?: "ROLE_USER"),
            jwt.createRefresh(parsed.userId))
    }

    data class Tokens(val accessToken: String, val refreshToken: String)
}
