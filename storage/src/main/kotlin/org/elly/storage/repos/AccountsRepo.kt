package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elly.core.model.*
import org.elly.core.ports.AccountsRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class AccountsRepoR2dbc(
    private val db: DatabaseClient
) : AccountsRepo {

    override suspend fun getReserveAccount(userId: UserId, bank: BankCode): AccountRef? {
        val sql = """
      SELECT bank, account_id, nickname, is_reserve, is_salary_source
      FROM accounts
      WHERE user_id = :userId AND bank = :bank AND is_reserve = true
      LIMIT 1
    """.trimIndent()

        return db.sql(sql)
            .bind("userId", userId.value)
            .bind("bank", bank.value)
            .map { row, _ ->
                val bank = row.get("bank", String::class.java)!!
                val acc  = row.get("account_id", String::class.java)!!
                val nick = row.get("nickname", String::class.java)

                val isReserve: Boolean =
                    row.get("is_reserve", Boolean::class.javaObjectType) == java.lang.Boolean.TRUE
                val isSalary: Boolean =
                    row.get("is_salary_source", Boolean::class.javaObjectType) == java.lang.Boolean.TRUE

                AccountRef(
                    bank = BankCode(bank),
                    accountId = AccountId(acc),
                    nickname = nick,
                    isReserve = isReserve,
                    isSalarySource = isSalary
                )
            }
            .one()
            .awaitSingleOrNull()
    }

    override suspend fun saveAccountRef(ref: AccountRef, userId: UserId) {
        val sql = """
      INSERT INTO accounts (user_id, bank, account_id, nickname, is_reserve, is_salary_source)
      VALUES (:userId, :bank, :acc, :nick, :isReserve, :isSalary)
      ON CONFLICT (user_id, bank, account_id) DO UPDATE SET
        nickname        = EXCLUDED.nickname,
        is_reserve      = EXCLUDED.is_reserve,
        is_salary_source= EXCLUDED.is_salary_source
    """.trimIndent()

        db.sql(sql)
            .bind("userId", userId.value)
            .bind("bank", ref.bank.value)
            .bind("acc", ref.accountId.value)
            .bind("nick", ref.nickname)
            .bind("isReserve", ref.isReserve)
            .bind("isSalary", ref.isSalarySource)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun getSalaryAccount(userId: UserId): AccountRef? {
        val sql = """
      SELECT bank, account_id, nickname, is_reserve, is_salary_source
      FROM accounts
      WHERE user_id = :userId AND is_salary_source = true
      ORDER BY updated_at DESC NULLS LAST
      LIMIT 1
    """.trimIndent()

        return db.sql(sql)
            .bind("userId", userId.value)
            .map { row, _ ->
                val bank = row.get("bank", String::class.java)!!
                val acc  = row.get("account_id", String::class.java)!!
                val nick = row.get("nickname", String::class.java)

                val isReserve: Boolean =
                    row.get("is_reserve", Boolean::class.javaObjectType) == java.lang.Boolean.TRUE
                val isSalary: Boolean =
                    row.get("is_salary_source", Boolean::class.javaObjectType) == java.lang.Boolean.TRUE

                AccountRef(
                    bank = BankCode(bank),
                    accountId = AccountId(acc),
                    nickname = nick,
                    isReserve = isReserve,
                    isSalarySource = isSalary
                )
            }
            .one()
            .awaitSingleOrNull()
    }

    override suspend fun setSalaryAccount(userId: UserId, account: AccountRef) {
        // 1) снять флаг со всех аккаунтов пользователя
        db.sql("""UPDATE accounts SET is_salary_source = false WHERE user_id = :userId""")
            .bind("userId", userId.value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        // 2) апсертом обозначить выбранный аккаунт как зарплатный (создать, если нет)
        val upsert = """
      INSERT INTO accounts (user_id, bank, account_id, nickname, is_reserve, is_salary_source)
      VALUES (:userId, :bank, :acc, :nick, false, true)
      ON CONFLICT (user_id, bank, account_id) DO UPDATE SET
        nickname         = COALESCE(EXCLUDED.nickname, accounts.nickname),
        is_salary_source = true
    """.trimIndent()

        db.sql(upsert)
            .bind("userId", userId.value)
            .bind("bank", account.bank.value)
            .bind("acc", account.accountId.value)
            .bind("nick", account.nickname)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
