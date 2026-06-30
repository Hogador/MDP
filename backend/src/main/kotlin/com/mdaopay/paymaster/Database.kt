package com.mdaopay.paymaster

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import javax.sql.DataSource

private val dbLog = LoggerFactory.getLogger("Database")

fun createDataSource(databaseUrl: String): DataSource {
    val config = HikariConfig().apply {
        jdbcUrl = databaseUrl
        maximumPoolSize = 25
        minimumIdle = 2
        idleTimeout = 30_000
        connectionTimeout = 5_000
        maxLifetime = 600_000
    }
    return HikariDataSource(config)
}

fun runMigrations(databaseUrl: String) {
    try {
        Flyway.configure()
            .dataSource(databaseUrl, "", "")
            .locations("classpath:db/migration")
            .load()
            .migrate()
        dbLog.info("Database migrations applied successfully")
    } catch (e: Exception) {
        dbLog.error("Migration failed reason={}", LogSanitizer.sanitizeError(e))
        if (dbLog.isDebugEnabled) dbLog.debug("Migration failed details", e)
        throw e
    }
}
