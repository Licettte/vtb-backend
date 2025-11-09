// app/clients/dto/TransactionsApiModels.kt
package org.elly.app.clients.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.elly.app.util.Json
import org.elly.core.model.AccountRef
import org.elly.core.model.Money
import org.elly.core.model.TxRecord
import java.math.BigDecimal
import java.math.RoundingMode

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionsEnvelope(val data: TransactionsData?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionsData(val transactions: List<TxItem>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TxItem(
    val transaction_id: String? = null,
    val amount: String? = null,           // "1234.56" или "-1234.56"
    val currency: String? = null,
    val bookingDateTime: String? = null,  // ISO-8601
    val description: String? = null,
    val counterpartyAccount: String? = null
) {
    fun toTxRecord(account: AccountRef): TxRecord {
        val minor = amount
            ?.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, BigDecimal.ROUND_HALF_UP)
            ?.toLong()
            ?: 0L

        val bookedAt = bookingDateTime
            ?.let { kotlinx.datetime.Instant.parse(it).toLocalDateTime(TimeZone.UTC) }
            ?: kotlinx.datetime.Instant.DISTANT_PAST.toLocalDateTime(TimeZone.UTC)

        return TxRecord(
            account = account,
            bookingAt = bookedAt,
            amount = Money(minor),
            description = description,
            counterpartyAccount = counterpartyAccount
        )
    }
}

fun parseTransactionsFlexible(rawJson: String, account: AccountRef): List<TxRecord> {
    val node = Json.mapper.readTree(rawJson)

    // 1) Envelope
    if (node.has("data")) {
        val data = node["data"]
        val arr = when {
            data.has("transactions") && data["transactions"].isArray -> data["transactions"]
            data.has("transaction")  && data["transaction"].isArray  -> data["transaction"]   // <-- твой кейс
            else -> null
        }
        if (arr != null) return arr.mapNotNull { it.toTxRecordSafe(account) }
    }

    // 2) Root object with array
    if (node.isObject) {
        val arr = when {
            node.has("transactions") && node["transactions"].isArray -> node["transactions"]
            node.has("transaction")  && node["transaction"].isArray  -> node["transaction"]
            else -> null
        }
        if (arr != null) return arr.mapNotNull { it.toTxRecordSafe(account) }
    }

    // 3) Root array
    if (node.isArray) {
        return node.mapNotNull { it.toTxRecordSafe(account) }
    }

    return emptyList()
}

/** Безопасная конвертация одного элемента транзакции в TxRecord с учётом разных схем. */
private fun JsonNode.toTxRecordSafe(account: AccountRef): TxRecord? = try {
    // id
    val txId = firstText("transactionId", "transaction_id", "id") ?: return null

    // сумма: может быть строкой, а может быть объектом { amount, currency }
    val (amountMinor, _currency) = parseAmountMinorAndCurrency(this)

    // дата: bookingDateTime / valueDateTime / bookingDate / valueDate
    val bookingIso = firstText("bookingDateTime", "valueDateTime", "bookingDate", "valueDate")
    val booking = bookingIso
        ?.let { Instant.parse(it).toLocalDateTime(TimeZone.UTC) }
        ?: Instant.DISTANT_PAST.toLocalDateTime(TimeZone.UTC)

    // описание
    val description = firstText("transactionInformation", "description", "narrative", "details")
        ?: this.path("merchant").path("name").takeIf { it.isTextual }?.asText()

    // контрагент (если есть разные варианты)
    val counterpartyAccount =
        firstText("counterpartyAccount", "counterparty_account")
            ?: this.path("counterparty").path("accountId").takeIf { it.isTextual }?.asText()

    TxRecord(
        account = account,
        bookingAt = booking,
        amount = Money(amountMinor),
        description = description,
        counterpartyAccount = counterpartyAccount
    )
} catch (_: Exception) {
    null
}

/** Достаёт текст из первого существующего поля. */
private fun JsonNode.firstText(vararg keys: String): String? {
    for (k in keys) {
        val n = this.get(k)
        if (n != null && n.isValueNode && !n.isNull) {
            val v = n.asText()
            if (v.isNotBlank()) return v
        }
    }
    return null
}

/** Парсим сумму в минорных единицах и применяем знак по creditDebitIndicator (Debit → минус). */
private fun parseAmountMinorAndCurrency(node: JsonNode): Pair<Long, String?> {
    // Варианты:
    //  A) "amount": "123.45", "currency": "RUB"
    //  B) "amount": { "amount": "123.45", "currency": "RUB" }
    //  C) альтернативные поля на всякий случай
    val amountNode = node.get("amount")
    var amountStr: String? = null
    var currency: String? = null

    if (amountNode != null && amountNode.isObject) {
        amountStr = amountNode.get("amount")?.asText()
        currency = amountNode.get("currency")?.asText()
    } else if (amountNode != null && amountNode.isValueNode) {
        amountStr = amountNode.asText()
        currency = node.get("currency")?.asText()
    } else {
        // fallback
        amountStr = node.get("transactionAmount")?.asText()
            ?: node.get("value")?.asText()
        currency = node.get("currency")?.asText()
    }

    val debitCredit = node.get("creditDebitIndicator")?.asText()?.lowercase()
    val sign = when (debitCredit) {
        "debit"  -> -1
        "credit" -> 1
        else     -> 1 // если не сообщили — доверимся знаку самой суммы
    }

    val minor = amountStr
        ?.toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()
        ?: 0L

    // Если строка без знака, то применяем sign; если в строке уже минус — не дублируем
    val finalMinor = if (minor >= 0) sign * minor else minor

    return finalMinor to currency
}
