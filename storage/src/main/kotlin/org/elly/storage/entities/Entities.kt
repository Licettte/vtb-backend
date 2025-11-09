package org.elly.storage.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Table("bank_links")
data class BankLinkEntity(
    @Id val id: Long? = null,
    val userId: Long,
    val bank: String,
    val clientId: String,
    val accessToken: String,
    val expiresAt: LocalDateTime
)

@Table("accounts")
data class AccountEntity(
    @Id val id: Long? = null,
    val userId: Long,
    val bank: String,
    val accountId: String,
    val nickname: String?,
    val isReserve: Boolean,
    val isSalarySource: Boolean
)

@Table("obligations")
data class ObligationEntity(
    @Id val id: String,         // детерминированный hash
    val userId: Long,
    val source: String,         // "agg" или код банка
    val merchantKey: String,
    val title: String,
    val category: String,       // loan|utility|telecom|subscription|rent|other
    val currency: String,       // "RUB"
    val avgAmountMinor: Long,   // отрицательное
    val periodicity: String,    // MONTHLY|WEEKLY
    val typicalDay: Int?,       // можно null
    val nextDueDate: LocalDate,
    val repeats: Int,
    val confidence: Double,
    val createdAt: Instant
)

@Table("payment_runs")
data class PaymentRunEntity(
    @Id val id: Long? = null,
    val obligationId: String,        // ← ВАЖНО: строка, т.к. obligations.id = VARCHAR
    val scheduledAt: LocalDateTime,
    val amountMinor: Long,           // единый нейминг
    val status: String,              // "PENDING"|"PROCESSING"|"DONE"|"FAILED"
    val paymentId: String?,
    val failReason: String?
)
