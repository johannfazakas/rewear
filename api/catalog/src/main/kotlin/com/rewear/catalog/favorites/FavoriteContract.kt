package com.rewear.catalog.favorites

import com.rewear.catalog.listings.ListingId
import com.rewear.users.UserId

data class Favorite(
    val userId: UserId,
    val listingId: ListingId,
)

interface FavoriteQueries {
    suspend fun listFor(userId: UserId): List<Favorite>
}
