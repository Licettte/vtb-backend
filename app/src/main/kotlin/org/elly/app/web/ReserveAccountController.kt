package org.elly.app.web

import org.elly.app.services.AccountService
import org.elly.app.web.dto.CreateReserveAccountReq
import org.elly.app.web.dto.ReserveAccountResp
import org.elly.core.model.UserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/accounts/reserve")
class ReserveAccountController(
    private val accountService: AccountService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createReserveAccount(
        @RequestBody req: CreateReserveAccountReq
    ): ReserveAccountResp {
        return accountService.createReserveAccount(
            userId = UserId(currentUserId()),
            req = req
        )
    }
}
