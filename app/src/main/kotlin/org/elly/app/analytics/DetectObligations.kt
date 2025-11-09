// app/analytics/DetectObligations.kt
package org.elly.app.analytics

import kotlinx.datetime.*
import org.elly.core.model.Obligation
import org.elly.core.model.TxRecord
import java.security.MessageDigest

/**
 * Возвращает готовые доменные Obligations.
 * @param userId   нужен для детерминированного id (hash по userId|merchantKey|category)
 * @param source   "agg" (или код банка, если захотите)
 * @param currency "RUB" по умолчанию
 */
fun detectObligations(
    userId: Long,
    tx: List<TxRecord>,
    today: LocalDate,
    source: String = "agg",
    currency: String = "RUB"
): List<Obligation> {
    // 1) только списания
    val debits = tx.filter { it.amount.amount < 0 }
    if (debits.isEmpty()) return emptyList()

    // 2) грубая нормализация ключа группы: контрагент → описание
    val groups = debits.groupBy {
        normalizeKey(it.counterpartyAccount ?: it.description ?: "unknown")
    }

    val result = mutableListOf<Obligation>()
    val now = Clock.System.now()

    for ((key, raw) in groups) {
        val list = raw.sortedBy { it.bookingAt } // bookingAt: LocalDateTime
        if (list.size < 3) continue

        val dates = list.map { it.bookingAt.date }
        val intervals = dates.zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toInt() }

        val monthlyHits = intervals.count { it in 25..35 }
        val weeklyHits  = intervals.count { it in 5..9  }

        val period = when {
            monthlyHits >= 2 -> "MONTHLY"
            weeklyHits  >= 3 -> "WEEKLY"
            else -> continue
        }

        val minRepeats = if (period == "MONTHLY") 3 else 4
        if (list.size < minRepeats) continue

        // 3) средняя сумма по модулю из последних 3 транзакций
        val last3 = list.takeLast(3).map { kotlin.math.abs(it.amount.amount) }
        val avgAbs = (last3.sum() / last3.size).coerceAtLeast(1)
        val avgMinor = -avgAbs // обязательства — это списания

// 4) nextDueDate (+ simple typicalDay)
        val last = dates.last()

        val nextAndDay: Pair<LocalDate, Int?> = when (period) {
            "MONTHLY" -> {
                val safeDay = last.dayOfMonth.coerceIn(1, 28)
                val firstNextMonth = if (last.monthNumber == 12)
                    LocalDate(last.year + 1, 1, 1)
                else
                    LocalDate(last.year, last.monthNumber + 1, 1)

                val cand = LocalDate(firstNextMonth.year, firstNextMonth.monthNumber, safeDay)
                val adj = if (cand < today) cand.plus(1, DateTimeUnit.MONTH) else cand
                adj to safeDay
            }
            else -> {
                val cand = last.plus(7, DateTimeUnit.DAY)
                val adj = if (cand < today) cand.plus(7, DateTimeUnit.DAY) else cand
                adj to null
            }
        }

        val (next, typicalDay) = nextAndDay

        val title = inferTitle(list, key)
        val category = classifyCategory(list, title)
        if (category == "other") continue

        val repeats = list.size
        val confidence = when (period) {
            "MONTHLY" -> (0.6 + 0.05 * repeats).coerceAtMost(0.9)
            "WEEKLY"  -> (0.5 + 0.05 * repeats).coerceAtMost(0.85)
            else      -> 0.4
        }

        val id = "oblg_${hash(userId, key, category)}"

        result += Obligation(
            id = id,
            userId = userId,
            source = source,
            merchantKey = key,
            title = title,
            category = category,
            currency = currency,
            avgAmountMinor = avgMinor,
            periodicity = period,
            typicalDay = typicalDay,
            nextDueDate = next,
            repeats = repeats,
            confidence = confidence,
            createdAt = now
        )
    }

    return result
}

// --- helpers (оставь рядом или вынеси в utils) ---

fun normalizeKey(raw: String): String =
    raw.lowercase()
        .replace(Regex("\\*+\\d+"), " ")
        .replace(Regex("\\d{2,}"), " ")
        .replace(Regex("оплата|списание|покупка|платеж|visa|mir|mastercard"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun inferTitle(group: List<TxRecord>, merchantKey: String): String {
    val candidates = buildList {
        addAll(group.mapNotNull { it.counterpartyAccount?.takeIf { s -> s.length > 3 } })
        addAll(group.mapNotNull { it.description?.takeIf { s -> s.length > 3 } })
        add(merchantKey)
    }
    return candidates
        .map { it.trim() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: merchantKey
}

private fun classifyCategory(group: List<TxRecord>, title: String): String {
    val text = (title + " " + group.firstOrNull()?.description.orEmpty()).lowercase()
    return when {
        Regex("кредит|loan|ипотек|платеж по кредит").containsMatchIn(text) -> "loan"
        Regex("жкх|коммун|квартир|водоканал|электр").containsMatchIn(text) -> "utility"
        Regex("интернет|телек(ом)?|связь|мобил").containsMatchIn(text) -> "telecom"
        Regex("подписк|subscription|netflix|spotify|icloud|youtube").containsMatchIn(text) -> "subscription"
        Regex("аренд|rent").containsMatchIn(text) -> "rent"
        else -> "other"
    }
}

private fun hash(userId: Long, key: String, category: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest("$userId|$key|$category".toByteArray())
    return bytes.take(10).joinToString("") { "%02x".format(it) }
}
