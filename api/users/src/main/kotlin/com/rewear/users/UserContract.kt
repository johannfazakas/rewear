package com.rewear.users

import com.rewear.common.EntityId
import java.util.UUID

@JvmInline
value class UserId(override val value: UUID) : EntityId

data class UserProfile(
    val id: UserId,
    val displayName: String,
    val blocked: Boolean,
)

interface UserQueries {
    suspend fun findById(id: UserId): UserProfile?

    suspend fun exists(id: UserId): Boolean
}
