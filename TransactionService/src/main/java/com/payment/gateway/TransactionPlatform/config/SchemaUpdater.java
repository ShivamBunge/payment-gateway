package com.payment.gateway.TransactionPlatform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaUpdater {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpdater.class);
    private final JdbcTemplate jdbcTemplate;

    public SchemaUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureOutboxColumns() {
        try {
            // Add columns if they don't exist (Postgres syntax)
            jdbcTemplate.execute("ALTER TABLE outbox ADD COLUMN IF NOT EXISTS attempts integer DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE outbox ADD COLUMN IF NOT EXISTS last_error text");
            jdbcTemplate.execute("ALTER TABLE outbox ADD COLUMN IF NOT EXISTS processed_at timestamp");
            log.info("SchemaUpdater: ensured outbox columns attempts,last_error,processed_at");
        } catch (Exception e) {
            log.warn("SchemaUpdater: could not ensure outbox columns: {}", e.getMessage());
        }
    }
}

