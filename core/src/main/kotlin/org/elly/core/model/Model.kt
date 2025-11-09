package org.elly.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@JvmInline value class UserId(val value: Long)
@JvmInline value class BankCode(val value: String)      // "vbank","abank","sbank"
@JvmInline value class ClientId(val value: String)      // team200-1
@JvmInline value class AccountId(val value: String)     // 40817810...
@JvmInline value class ConsentId(val value: String)
@JvmInline value class PaymentId(val value: String)

data class Money(val amount: Long, val currency: String = "RUB") { // amount в копейках
    companion object { fun rub(rubles: Double) = Money((rubles * 100).toLong()) }
}

enum class ObligationType { LOAN, UTILS, SUBSCRIPTION }

data class BankLink(
    val userId: UserId,
    val bank: BankCode,
    val clientId: ClientId,
    val accessToken: String,
    val expiresAt: LocalDateTime
)

data class AccountsConsent(
    val userId: UserId,
    val bank: BankCode,
    val clientId: ClientId,
    val consentId: ConsentId,
    val status: String,
    val createdAt: Instant = Clock.System.now()
)

data class AccountRef(
    val bank: BankCode,
    val accountId: AccountId,
    val nickname: String? = null,
    val isReserve: Boolean = false,
    val isSalarySource: Boolean = false
)

data class Obligation(
    val id: String,
    val userId: Long,
    val source: String,
    val merchantKey: String,
    val title: String,
    val category: String,
    val currency: String,
    val avgAmountMinor: Long,
    val periodicity: String,   // MONTHLY|WEEKLY
    val typicalDay: Int?,
    val nextDueDate: LocalDate, // ← kotlinx.datetime
    val repeats: Int,
    val confidence: Double,
    val createdAt: Instant      // ← kotlinx.datetime
)

enum class RunStatus { PENDING, DUE, PROCESSING, DONE, FAILED }

data class PaymentRun(
    val id: Long?,
    val obligationId: Long,
    val scheduledAt: LocalDateTime,
    val amount: Money,
    val status: RunStatus,
    val paymentId: PaymentId? = null,
    val failReason: String? = null
)

data class TxRecord(
    val account: AccountRef,
    val bookingAt: LocalDateTime,
    val amount: Money,            // входящий >0, исходящий <0
    val description: String?,
    val counterpartyAccount: String?
)

/** Ответ на POST /auth/bank-token */
data class BankTokenResp(
    val access_token: String,
    val expires_in: Long = 86_400
)

/** Тело запроса на создание account-consent */
data class ConsentRequestBody(
    val client_id: String,
    val permissions: List<String>,
    val reason: String = "",
    val requesting_bank: String,
    val requesting_bank_name: String = "Ellie App"
)

/** Обёртка ответа на account-consents */
data class ConsentResponse(val data: ConsentData)

/** Вложенные данные согласия */
data class ConsentData(
    val consentId: String,
    val status: String
)
