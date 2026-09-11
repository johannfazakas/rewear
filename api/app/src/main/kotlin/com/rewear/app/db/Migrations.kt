package com.rewear.app.db

import com.rewear.app.config.DatabaseConfig
import org.flywaydb.core.Flyway

data class ModuleMigrations(val name: String, val historyTable: String)

val moduleMigrations = listOf(
    ModuleMigrations(name = "users", historyTable = "flyway_schema_history_users"),
    ModuleMigrations(name = "catalog", historyTable = "flyway_schema_history_catalog"),
)

private fun locationPrefix(): String =
    System.getenv("MIGRATIONS_LOCATION_PREFIX")?.takeIf { it.isNotBlank() } ?: "classpath:db/migration"

fun runMigrations(config: DatabaseConfig) {
    Class.forName("org.postgresql.Driver")
    val prefix = locationPrefix()
    moduleMigrations.forEach { module ->
        Flyway.configure()
            .dataSource(config.jdbcUrl, config.user, config.password)
            .locations("$prefix/${module.name}")
            .table(module.historyTable)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
    }
}
