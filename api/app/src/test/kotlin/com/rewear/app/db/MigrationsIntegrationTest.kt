package com.rewear.app.db

import com.rewear.app.config.DatabaseConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
class MigrationsIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16.4-alpine")
    }

    private fun config() = DatabaseConfig(
        host = postgres.host,
        port = postgres.firstMappedPort,
        name = postgres.databaseName,
        user = postgres.username,
        password = postgres.password,
    )

    @Test
    fun `given an empty database when migrations run then per-module history tables exist and a round-trip query succeeds`() =
        runBlocking {
            val config = config()

            runMigrations(config)

            val database = Database.connect(config)

            assertEquals(1, database.ping())

            val historyTables = database.query {
                val rows = exec(
                    "SELECT count(*)::int FROM information_schema.tables " +
                        "WHERE table_name = ANY(ARRAY['flyway_schema_history_users', 'flyway_schema_history_catalog'])",
                    emptyList(),
                    StatementType.SELECT,
                ) { row -> (row.get(0) as Number).toInt() }
                rows?.first() ?: 0
            }

            assertEquals(2, historyTables)
        }
}
