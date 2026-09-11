package com.rewear.app.config

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String,
) {
    val jdbcUrl: String get() = "jdbc:postgresql://$host:$port/$name"
    val r2dbcUrl: String get() = "r2dbc:postgresql://$host:$port/$name"
}

data class StorageConfig(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
)

data class AuthConfig(
    val issuer: String,
    val jwksUrl: String,
)

data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val storage: StorageConfig,
    val auth: AuthConfig,
) {
    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv): AppConfig {
            val missing = mutableListOf<String>()

            fun required(name: String): String {
                val value = env(name)
                if (value.isNullOrBlank()) {
                    missing += name
                    return ""
                }
                return value
            }

            fun optional(name: String, default: String): String =
                env(name)?.takeIf { it.isNotBlank() } ?: default

            val config = AppConfig(
                port = optional("PORT", "8080").toInt(),
                database = DatabaseConfig(
                    host = required("DATABASE_HOST"),
                    port = optional("DATABASE_PORT", "5432").toInt(),
                    name = required("DATABASE_NAME"),
                    user = required("DATABASE_USER"),
                    password = required("DATABASE_PASSWORD"),
                ),
                storage = StorageConfig(
                    endpoint = required("S3_ENDPOINT"),
                    region = optional("S3_REGION", "garage"),
                    accessKey = required("S3_ACCESS_KEY"),
                    secretKey = required("S3_SECRET_KEY"),
                    bucket = required("S3_BUCKET"),
                ),
                auth = AuthConfig(
                    issuer = required("ZITADEL_ISSUER"),
                    jwksUrl = required("ZITADEL_JWKS_URL"),
                ),
            )

            check(missing.isEmpty()) {
                "Missing required environment variables: ${missing.joinToString(", ")}"
            }
            return config
        }
    }
}
