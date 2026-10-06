package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ReceiptApiTest extends ApiTestBase {

    @Test
    void uploadStoresReceiptAndGetReturnsMetadata() throws Exception {
        long id = upload("receipt-clean.txt");
        mvc.perform(get("/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.original_filename").value("receipt-clean.txt"))
                .andExpect(jsonPath("$.uploaded_at").isNotEmpty())
                .andExpect(jsonPath("$.processed").value(false))
                .andExpect(jsonPath("$.transaction_id").value(nullValue()));
        String path = jdbc.queryForObject("SELECT storage_path FROM receipts WHERE id = ?", String.class, id);
        assertThat(Files.readAllBytes(Path.of(path))).isEqualTo(fixture("receipt-clean.txt"));
    }

    @Test
    void identicalBytesReturnExistingReceiptWith200() throws Exception {
        long id = upload("receipt-clean.txt");
        mvc.perform(multipart("/receipts").file(new MockMultipartFile("file", "renamed.txt", "text/plain", fixture("receipt-clean.txt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipt_id").value(id))
                .andExpect(jsonPath("$.duplicate").value(true));
        assertThat(count("SELECT COUNT(*) FROM receipts")).isEqualTo(1);
    }

    @Test
    void emptyFileIs400() throws Exception {
        mvc.perform(multipart("/receipts").file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest());
        assertThat(count("SELECT COUNT(*) FROM receipts")).isZero();
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mvc.perform(multipart("/receipts").param("not_file", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownReceiptIs404() throws Exception {
        mvc.perform(get("/receipts/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(get("/receipts/{id}/ocr", 999_999)).andExpect(status().isNotFound());
        mvc.perform(delete("/receipts/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void ocrBeforeProcessIs409() throws Exception {
        long id = upload("receipt-clean.txt");
        mvc.perform(get("/receipts/{id}/ocr", id)).andExpect(status().isConflict());
    }

    @Test
    void deleteUnprocessedRemovesRowAndFile() throws Exception {
        long id = upload("receipt-mismatch.txt");
        String path = jdbc.queryForObject("SELECT storage_path FROM receipts WHERE id = ?", String.class, id);
        mvc.perform(delete("/receipts/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/receipts/{id}", id)).andExpect(status().isNotFound());
        assertThat(Files.exists(Path.of(path))).isFalse();
    }
}
