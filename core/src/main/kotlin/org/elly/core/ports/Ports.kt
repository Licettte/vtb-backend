package org.elly.core.ports

import org.elly.core.model.*

interface BankAccountsPort {
  // NEW: получить список счетов (нужен для сбора транзакций)
  suspend fun listAccounts(
    bank: BankCode,
    clientId: ClientId,
    consentId: ConsentId
  ): List<AccountRef>

  // Было: список транзакций по КОНКРЕТНОМУ счёту
  suspend fun listTransactions(
    token: String,                    // игнорируем в реализациях MVP (берём bank-token сами)
    account: AccountRef,
    fromIso: String,
    toIso: String,
    consentId: ConsentId?
  ): List<TxRecord>

  // Оставляем как заглушки для будущих шагов (резервный счёт, переводы)
  suspend fun ensureReserveAccount(
    userId: UserId,
    bank: BankCode,
    clientId: ClientId,
    consentId: ConsentId
  ): AccountRef

  suspend fun transfer(
    token: String,                    // игнорируем в MVP
    debtor: AccountRef,
    creditor: AccountRef,
    amount: Money,
    comment: String?,
    clientId: ClientId,
    paymentConsentId: ConsentId? = null
  ): PaymentId
}

interface ConsentsPort {
  suspend fun ensureAccountsConsent(
    bank: BankCode,
    clientId: ClientId,
    userId: UserId
  ): AccountsConsent

  suspend fun ensurePaymentsConsent(
    bank: BankCode,
    clientId: ClientId,
    userId: UserId
  ): AccountsConsent
}

interface LinksRepo {
  suspend fun getBankLink(userId: UserId, bank: BankCode): BankLink?
  suspend fun saveBankLink(link: BankLink)
}

interface AccountsConsentsRepo {
  suspend fun find(userId: UserId, bank: BankCode): AccountsConsent?
  suspend fun upsert(consent: AccountsConsent): AccountsConsent
  suspend fun delete(userId: UserId, bank: BankCode) // на будущее (revoke/rotation)
}

interface AccountsRepo {
  /** Вернуть резервный счёт пользователя в конкретном банке (если есть) */
  suspend fun getReserveAccount(userId: UserId, bank: BankCode): AccountRef?

  /** Сохранить/обновить ссылку на счёт пользователя (флаг isReserve учитывается в самом AccountRef) */
  suspend fun saveAccountRef(ref: AccountRef, userId: UserId)

  /** Вернуть зарплатный счёт пользователя (если выбран) */
  suspend fun getSalaryAccount(userId: UserId): AccountRef?

  /** Установить/обновить зарплатный счёт пользователя */
  suspend fun setSalaryAccount(userId: UserId, account: AccountRef)
}

interface ObligationsRepo {
  suspend fun upsertAll(items: List<Obligation>)
  suspend fun listActive(userId: Long): List<Obligation>
}

interface RunsRepo {
  suspend fun plan(runs: List<PaymentRun>): List<PaymentRun>
  suspend fun listDue(now: kotlinx.datetime.LocalDateTime): List<PaymentRun>
  suspend fun getById(id: Long): PaymentRun?
  suspend fun markProcessing(id: Long)
  suspend fun markDone(id: Long, paymentId: PaymentId)
  suspend fun markFailed(id: Long, reason: String)
}
