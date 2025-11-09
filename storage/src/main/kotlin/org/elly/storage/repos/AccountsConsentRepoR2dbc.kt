// storage/repos/AccountsConsentsRepoR2dbc.kt
package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elly.core.model.*
import org.elly.core.ports.AccountsConsentsRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class AccountsConsentsRepoR2dbc(private val db: DatabaseClient) : AccountsConsentsRepo {

    override suspend fun find(userId: UserId, bank: BankCode): AccountsConsent? =
        db.sql(
            """
            SELECT user_id, bank, client_id, consent_id, status, created_at
            FROM accounts_consents
            WHERE user_id = :uid AND bank = :bank
            """.trimIndent()
        )
            .bind("uid", userId.value)
            .bind("bank", bank.value.lowercase())
            .map { row, _ ->
                AccountsConsent(
                    userId = UserId(row.get("user_id", java.lang.Long::class.java)!!.toLong()),
                    bank = BankCode(row.get("bank", String::class.java)!!),
                    clientId = ClientId(row.get("client_id", String::class.java)!!),
                    consentId = ConsentId(row.get("consent_id", String::class.java)!!),
                    status = row.get("status", String::class.java)!!,
                    // читаем Instant из БД и конвертируем в kotlinx.datetime.Instant
                    createdAt = row.get("created_at", java.time.Instant::class.java)!!
                        .toKxInstant()
                )
            }
            .one()
            .awaitSingleOrNull()

    override suspend fun upsert(consent: AccountsConsent): AccountsConsent {
        db.sql(
            """
            INSERT INTO accounts_consents (user_id, bank, client_id, consent_id, status, created_at)
            VALUES (:uid, :bank, :clientId, :consentId, :status, :createdAt)
            ON CONFLICT (user_id, bank) DO UPDATE
              SET client_id = EXCLUDED.client_id,
                  consent_id = EXCLUDED.consent_id,
                  status     = EXCLUDED.status,
                  created_at = EXCLUDED.created_at
            """.trimIndent()
        )
            .bind("uid", consent.userId.value)
            .bind("bank", consent.bank.value.lowercase())
            .bind("clientId", consent.clientId.value)
            .bind("consentId", consent.consentId.value)
            .bind("status", consent.status)
            // записываем kotlinx.datetime.Instant как java.time.Instant
            .bind("createdAt", consent.createdAt.toJavaInstant())
            .then()
            .awaitSingleOrNull()

        return consent
    }

    override suspend fun delete(userId: UserId, bank: BankCode) {
        db.sql("DELETE FROM accounts_consents WHERE user_id=:uid AND bank=:bank")
            .bind("uid", userId.value)
            .bind("bank", bank.value.lowercase())
            .then()
            .awaitSingleOrNull()
    }
}

/* --- маленькие конвертеры времени --- */
private fun java.time.Instant.toKxInstant(): kotlinx.datetime.Instant =
    kotlinx.datetime.Instant.fromEpochMilliseconds(this.toEpochMilli())

private fun kotlinx.datetime.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochMilli(this.toEpochMilliseconds())
