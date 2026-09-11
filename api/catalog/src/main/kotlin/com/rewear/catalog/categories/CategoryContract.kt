package com.rewear.catalog.categories

import com.rewear.common.EntityId
import java.util.UUID

@JvmInline
value class CategoryId(override val value: UUID) : EntityId

interface CategoryQueries {
    suspend fun count(): Long
}
