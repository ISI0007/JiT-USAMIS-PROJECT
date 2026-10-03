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
 * Config is read from db.properties (classpath).
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
                    if (is == null) throw new RuntimeException("db.properties not found on classpath");
                    props.load(is);
                }

                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl(props.getProperty("db.url"));
                cfg.setUsername(props.getProperty("db.user"));
                cfg.setPassword(props.getProperty("db.password"));
                cfg.setDriverClassName("org.postgresql.Driver");

                // Pool sizing — for a university MIS: 10 connections handles ~50 concurrent users
                cfg.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.max", "10")));
                cfg.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.min", "2")));
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
