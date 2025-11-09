package org.elly.app.services

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.elly.app.clients.OpenBankConsentsAdapter
import org.elly.app.web.dto.CreateReserveAccountReq
import org.elly.app.web.dto.ReserveAccountResp
import org.elly.core.model.BankCode
import org.elly.core.model.ClientId
import org.elly.core.model.UserId
import org.springframework.stereotype.Component

interface AccountService {
    suspend fun createReserveAccount(userId: UserId, req: CreateReserveAccountReq): ReserveAccountResp

}

@Component
class AccountServiceImpl(
    private val consentsAdapter: OpenBankConsentsAdapter
) : AccountService {
    override suspend fun createReserveAccount(userId: UserId, req: CreateReserveAccountReq): ReserveAccountResp {
//        consentsAdapter.ensurePaymentsConsent(BankCode("vbank"), ClientId("team205"), userId)
        return ReserveAccountResp(
            id = userId.value,
            bank = "vbank",
            percentOfSalary = req.percentOfSalary,
            obligationsTotalMinor = req.payments.sumOf { it.amountRub }.toLong(),
            createdAt = Clock.System.now().toString(),
            updatedAt = Clock.System.now().toString()
        )
    }
}
