package org.elly.core.repo

import org.elly.core.model.UserId
import org.elly.core.model.UserRow

interface IUsersRepo {

    suspend fun getEmail(userId: UserId): String?

    suspend fun findByEmail(email: String): UserRow?

    suspend fun create(email: String, passwordHash: String): UserRow
}