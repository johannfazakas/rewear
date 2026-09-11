package com.rewear.app.db

import com.rewear.app.config.DatabaseConfig
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlinx.coroutines.flow.first

fun connectionPool(config: DatabaseConfig): ConnectionPool {
    val factory = PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(config.host)
            .port(config.port)
            .database(config.name)
            .username(config.user)
            .password(config.password)
            .build(),
    )
    val poolConfig = ConnectionPoolConfiguration.builder(factory)
        .maxSize(10)
        .initialSize(2)
        .build()
    return ConnectionPool(poolConfig)
}

class Database(private val exposed: R2dbcDatabase) {
    suspend fun <T> query(block: suspend org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.() -> T): T =
        suspendTransaction(db = exposed) { block() }

    suspend fun ping(): Int =
        query {
            val rows = exec("SELECT 1", emptyList(), StatementType.SELECT) { row ->
                (row.get(0) as Number).toInt()
            }
            rows?.first() ?: error("SELECT 1 returned no rows")
        }

    companion object {
        fun connect(config: DatabaseConfig): Database {
            val exposedConfig = R2dbcDatabaseConfig { setUrl(config.r2dbcUrl) }
            return Database(R2dbcDatabase.connect(connectionPool(config), exposedConfig))
        }
    }
}
