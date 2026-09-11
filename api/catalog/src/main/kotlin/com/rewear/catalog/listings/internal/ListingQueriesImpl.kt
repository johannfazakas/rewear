package com.rewear.catalog.listings.internal

import com.rewear.catalog.listings.ListingQueries
import com.rewear.users.UserQueries

internal class ListingQueriesImpl(
    private val userQueries: UserQueries,
) : ListingQueries {
    override suspend fun count(): Long = 0
}
