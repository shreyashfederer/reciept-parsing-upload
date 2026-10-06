package com.example.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    protected static final Path DATA_DIR = createTempDir();

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", DATA_DIR::toString);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestRestTemplate http;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM line_items");
        jdbc.update("DELETE FROM taxes");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM receipts");
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("receipts-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
