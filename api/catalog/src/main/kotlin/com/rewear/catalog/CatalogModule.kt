package com.rewear.catalog

import com.rewear.catalog.categories.CategoryQueries
import com.rewear.catalog.categories.internal.CategoryQueriesImpl
import com.rewear.catalog.favorites.FavoriteQueries
import com.rewear.catalog.favorites.internal.FavoriteQueriesImpl
import com.rewear.catalog.listings.ListingQueries
import com.rewear.catalog.listings.internal.ListingQueriesImpl
import org.koin.dsl.module

val catalogModule = module {
    single<ListingQueries> { ListingQueriesImpl(get()) }
    single<CategoryQueries> { CategoryQueriesImpl() }
    single<FavoriteQueries> { FavoriteQueriesImpl() }
}
