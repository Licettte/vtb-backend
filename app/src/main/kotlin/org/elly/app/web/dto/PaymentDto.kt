// app/web/dto/PaymentDto.kt
package org.elly.app.web.dto

data class PaymentDto(
    val id: String,
    val category: String, // "ЖКХ" | "Кредит" | "Аренда" | "Налоги" | "Связь" | "Подписка" | "Другое"
    val amountRub: Double,
    val day: Int,
    val status: String     // "Оплачен" | "Ожидает" | "Просрочен"
)
