package com.rewear.users.internal

import com.rewear.users.UserId
import com.rewear.users.UserProfile
import com.rewear.users.UserQueries

internal class UserQueriesImpl : UserQueries {
    override suspend fun findById(id: UserId): UserProfile? = null

    override suspend fun exists(id: UserId): Boolean = false
}
