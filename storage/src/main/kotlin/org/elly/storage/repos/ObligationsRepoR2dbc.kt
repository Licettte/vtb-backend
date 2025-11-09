// storage/repos/ObligationsRepoR2dbc.kt
package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import org.elly.core.ports.ObligationsRepo
import org.elly.core.model.Obligation            // доменная модель (kotlinx.datetime)
import org.elly.storage.entities.ObligationEntity // Entity (java.time)
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate
import kotlinx.datetime.Instant as KxInstant
import kotlinx.datetime.LocalDate as KxLocalDate

@Repository
class ObligationsRepoR2dbc(private val db: DatabaseClient) : ObligationsRepo {

    // --- ObligationsRepo (порт оперирует МОДЕЛЬЮ) ---
    override suspend fun upsertAll(items: List<Obligation>) {
        if (items.isEmpty()) return
        for (m in items) {
            val e = m.toEntity()  // model -> entity
            val stmt = db.sql(
                """
    INSERT INTO obligations (
      id, user_id, source, merchant_key, title, category, currency,
      avg_amount_minor, periodicity, typical_day, next_due_date, repeats, confidence, created_at
    ) VALUES (
      :id, :userId, :source, :merchantKey, :title, :category, :currency,
      :avg, :periodicity, :typicalDay, :nextDue, :repeats, :confidence, :createdAt
    )
    ON CONFLICT (id) DO UPDATE SET
      source=:source,
      merchant_key=:merchantKey,
      title=:title,
      category=:category,
      currency=:currency,
      avg_amount_minor=:avg,
      periodicity=:periodicity,
      typical_day=:typicalDay,
      next_due_date=:nextDue,
      repeats=:repeats,
      confidence=:confidence,
      created_at=:createdAt
    """.trimIndent()
            )

            var spec = stmt
                .bind("id", e.id)
                .bind("userId", e.userId)
                .bind("source", e.source)
                .bind("merchantKey", e.merchantKey)
                .bind("title", e.title)
                .bind("category", e.category)
                .bind("currency", e.currency)
                .bind("avg", e.avgAmountMinor)
                .bind("periodicity", e.periodicity)

// ВАЖНО: nullable поле — через bindNull, если null
            spec = if (e.typicalDay != null)
                spec.bind("typicalDay", e.typicalDay)
            else
                spec.bindNull("typicalDay", java.lang.Integer::class.java)

            spec = spec
                .bind("nextDue", e.nextDueDate)        // java.time.LocalDate
                .bind("repeats", e.repeats)
                .bind("confidence", e.confidence)
                .bind("createdAt", e.createdAt)        // java.time.Instant

            spec.fetch().rowsUpdated().awaitSingle()
        }
    }

    override suspend fun listActive(userId: Long): List<Obligation> =
        db.sql(
            """
            SELECT id, user_id, source, merchant_key, title, category, currency,
                   avg_amount_minor, periodicity, typical_day, next_due_date, repeats, confidence, created_at
            FROM obligations
            WHERE user_id = :userId
              AND next_due_date >= CURRENT_DATE - INTERVAL '3 days'
            ORDER BY next_due_date ASC, category ASC, title ASC
            """.trimIndent()
        )
            .bind("userId", userId)
            .map { row: Row, _: RowMetadata -> row.toEntity() } // Row -> Entity
            .all()
            .collectList()
            .awaitSingle()
            .map { it.toModel() }            // Entity -> Model

    // --- Row → Entity маппер ---
    private fun Row.toEntity(): ObligationEntity =
        ObligationEntity(
            id = get("id", String::class.java)!!,
            userId = get("user_id", java.lang.Long::class.java)!!.toLong(),
            source = get("source", String::class.java)!!,
            merchantKey = get("merchant_key", String::class.java)!!,
            title = get("title", String::class.java)!!,
            category = get("category", String::class.java)!!,
            currency = get("currency", String::class.java)!!,
            avgAmountMinor = get("avg_amount_minor", java.lang.Long::class.java)!!.toLong(),
            periodicity = get("periodicity", String::class.java)!!,
            typicalDay = get("typical_day", java.lang.Integer::class.java)?.toInt(),
            nextDueDate = get("next_due_date", JLocalDate::class.java)!!,
            repeats = get("repeats", java.lang.Integer::class.java)!!.toInt(),
            confidence = get("confidence", java.lang.Double::class.java)!!.toDouble(),
            createdAt = get("created_at", JInstant::class.java)!!
        )

    // --- Entity ↔ Model мапперы и конвертеры дат ---
    private fun ObligationEntity.toModel(): Obligation =
        Obligation(
            id = id,
            userId = userId,
            source = source,
            merchantKey = merchantKey,
            title = title,
            category = category,
            currency = currency,
            avgAmountMinor = avgAmountMinor,
            periodicity = periodicity,
            typicalDay = typicalDay,
            nextDueDate = nextDueDate.toKx(),
            repeats = repeats,
            confidence = confidence,
            createdAt = createdAt.toKx()
        )

    private fun Obligation.toEntity(): ObligationEntity =
        ObligationEntity(
            id = id,
            userId = userId,
            source = source,
            merchantKey = merchantKey,
            title = title,
            category = category,
            currency = currency,
            avgAmountMinor = avgAmountMinor,
            periodicity = periodicity,
            typicalDay = typicalDay,
            nextDueDate = nextDueDate.toJava(),
            repeats = repeats,
            confidence = confidence,
            createdAt = createdAt.toJava()
        )

    private fun JLocalDate.toKx(): KxLocalDate =
        KxLocalDate(year, monthValue, dayOfMonth)

    private fun KxLocalDate.toJava(): JLocalDate =
        JLocalDate.of(year, monthNumber, dayOfMonth)

    private fun JInstant.toKx(): KxInstant =
        KxInstant.fromEpochMilliseconds(toEpochMilli())

    private fun KxInstant.toJava(): JInstant =
        JInstant.ofEpochMilli(toEpochMilliseconds())
}
