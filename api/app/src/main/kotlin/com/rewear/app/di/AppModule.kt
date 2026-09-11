package com.rewear.app.di

import com.rewear.app.db.Database
import org.koin.dsl.module

fun appModule(database: Database) = module {
    single { database }
}
