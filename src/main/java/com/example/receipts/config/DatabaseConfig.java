package com.example.receipts.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    /**
     * WAL lets readers run alongside the single writer. busy_timeout makes a second writer wait instead of failing.
     * transaction_mode=IMMEDIATE takes the write lock at BEGIN, so two concurrent read-then-write
     * transactions serialize instead of deadlocking on lock upgrade (SQLITE_BUSY).
     * foreign_keys is off by default in SQLite and must be enabled per connection.
     */
    @Bean
    DataSource dataSource(AppProperties props) throws IOException {
        Files.createDirectories(props.dataDir());
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + props.dataDir().resolve("receipts.db").toAbsolutePath()
                + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=true&transaction_mode=IMMEDIATE");
        cfg.setMaximumPoolSize(props.dbPoolSize());
        return new HikariDataSource(cfg);
    }
}
