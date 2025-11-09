// app/clients/OpenBankAccountsAdapter.kt
package org.elly.app.clients

import kotlinx.coroutines.reactor.awaitSingle
import org.elly.app.config.EllyBanksProps
import org.elly.app.clients.dto.parseAccountsFlexible
import org.elly.app.clients.dto.parseTransactionsFlexible
import org.elly.core.model.*
import org.elly.core.ports.BankAccountsPort
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.temporal.ChronoUnit

@Component
class OpenBankAccountsAdapter(
    private val props: EllyBanksProps,
    private val webClientBuilder: WebClient.Builder,
    private val tokens: BankTokenProvider
) : BankAccountsPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun listAccounts(
        bank: BankCode,
        clientId: ClientId,
        consentId: ConsentId
    ): List<AccountRef> {
        val base = baseUrl(bank)
        val bankToken = tokens.getBankToken(bank)
        val url = "$base/accounts"

        val t0 = System.nanoTime()
        log.info(
            "Accounts[list→]: bank={} clientId={} url={} (X-Requesting-Bank={}, X-Consent-Id={})",
            bank.value, clientId.value, url, props.teamClientId, consentId.value
        )

        val raw = wc(base)
            .get()
            .uri { b ->
                b.path("/accounts")
                    .queryParam("client_id", clientId.value)
                    .build()
            }
            .headers {
                it.setBearerAuth(bankToken)
                it.add("X-Requesting-Bank", props.teamClientId)
                it.add("X-Consent-Id", consentId.value)
            }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()

        val items = parseAccountsFlexible(raw, bank)

        val ms = (System.nanoTime() - t0) / 1_000_000
        if (items.isEmpty()) {
            log.warn("Accounts[list←0]: bank={} clientId={} ({} ms, empty)", bank.value, clientId.value, ms)
        } else {
            val sample = items.take(3).joinToString { it.accountId.value }
            log.info("Accounts[list←{}]: bank={} clientId={} ({} ms, sample={})",
                items.size, bank.value, clientId.value, ms, sample)
        }
        return items
    }

    override suspend fun listTransactions(
        token: String,
        account: AccountRef,
        fromIso: String,
        toIso: String,
        consentId: ConsentId?
    ): List<TxRecord> {
        requireNotNull(consentId) { "consentId is required for interbank transactions" }

        val bank = account.bank
        val base = baseUrl(bank)
        val bankToken = tokens.getBankToken(bank)

        val from = normalizeIsoToSeconds(fromIso)
        val to   = normalizeIsoToSeconds(toIso)
        val limit = 100

        val url = "$base/accounts/${account.accountId.value}/transactions"
        val t0 = System.nanoTime()
        log.info(
            "Tx[list→]: bank={} accountId={} from={} to={} url={} (X-Requesting-Bank={}, X-Consent-Id={})",
            bank.value, account.accountId.value, from, to, url, props.teamClientId, consentId.value
        )

        // 1) тянем СЫРОЕ тело JSON
        val raw = wc(base)
            .get()
            .uri { b ->
                b.path("/accounts/{id}/transactions")
                    .queryParam("from_booking_date_time", from)
                    .queryParam("to_booking_date_time", to)
                    .queryParam("limit", limit)
                    .build(account.accountId.value)
            }
            .headers {
                it.setBearerAuth(bankToken)
                it.add("X-Requesting-Bank", props.teamClientId)
                it.add("X-Consent-Id", consentId.value)
            }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus({ s -> s.is4xxClientError || s.is5xxServerError }) { r ->
                r.bodyToMono(String::class.java).map { body ->
                    log.warn(
                        "Tx[http-error]: bank={} accountId={} status={} body={}",
                        bank.value, account.accountId.value, r.statusCode(), body.take(1000)
                    )
                    IllegalStateException("Transactions request failed: ${r.statusCode()}")
                }
            }
            .bodyToMono(String::class.java)
            .awaitSingle()

        // 2) парсим твоим гибким парсером → сразу получаем List<TxRecord>
        val list = parseTransactionsFlexible(raw, account)

        val ms = (System.nanoTime() - t0) / 1_000_000
        if (list.isEmpty()) {
            log.warn("Tx[list←0]: bank={} accountId={} ({} ms, empty)", bank.value, account.accountId.value, ms)
        } else {
            val first3 = list.take(3).joinToString {
                val amt = it.amount.amount
                val sign = if (amt >= 0) "+" else "-"
                "${it.bookingAt.date} ${sign}${kotlin.math.abs(amt)}"
            }
            log.info("Tx[list←{}]: bank={} accountId={} ({} ms, sample={})",
                list.size, bank.value, account.accountId.value, ms, first3)
        }
        return list
    }

// --- helpers ---

    private fun normalizeIsoToSeconds(iso: String): String =
        try {
            java.time.Instant.parse(iso)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString() // формат вида 2025-11-08T23:41:13Z
        } catch (_: Exception) {
            // если вдруг пришёл локальный формат — оставим как есть
            iso
        }

    override suspend fun ensureReserveAccount(
        userId: UserId,
        bank: BankCode,
        clientId: ClientId,
        consentId: ConsentId
    ): AccountRef = throw UnsupportedOperationException("ensureReserveAccount is not implemented yet in MVP")

    override suspend fun transfer(
        token: String,
        debtor: AccountRef,
        creditor: AccountRef,
        amount: Money,
        comment: String?,
        clientId: ClientId,
        paymentConsentId: ConsentId?
    ): PaymentId = throw UnsupportedOperationException("transfer is not implemented yet in MVP")

    // --- helpers ---
    private fun wc(baseUrl: String): WebClient = webClientBuilder.clone().baseUrl(baseUrl).build()
    private fun baseUrl(bank: BankCode): String =
        props.banks[bank.value.lowercase()]?.baseUrl
            ?: error("No baseUrl configured for bank='${bank.value}'")
}
