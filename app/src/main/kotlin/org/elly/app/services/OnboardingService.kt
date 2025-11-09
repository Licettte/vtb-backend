package org.elly.app.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.elly.app.analytics.detectObligations
import org.elly.app.analytics.withMockTx
import org.elly.app.util.Json
import org.elly.app.web.OnboardingSseController
import org.elly.app.web.dto.PaymentDto
import org.elly.app.web.mappers.toPaymentDto
import org.elly.core.model.*
import org.elly.core.ports.*
import org.elly.core.repo.OnboardingRepo
import org.elly.storage.entities.ObligationEntity
import org.elly.storage.repos.UsersRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OnboardingService(
    private val linksRepo: LinksRepo,
    private val consentsPort: ConsentsPort,
    private val consentsRepo: AccountsConsentsRepo,
    private val bankPort: BankAccountsPort,
    private val obligationsRepo: ObligationsRepo,
    private val onbRepo: OnboardingRepo,
    private val usersRepo: UsersRepo,
    private val appScope: CoroutineScope,
    private val sse: OnboardingSseController,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // === Public API ===========================================================

    suspend fun startAsync(userId: UserId, banks: List<BankCode>): OnboardingJob {
        val clientId = deriveClientId(userId)

        val job = OnboardingJob(
            jobId = "onb_${System.currentTimeMillis()}",
            userId = userId,
            phase = OnbPhase.CONSENTS_IN_PROGRESS,
            progress = 5
        )
        onbRepo.create(job)
        notify(job.jobId, job.phase, job.progress, mapOf("banks" to banks.map { it.value }))

        appScope.launch {
            try {
                // 1) Согласия (параллельно, с кешированием)
                val perBank = requestConsents(userId, clientId, banks)

                var state = job.copy(
                    phase = OnbPhase.CONSENTS_IN_PROGRESS,
                    progress = 25,
                    perBankConsent = perBank
                )
                onbRepo.update(state)
                notify(job.jobId, state.phase, state.progress, mapOf("consents" to perBank.mapKeys { it.key.value }))

                // 2) Сбор транзакций
                state = state.copy(phase = OnbPhase.TRANSACTIONS_COLLECTING, progress = 30)
                onbRepo.update(state)
                notify(job.jobId, state.phase, state.progress, null)

                val (fromIso, toIso) = calcWindowIso(daysBack = 90)
                val collectedTx = collectAllTransactions(userId, clientId, banks, perBank, fromIso, toIso)

                state = state.copy(progress = 60)
                onbRepo.update(state)
                notify(job.jobId, state.phase, state.progress, null)

// 3) Аналитика + сохранение
                val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                val txWithMocks = withMockTx(collectedTx, today)

                val obligations = detectObligations(
                    userId = userId.value,
                    tx = txWithMocks,
                    today = today,
                    source = "agg",
                    currency = "RUB"
                )// ← передаём userId
                obligationsRepo.upsertAll(obligations)

// 4) Завершение + payload для фронта
                val payments: List<PaymentDto> = obligations.map { it.toPaymentDto(today) }.sortedBy { it.day }

                state = state.copy(
                    phase = OnbPhase.DONE,
                    progress = 100,
                    obligationsDetected = obligations.size
                )
                onbRepo.update(state)
                log.info(
                    "Onboarding done payload: jobId={} payload={}",
                    job.jobId, Json.toJson(mapOf("obligationsDetected" to obligations.size, "payments" to payments))
                )

                sse.emit(
                    job.jobId,
                    "done",
                    Json.toJson(
                        mapOf(
                            "obligationsDetected" to obligations.size,
                            "payments" to payments
                        )
                    )
                )
                sse.complete(job.jobId)
            } catch (e: Throwable) {
                log.warn("Onboarding failed: {}", e.message, e)
                onbRepo.update(job.copy(phase = OnbPhase.FAILED, progress = 100, error = e.message))
                sse.emit(job.jobId, "failed", Json.toJson(mapOf("error" to (e.message ?: "unknown"))))
                sse.complete(job.jobId)
            }
        }

        return job
    }

    suspend fun status(jobId: String) = onbRepo.get(jobId)

    // === Phase helpers ========================================================

    /** параллельно проверяем/получаем consents и возвращаем approved|pending по банкам */
    private suspend fun requestConsents(
        userId: UserId,
        clientId: ClientId,
        banks: List<BankCode>
    ): Map<BankCode, String> =
        banks.associateWith { bank ->
            appScope.async {
                val consent = ensureConsentCached(userId, bank, clientId)
                if (consent.status == "approved") "approved" else "pending"
            }
        }.mapValues { it.value.await() }

    /** собираем транзакции со всех банков, где consent=approved; без UI-пэйлоадов */
    private suspend fun collectAllTransactions(
        userId: UserId,
        clientId: ClientId,
        banks: List<BankCode>,
        perBank: Map<BankCode, String>,
        fromIso: String,
        toIso: String
    ): List<TxRecord> {
        // собираем по банкам параллельно
        val perBankTx: Map<BankCode, List<TxRecord>> = banks.associateWith { bank ->
            appScope.async {
                if (perBank[bank] != "approved") {
                    log.info("Skip bank={} (consent not approved)", bank.value)
                    return@async emptyList()
                }
                collectBankTransactions(userId, clientId, bank, fromIso, toIso)
            }
        }.mapValues { it.value.await() }

        val total = perBankTx.values.sumOf { it.size }
        log.info("Transactions collected: total={} perBank={}",
            total,
            perBankTx.mapKeys { it.key.value }.mapValues { it.value.size }
        )

        return perBankTx.values.flatten()
    }

    /** сбор транзакций по одному банку (последовательно по счетам — безопаснее для лимитов) */
    private suspend fun collectBankTransactions(
        userId: UserId,
        clientId: ClientId,
        bank: BankCode,
        fromIso: String,
        toIso: String
    ): List<TxRecord> {
        val approvedConsent = ensureConsentCached(userId, bank, clientId)
        require(approvedConsent.status == "approved") { "Consent not approved for ${bank.value}" }

        val accounts = bankPort.listAccounts(
            bank = bank,
            clientId = clientId,
            consentId = approvedConsent.consentId
        )

        val all = mutableListOf<TxRecord>()
        for (acc in accounts) {
            val tx = bankPort.listTransactions(
                token = "", // игнорируется реализацией клиента в MVP
                account = acc,
                fromIso = fromIso,
                toIso = toIso,
                consentId = approvedConsent.consentId
            )
            all += tx
        }
        log.info("Bank={} accounts={} tx={}", bank.value, accounts.size, all.size)
        return all
    }

    // === Consents cache =======================================================

    private suspend fun ensureConsentCached(
        userId: UserId,
        bank: BankCode,
        clientId: ClientId
    ): AccountsConsent {
        // 1) пробуем из БД
        consentsRepo.find(userId, bank)?.let { cached ->
            if (cached.status == "approved") return cached
        }
        // 2) ходим к банку
        val ext = consentsPort.ensureAccountsConsent(bank, clientId, userId)
        // 3) сохраняем и возвращаем
        val saved = AccountsConsent(
            userId = userId,
            bank = bank,
            clientId = clientId,
            consentId = ext.consentId,
            status = ext.status,
            createdAt = Clock.System.now()
        )
        return consentsRepo.upsert(saved)
    }

    // === Misc =================================================================

    private fun calcWindowIso(daysBack: Long): Pair<String, String> {
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val fromIso = now.minusDays(daysBack).toString()
        val toIso = now.toString()
        return fromIso to toIso
    }

    private data class Progress(val phase: OnbPhase, val progress: Int, val detail: Any? = null)

    private fun notify(jobId: String, phase: OnbPhase, progress: Int, detail: Any? = null) {
        val payload = Json.toJson(Progress(phase, progress, detail))
        sse.emit(jobId, "progress", payload)
    }

    /** MVP: clientId = локальная часть e-mail (до '@'), нормализованная */
    private suspend fun deriveClientId(userId: UserId): ClientId {
        val email = usersRepo.getEmail(userId) ?: error("Email not found for userId=${userId.value}")
        val local = email.substringBefore('@').trim()
        require(local.isNotBlank()) { "Cannot derive clientId from email='$email'" }
        val normalized = local.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        return ClientId(normalized)
    }
}
