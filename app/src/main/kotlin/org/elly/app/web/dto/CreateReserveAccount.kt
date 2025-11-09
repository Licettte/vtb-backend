package org.elly.app.web.dto

/**
 * Запрос на создание "резервного" счёта и правила пополнения.
 */
data class CreateReserveAccountReq(

    /** Процент от зарплаты, который уводим в резерв (0..100). */
    val percentOfSalary: Int,

    val payments: List<PaymentDto>
)

/**
 * Ответ на создание "резервного" счёта.
 * Содержит идентификатор счёта, базовые метаданные и сводку по финансированию.
 */
data class ReserveAccountResp(
    /** Внутренний id нашего резервного счёта. */
    val id: Long,

    /** Где "лежит" счёт */
    val bank: String = "vbank",

    /** Валюта счёта (MVP: "RUB"). */
    val currency: String = "RUB",

    /** Статус счёта (MVP: "ACTIVE"). */
    val status: String = "ACTIVE",

    /** ID банковского счёта */
    val externalAccountId: String? = null,

    /** Текущий баланс резерва в копейках (на старте 0). */
    val balanceMinor: Long = 0,

    /** Процент от зарплаты, который уводим в резерв (0..100). */
    val percentOfSalary: Int,

    /**
     * Сумма всех обязательств T за месяц (копейки), посчитанная из payments.amountRub.
     */
    val obligationsTotalMinor: Long,

    /** Аудитные поля. */
    val createdAt: String,
    val updatedAt: String
)