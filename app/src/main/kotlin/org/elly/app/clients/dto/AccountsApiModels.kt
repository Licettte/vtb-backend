// app/clients/dto/AccountsApiModels.kt
package org.elly.app.clients.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.elly.app.util.Json
import org.elly.core.model.*

/** Плоский формат: { "accounts": [ { "account_id": "...", "nickname": "..." } ] } */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountsFlat(val accounts: List<AccountItem>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountItem(
    val account_id: String,
    val currency: String? = null,
    val account_type: String? = null,
    val nickname: String? = null
) {
    fun toRef(bank: BankCode) = AccountRef(
        bank = bank,
        accountId = AccountId(account_id),
        nickname = nickname,
        isReserve = false
    )
}

/** OB-конверт: { "data": { "account": [...] }, "links": {...}, "meta": {...} } */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountsEnvelope(val data: AccountsData?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountsData(val account: List<AccountV3>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountV3(
    val accountId: String,
    val status: String? = null,
    val currency: String? = null,
    val accountType: String? = null,
    val accountSubType: String? = null,
    val nickname: String? = null
) {
    fun toRef(bank: BankCode) = AccountRef(
        bank = bank,
        accountId = AccountId(accountId),
        nickname = nickname,
        isReserve = false
    )
}

/** Универсальный парсер «любой из двух форм» в List<AccountRef> */
fun parseAccountsFlexible(rawJson: String, bank: BankCode): List<AccountRef> {
    val node = Json.mapper.readTree(rawJson)

    // OB envelope
    if (node.has("data") && node["data"].has("account")) {
        val env = Json.mapper.treeToValue(node, AccountsEnvelope::class.java)
        return env.data?.account.orEmpty().map { it.toRef(bank) }
    }

    // Flat
    if (node.has("accounts")) {
        val flat = Json.mapper.treeToValue(node, AccountsFlat::class.java)
        return flat.accounts.orEmpty().map { it.toRef(bank) }
    }

    return emptyList()
}
