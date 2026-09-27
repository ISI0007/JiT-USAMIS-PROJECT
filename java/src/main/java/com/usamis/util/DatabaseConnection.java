package com.usamis.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * HikariCP connection pool singleton.
 * Config is read from environment variables first, then db.properties.
 *
 * WHY: A new Connection per request is expensive (~50ms).
 * HikariCP pools keeps connections alive and reuses them,
 * reducing latency to <1ms per borrow.
 */
public final class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static volatile HikariDataSource dataSource;

    private DatabaseConnection() {}

    public static void init() {
        if (dataSource != null) return;
        synchronized (DatabaseConnection.class) {
            if (dataSource != null) return;
            try {
                Properties props = new Properties();
                try (InputStream is = DatabaseConnection.class
                        .getClassLoader().getResourceAsStream("db.properties")) {
                    if (is != null) props.load(is);
                }

                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl(value("DB_URL", props, "db.url"));
                cfg.setUsername(value("DB_USER", props, "db.user"));
                cfg.setPassword(value("DB_PASSWORD", props, "db.password"));
                cfg.setDriverClassName("org.postgresql.Driver");

                // Pool sizing — for a university MIS: 10 connections handles ~50 concurrent users
                cfg.setMaximumPoolSize(Integer.parseInt(value("DB_POOL_MAX", props, "db.pool.max", "10")));
                cfg.setMinimumIdle(Integer.parseInt(value("DB_POOL_MIN", props, "db.pool.min", "2")));
                cfg.setConnectionTimeout(30_000L);
                cfg.setIdleTimeout(600_000L);
                cfg.setMaxLifetime(1_800_000L);
                cfg.setPoolName("USAMIS-Pool");

                // Validate connection on borrow
                cfg.setConnectionTestQuery("SELECT 1");

                dataSource = new HikariDataSource(cfg);
                log.info("HikariCP pool initialized: {} connections", cfg.getMaximumPoolSize());
            } catch (Exception e) {
                log.error("Failed to initialize database pool", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }
    }

    private static String value(String env, Properties props, String key) {
        return value(env, props, key, null);
    }

    private static String value(String env, Properties props, String key, String fallback) {
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        String fromProps = props.getProperty(key);
        if (fromProps != null && !fromProps.isBlank()) return fromProps;
        if (fallback != null) return fallback;
        throw new IllegalStateException("Missing database configuration: " + env);
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) init();
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database pool closed");
        }
    }
}
