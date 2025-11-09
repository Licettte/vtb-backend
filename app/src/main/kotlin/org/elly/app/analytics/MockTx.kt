// app/analytics/MockTx.kt
package org.elly.app.analytics

import kotlinx.datetime.*
import org.elly.core.model.AccountId
import org.elly.core.model.AccountRef
import org.elly.core.model.BankCode
import org.elly.core.model.TxRecord

/**
 * Подмешивает MOC-транзакции так, чтобы в данных точно были категории:
 * utility, telecom, subscription, rent, taxes (кредит у тебя уже есть).
 * Ничего не ломает: если реально похожие мерчанты уже есть — пропускаем мок.
 */
fun withMockTx(
    original: List<TxRecord>,
    today: LocalDate,
    currency: String = "RUB",
): List<TxRecord> {
    val existingKeys = original
        .map { normalizeKey(it.counterpartyAccount ?: it.description ?: "unknown") }
        .toSet()

    fun canAdd(title: String) = normalizeKey(title) !in existingKeys

    val startMonth = today.minus(DatePeriod(months = 4)) // 4 повтора назад достаточно для алгоритма
    val mocks = buildList {
        // MONTHLY: ЖКХ (utility)
        if (canAdd("МосЭнергоСбыт")) addAll(
            monthlySeries(
                title = "МосЭнергоСбыт",
                description = "Оплата электроэнергии",
                day = 5,
                startMonth = startMonth,
                months = 4,
                amountMinor = -2_500_00
            )
        )
        // MONTHLY: Интернет/моб. связь (telecom)
        if (canAdd("Ростелеком")) addAll(
            monthlySeries(
                title = "Ростелеком",
                description = "Интернет и ТВ",
                day = 12,
                startMonth = startMonth,
                months = 4,
                amountMinor = -790_00
            )
        )
        // MONTHLY: Аренда (rent)
        if (canAdd("Аренда ЖК")) addAll(
            monthlySeries(
                title = "Аренда ЖК",
                description = "Оплата аренды",
                day = 3,
                startMonth = startMonth,
                months = 4,
                amountMinor = -35_000_00
            )
        )
        // MONTHLY: Налоги (taxes) — для алгоритма делаем тоже ежемесячно
        if (canAdd("ФНС Россия")) addAll(
            monthlySeries(
                title = "ФНС Россия",
                description = "Налог на имущество",
                day = 20,
                startMonth = startMonth,
                months = 3,
                amountMinor = -1_200_00
            )
        )
    }

    return (original + mocks).sortedBy { it.bookingAt }
}

// --- генераторы серий ---

private fun monthlySeries(
    title: String,
    description: String,
    day: Int,
    startMonth: LocalDate,
    months: Int,
    amountMinor: Long,
): List<TxRecord> = (0 until months).map { i ->
    val base = startMonth.plus(DatePeriod(months = i))
    val d = safeDate(base.year, base.monthNumber, day)
    tx(
        date = d,
        amountMinor = amountMinor,
        title = title,
        description = description
    )
}

private fun safeDate(y: Int, m: Int, d: Int): LocalDate {
    val day = d.coerceIn(1, 28) // избегаем 29-31
    return LocalDate(y, m, day)
}

// Конструктор TxRecord: подправь под свою модель
private fun tx(
    date: LocalDate,
    amountMinor: Long, // отрицательное для списаний
    title: String,
    description: String,
): TxRecord {
    return TxRecord(
        // bookingAt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0),
        bookingAt = date.atTime(12, 0),
        amount = org.elly.core.model.Money(amountMinor), // если у тебя другой тип — подставь свой
        counterpartyAccount = title,
        description = description,
        account = AccountRef(BankCode("vbank"), AccountId("team205-1"))
        // заполни остальные поля дефолтами, если есть
    )
}

private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, hour, minute)
