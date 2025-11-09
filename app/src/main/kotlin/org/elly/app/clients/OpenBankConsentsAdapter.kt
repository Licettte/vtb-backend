// app/clients/OpenBankConsentsAdapter.kt
package org.elly.app.clients

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.elly.app.config.EllyBanksProps
import org.elly.app.util.Json
import org.elly.core.model.*
import org.elly.core.ports.ConsentsPort
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class OpenBankConsentsAdapter(
    private val props: EllyBanksProps,
    private val webClientBuilder: WebClient.Builder,
    private val tokens: BankTokenProvider
) : ConsentsPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun ensureAccountsConsent(
        bank: BankCode,
        clientId: ClientId,
        userId: UserId
    ): AccountsConsent {
        val token = tokens.getBankToken(bank)

        val body = ConsentRequestBody(
            client_id = clientId.value,
            permissions = listOf("ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"),
            reason = "Ellie onboarding aggregation",
            requesting_bank = props.teamClientId,
            requesting_bank_name = "Ellie App"
        )

        val base = props.banks[bank.value.lowercase()]?.baseUrl
            ?: error("No baseUrl configured for bank='${bank.value}'")
        val url = "$base/account-consents/request"

        log.info(
            "Consent[request]: bank={} clientId={} url={} (Authorization: Bearer ****, X-Requesting-Bank: {})",
            bank.value, clientId.value, url, props.teamClientId
        )

        val raw = wc(bank)
            .post()
            .uri("/account-consents/request")
            .headers {
                it.setBearerAuth(token)
                it.add("X-Requesting-Bank", props.teamClientId)
            }
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .onStatus({ s -> s.isError }) { r ->
                r.bodyToMono(String::class.java).map { payload ->
                    log.warn("Consent[http-error]: bank={} status={} bodySnippet={}", bank.value, r.statusCode(), payload.take(500))
                    IllegalStateException("Consent request failed: ${r.statusCode()}")
                }
            }
            .bodyToMono(String::class.java)
            .awaitSingle()

        log.info("Consent[response]: bank={} raw={}", bank.value, raw.take(500))

        val (consentId, status) = parseConsentFlexible(raw)
        log.info("Consent[ok]: bank={} consentId={} status={}", bank.value, consentId, status)

        return AccountsConsent(
            userId = userId,
            bank = bank,
            clientId = clientId,
            consentId = ConsentId(consentId),
            status = status,
            createdAt = Clock.System.now()         // если нашли в ответе — положим
        )
    }

    override suspend fun ensurePaymentsConsent(bank: BankCode, clientId: ClientId, userId: UserId): AccountsConsent {
        TODO("Not yet implemented")
    }

    // ---------------- helpers ----------------

    private fun wc(bank: BankCode): WebClient {
        val base = props.banks[bank.value.lowercase()]?.baseUrl
            ?: error("No baseUrl configured for bank='${bank.value}'")
        return webClientBuilder.clone().baseUrl(base).build()
    }

    /**
     * Поддерживает оба формата:
     * 1) {"data":{"consentId":"...","status":"..."}}
     * 2) {"consent_id":"...","status":"approved", ...}
     */
    private fun parseConsentFlexible(json: String): Pair<String, String> {
        val node = Json.mapper.readTree(json)

        // вариант 1: обёртка data
        val data = node.get("data")
        val consentIdFromData = data?.get("consentId")?.asText()
        val statusFromData = data?.get("status")?.asText()

        if (!consentIdFromData.isNullOrBlank()) {
            return consentIdFromData to (statusFromData ?: "approved")
        }

        // вариант 2: плоская форма
        val consentId =
            node.get("consent_id")?.asText()
                ?: node.get("consentId")?.asText()
                ?: throw IllegalStateException("No consentId/consent_id in response")

        val status =
            node.get("status")?.asText()
                ?: "approved" // по умолчанию

        return consentId to status
    }

    // DTO для запроса
    private data class ConsentRequestBody(
        val client_id: String,
        val permissions: List<String>,
        val reason: String = "",
        val requesting_bank: String,
        val requesting_bank_name: String = "Ellie App"
    )
}
