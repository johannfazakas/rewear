package com.rewear.catalog.favorites.internal

import com.rewear.catalog.favorites.Favorite
import com.rewear.catalog.favorites.FavoriteQueries
import com.rewear.users.UserId

internal class FavoriteQueriesImpl : FavoriteQueries {
    override suspend fun listFor(userId: UserId): List<Favorite> = emptyList()
}
