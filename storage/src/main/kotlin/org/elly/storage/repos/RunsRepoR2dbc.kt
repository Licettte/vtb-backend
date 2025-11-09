package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elly.core.model.*
import org.elly.core.ports.RunsRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import kotlinx.datetime.LocalDateTime as KLocalDateTime

@Repository
class RunsRepoR2dbc(
    private val db: DatabaseClient
) : RunsRepo {

    override suspend fun plan(runs: List<PaymentRun>): List<PaymentRun> {
        if (runs.isEmpty()) return emptyList()
        val sql = """
      INSERT INTO payment_runs (obligation_id, scheduled_at, amount_cents, status)
      VALUES (:oblId, :at, :amount, :status)
      RETURNING id, obligation_id, scheduled_at, amount_cents, status, payment_id, fail_reason
    """.trimIndent()

        val result = mutableListOf<PaymentRun>()
        for (r in runs) {
            val inserted = db.sql(sql)
                .bind("oblId", r.obligationId)
                .bind("at", r.scheduledAt.toJava())
                .bind("amount", r.amount.amount)
                .bind("status", r.status.name)
                .map { row, _ -> mapRow(row) }
                .one()
                .awaitSingle()
            result += inserted
        }
        return result
    }

    override suspend fun listDue(now: KLocalDateTime): List<PaymentRun> {
        val sql = """
      SELECT id, obligation_id, scheduled_at, amount_cents, status, payment_id, fail_reason
      FROM payment_runs
      WHERE (status = 'PENDING' OR status = 'DUE' OR (status = 'PROCESSING' AND scheduled_at <= :now))
        AND scheduled_at <= :now
      ORDER BY scheduled_at ASC, id ASC
    """.trimIndent()

        return db.sql(sql)
            .bind("now", now.toJava())
            .map { row, _ -> mapRow(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    override suspend fun getById(id: Long): PaymentRun? {
        val sql = """
      SELECT id, obligation_id, scheduled_at, amount_cents, status, payment_id, fail_reason
      FROM payment_runs
      WHERE id = :id
      LIMIT 1
    """.trimIndent()

        return db.sql(sql)
            .bind("id", id)
            .map { row, _ -> mapRow(row) }
            .one()
            .awaitSingleOrNull()
    }

    override suspend fun markProcessing(id: Long) {
        db.sql("""UPDATE payment_runs SET status = 'PROCESSING' WHERE id = :id""")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun markDone(id: Long, paymentId: PaymentId) {
        db.sql("""UPDATE payment_runs SET status = 'DONE', payment_id = :pid WHERE id = :id""")
            .bind("id", id)
            .bind("pid", paymentId.value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun markFailed(id: Long, reason: String) {
        db.sql("""UPDATE payment_runs SET status = 'FAILED', fail_reason = :reason WHERE id = :id""")
            .bind("id", id)
            .bind("reason", reason)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /* ---------- helpers ---------- */

    private fun mapRow(row: io.r2dbc.spi.Row): PaymentRun {
        val id   = (row.get("id", java.lang.Long::class.java)!!).toLong()
        val obl  = (row.get("obligation_id", java.lang.Long::class.java)!!).toLong()
        val at   = row.get("scheduled_at", LocalDateTime::class.java)!!
        val amt  = (row.get("amount_cents", java.lang.Long::class.java)!!).toLong()
        val st   = RunStatus.valueOf(row.get("status", String::class.java)!!)
        val pid  = row.get("payment_id", String::class.java)?.let { PaymentId(it) }
        val fail = row.get("fail_reason", String::class.java)
        return PaymentRun(
            id = id,
            obligationId = obl,
            scheduledAt = at.toKotlin(),
            amount = Money(amt),
            status = st,
            paymentId = pid,
            failReason = fail
        )
    }

    private fun KLocalDateTime.toJava(): LocalDateTime =
        LocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second, nanosecond)

    private fun LocalDateTime.toKotlin(): KLocalDateTime =
        KLocalDateTime(year, month.value, dayOfMonth, hour, minute, second, nano)
}
