package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elly.core.model.UserId
import org.elly.core.model.UserRow
import org.elly.core.repo.IUsersRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class UsersRepo(private val db: DatabaseClient) : IUsersRepo {

    override suspend fun getEmail(userId: UserId): String? =
        db.sql("select email from users where id = :id")
            .bind("id", userId.value)
            .map { row -> row.get("email", String::class.java) }
            .one()
            .awaitSingleOrNull()

    override suspend fun findByEmail(email: String): UserRow? =
        db.sql("SELECT id,email,password_hash,roles FROM users WHERE email=:e LIMIT 1")
            .bind("e", email)
            .map { r, _ ->
                UserRow(
                    id = (r.get("id", Long::class.java)!!).toLong(),
                    email = r.get("email", String::class.java)!!,
                    passwordHash = r.get("password_hash", String::class.java)!!,
                    roles = r.get("roles", String::class.java) ?: "ROLE_USER"
                )
            }.one().awaitSingleOrNull()

    override suspend fun create(email: String, passwordHash: String): UserRow {
        val sql = """
      INSERT INTO users(email,password_hash,roles)
      VALUES (:e,:p,'ROLE_USER')
      RETURNING id,email,password_hash,roles
    """.trimIndent()
        return db.sql(sql)
            .bind("e", email)
            .bind("p", passwordHash)
            .map { r, _ ->
                UserRow(
                    id = (r.get("id", Long::class.java)!!).toLong(),
                    email = r.get("email", String::class.java)!!,
                    passwordHash = r.get("password_hash", String::class.java)!!,
                    roles = r.get("roles", String::class.java) ?: "ROLE_USER"
                )
            }.one().awaitSingle()
    }
}
