// CachingBankTokenProvider.kt
package org.elly.app.clients

import kotlinx.coroutines.reactor.awaitSingle
import org.elly.app.config.EllyBanksProps
import org.elly.core.model.BankCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.concurrent.ConcurrentHashMap

interface BankTokenProvider {
    suspend fun getBankToken(bank: BankCode): String
}

@Component
class CachingBankTokenProvider(
    private val props: EllyBanksProps,
    private val web: WebClient
) : BankTokenProvider {

    private val log = LoggerFactory.getLogger(CachingBankTokenProvider::class.java)

    private data class Entry(val token: String, val expiresAtEpochSec: Long)
    private val cache = ConcurrentHashMap<String, Entry>() // key = bankCode(lower)

    data class TokenResp(
        val access_token: String,
        val token_type: String? = null,
        val client_id: String? = null,
        val expires_in: Int? = null
    )

    override suspend fun getBankToken(bank: BankCode): String {
        val key = bank.value.lowercase()
        val now = System.currentTimeMillis() / 1000

        // 1) Пытаемся отдать из кэша (с 60с запасом)
        cache[key]?.let { entry ->
            val secondsLeft = entry.expiresAtEpochSec - now
            if (secondsLeft > 60) {
                if (log.isDebugEnabled) {
                    log.debug("BankToken[cache-hit]: bank={}, ttlLeft={}s", key, secondsLeft)
                }
                return entry.token
            } else {
                log.debug("BankToken[cache-stale]: bank={}, ttlLeft={}s → refresh", key, secondsLeft)
            }
        }

        // 2) Идём за новым токеном
        val base = props.banks[key]?.baseUrl
            ?: error("Unknown bank: ${bank.value}")

        val uri = "$base/auth/bank-token?client_id=${props.teamClientId}&client_secret=${props.teamClientSecret}"
        log.info("BankToken[fetch]: bank={} url={}", key, uri)

        try {
            val res = web.post()
                .uri("$base/auth/bank-token?client_id=${props.teamClientId}&client_secret=${props.teamClientSecret}")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { r ->
                    r.bodyToMono(String::class.java).map { body ->
                        // Логируем статус и кусочек тела ошибки (без секретов)
                        log.warn(
                            "BankToken[http-error]: bank={} status={} bodySnippet={}",
                            key, r.statusCode(), body.take(300)
                        )
                        IllegalStateException("bank-token http ${r.statusCode()}")
                    }
                }
                .bodyToMono(TokenResp::class.java)
                .awaitSingle()

            val ttl = (res.expires_in ?: 86400).toLong()
            val entry = Entry(res.access_token, now + ttl)
            cache[key] = entry

            if (log.isInfoEnabled) {
                log.info(
                    "BankToken[fetch-ok]: bank={} ttl={}s tokenPrefix={}",
                    key, ttl, res.access_token
                )
            }
            return entry.token
        } catch (e: Throwable) {
            log.error("BankToken[fetch-failed]: bank={} error={}", key, e.message)
            throw e
        }
    }
}
