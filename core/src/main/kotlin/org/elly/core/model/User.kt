package org.elly.core.model

data class UserRow(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val roles: String
)