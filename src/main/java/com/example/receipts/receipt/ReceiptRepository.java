package com.example.receipts.receipt;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReceiptRepository {
    private static final RowMapper<Receipt> MAPPER = (rs, n) -> new Receipt(
            rs.getLong("id"),
            rs.getString("original_filename"),
            rs.getString("content_type"),
            rs.getString("content_sha256"),
            rs.getLong("size_bytes"),
            rs.getString("storage_path"),
            Instant.parse(rs.getString("uploaded_at")),
            rs.getString("ocr_text"),
            rs.getString("processed_at") == null ? null : Instant.parse(rs.getString("processed_at")));

    private final JdbcTemplate jdbc;

    public ReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if a row was inserted; false if a receipt with the same bytes already exists. */
    public boolean insertIfAbsent(String filename, String contentType, String sha256, long sizeBytes,
                                  String storagePath, Instant uploadedAt) {
        return jdbc.update("""
                INSERT INTO receipts (original_filename, content_type, content_sha256, size_bytes, storage_path, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(content_sha256) DO NOTHING
                """, filename, contentType, sha256, sizeBytes, storagePath, uploadedAt.toString()) == 1;
    }

    public Optional<Receipt> findById(long id) {
        return jdbc.query("SELECT * FROM receipts WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Receipt> findBySha256(String sha256) {
        return jdbc.query("SELECT * FROM receipts WHERE content_sha256 = ?", MAPPER, sha256).stream().findFirst();
    }

    public void markProcessed(long id, String ocrText, Instant at) {
        jdbc.update("UPDATE receipts SET ocr_text = ?, processed_at = ? WHERE id = ?", ocrText, at.toString(), id);
    }

    public int deleteById(long id) {
        return jdbc.update("DELETE FROM receipts WHERE id = ?", id);
    }
}
