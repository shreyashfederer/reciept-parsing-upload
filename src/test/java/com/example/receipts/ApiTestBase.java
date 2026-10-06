package com.example.receipts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    protected static byte[] fixture(String name) {
        try {
            return Files.readAllBytes(Path.of("fixtures", "task-a", name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected long upload(String fixtureName) throws Exception {
        return upload(fixtureName, fixture(fixtureName), "text/plain");
    }

    protected long upload(String filename, byte[] bytes, String contentType) throws Exception {
        MvcResult res = mvc.perform(multipart("/receipts").file(new MockMultipartFile("file", filename, contentType, bytes)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("receipt_id").asLong();
    }

    protected JsonNode process(long receiptId) throws Exception {
        String body = mvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    protected JsonNode getJson(String url, Object... vars) throws Exception {
        String body = mvc.perform(get(url, vars))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
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
