package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.elly.core.model.*
import org.elly.core.ports.LinksRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime as JLocalDateTime

@Repository
class LinksRepoR2dbc(
    private val db: DatabaseClient
) : LinksRepo {

    override suspend fun getBankLink(userId: UserId, bank: BankCode): BankLink? {
        val sql = """
      SELECT user_id, bank, client_id, access_token, expires_at
      FROM bank_links
      WHERE user_id = :userId AND bank = :bank
      LIMIT 1
    """.trimIndent()

        return db.sql(sql)
            .bind("userId", userId.value)
            .bind("bank", bank.value)
            .map { row, _ ->
                val uid  = (row.get("user_id", java.lang.Long::class.java)!!).toLong()
                val b    = row.get("bank", String::class.java)!!
                val cid  = row.get("client_id", String::class.java)!!
                val tok  = row.get("access_token", String::class.java)!!
                val exp  = row.get("expires_at", JLocalDateTime::class.java)!!
                BankLink(
                    userId = UserId(uid),
                    bank = BankCode(b),
                    clientId = ClientId(cid),
                    accessToken = tok,
                    expiresAt = exp.toKotlinLocalDateTime()
                )
            }
            .one()
            .awaitSingleOrNull()
    }

    override suspend fun saveBankLink(link: BankLink) {
        val sql = """
      INSERT INTO bank_links (user_id, bank, client_id, access_token, expires_at)
      VALUES (:userId, :bank, :clientId, :token, :expiresAt)
      ON CONFLICT (user_id, bank) DO UPDATE SET
        client_id    = EXCLUDED.client_id,
        access_token = EXCLUDED.access_token,
        expires_at   = EXCLUDED.expires_at
    """.trimIndent()

        db.sql(sql)
            .bind("userId", link.userId.value)
            .bind("bank", link.bank.value)
            .bind("clientId", link.clientId.value)
            .bind("token", link.accessToken)
            .bind("expiresAt", link.expiresAt.toJava())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}

/* ===== утилиты времени ===== */

private fun LocalDateTime.toJava(): JLocalDateTime =
    JLocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second, nanosecond)
