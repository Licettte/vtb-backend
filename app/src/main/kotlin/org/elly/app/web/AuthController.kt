package org.elly.app.web

import org.elly.app.services.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService) {

    data class RegisterReq(val email: String, val password: String)
    data class LoginReq(val email: String, val password: String)
    data class RefreshReq(val refreshToken: String)

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun register(@RequestBody req: RegisterReq) = auth.register(req.email, req.password)

    @PostMapping("/login")
    suspend fun login(@RequestBody req: LoginReq) = auth.login(req.email, req.password)

    @PostMapping("/refresh")
    suspend fun refresh(@RequestBody req: RefreshReq) = auth.refresh(req.refreshToken)
}
