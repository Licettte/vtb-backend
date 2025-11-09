// app/web/mappers/PaymentsMapping.kt
package org.elly.app.web.mappers

import kotlinx.datetime.LocalDate
import org.elly.app.web.dto.PaymentDto
import org.elly.core.model.Obligation
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

fun Obligation.toPaymentDto(today: LocalDate): PaymentDto {
    val categoryRu = when {
        category.equals("utility", ignoreCase = true) -> "ЖКХ"
        category.equals("loan", ignoreCase = true)    -> "Кредит"
        category.equals("rent", ignoreCase = true)    -> "Аренда"
        category.equals("telecom", ignoreCase = true) -> "Связь"
        category.equals("subscription", ignoreCase = true) -> "Подписка"
        title.contains("налог", ignoreCase = true) || category.equals("tax", true) -> "Налоги"
        else -> "Другое"
    }

    val amountRub = abs(avgAmountMinor) / 100.0
    val day = typicalDay ?: nextDueDate.dayOfMonth

    val status = when {
        nextDueDate < today -> "Просрочен"
        nextDueDate == today -> "Ожидает"
        else -> "Ожидает"
    }

    return PaymentDto(
        id = id,
        category = categoryRu,
        amountRub = String.format("%.2f", amountRub).replace(',', '.').toDouble(), // 2 знака
        day = day,
        status = status
    )
}
