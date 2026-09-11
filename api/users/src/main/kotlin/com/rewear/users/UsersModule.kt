package com.rewear.users

import com.rewear.users.internal.UserQueriesImpl
import org.koin.dsl.module

val usersModule = module {
    single<UserQueries> { UserQueriesImpl() }
}
