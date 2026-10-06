package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class HealthApiTest extends ApiTestBase {

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void databaseIsAFileWithForeignKeysAndWal() {
        assertThat(Files.exists(DATA_DIR.resolve("receipts.db"))).isTrue();
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class))
                .contains("receipts", "transactions", "taxes", "line_items");
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("PRAGMA journal_mode", String.class)).isEqualToIgnoringCase("wal");
    }
}
