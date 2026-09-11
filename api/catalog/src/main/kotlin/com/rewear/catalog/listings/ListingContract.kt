package com.rewear.catalog.listings

import com.rewear.common.EntityId
import java.util.UUID

@JvmInline
value class ListingId(override val value: UUID) : EntityId

interface ListingQueries {
    suspend fun count(): Long
}
