package com.example.receipts.transaction;

import com.example.receipts.domain.ItemizeStatus;
import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.ReceiptHeader;
import com.example.receipts.domain.TaxLine;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {
    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Create-or-update keyed on the UNIQUE receipt_id; never inserts a second row for a receipt. */
    public long upsertHeader(long receiptId, ReceiptHeader h, ItemizeStatus status, Instant now) {
        jdbc.update("""
                INSERT INTO transactions (receipt_id, merchant, txn_date, currency, grand_total_minor, itemize_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(receipt_id) DO UPDATE SET
                  merchant = excluded.merchant,
                  txn_date = excluded.txn_date,
                  currency = excluded.currency,
                  grand_total_minor = excluded.grand_total_minor,
                  itemize_status = excluded.itemize_status,
                  updated_at = excluded.updated_at
                """, receiptId, h.merchant(), h.date().toString(), h.currency(), h.grandTotalMinor(),
                status.name(), now.toString(), now.toString());
        return findIdByReceiptId(receiptId).orElseThrow();
    }

    public void replaceTaxes(long txnId, List<TaxLine> taxes) {
        jdbc.update("DELETE FROM taxes WHERE transaction_id = ?", txnId);
        for (int i = 0; i < taxes.size(); i++) {
            TaxLine t = taxes.get(i);
            jdbc.update("INSERT INTO taxes (transaction_id, position, name, rate, amount_minor, inclusive) VALUES (?, ?, ?, ?, ?, ?)",
                    txnId, i, t.name(), t.rate().toPlainString(), t.amountMinor(), t.inclusive() ? 1 : 0);
        }
    }

    public void replaceItems(long txnId, List<LineItem> items) {
        jdbc.update("DELETE FROM line_items WHERE transaction_id = ?", txnId);
        for (int i = 0; i < items.size(); i++) {
            LineItem it = items.get(i);
            jdbc.update("INSERT INTO line_items (transaction_id, position, description, amount_minor, quantity, tax_amount_minor) VALUES (?, ?, ?, ?, ?, ?)",
                    txnId, i, it.description(), it.amountMinor(), it.quantity(), it.taxAmountMinor());
        }
    }

    public void updateStatus(long txnId, ItemizeStatus status, Instant now) {
        jdbc.update("UPDATE transactions SET itemize_status = ?, updated_at = ? WHERE id = ?", status.name(), now.toString(), txnId);
    }

    public void touch(long txnId, Instant now) {
        jdbc.update("UPDATE transactions SET updated_at = ? WHERE id = ?", now.toString(), txnId);
    }

    public Optional<Long> findIdByReceiptId(long receiptId) {
        return jdbc.query("SELECT id FROM transactions WHERE receipt_id = ?", (rs, n) -> rs.getLong(1), receiptId)
                .stream().findFirst();
    }

    public Optional<TransactionHeader> findById(long id) {
        return jdbc.query("SELECT * FROM transactions WHERE id = ?", (rs, n) -> new TransactionHeader(
                rs.getLong("id"),
                rs.getLong("receipt_id"),
                new ReceiptHeader(rs.getString("merchant"), LocalDate.parse(rs.getString("txn_date")),
                        rs.getString("currency"), rs.getLong("grand_total_minor")),
                ItemizeStatus.valueOf(rs.getString("itemize_status")),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))), id).stream().findFirst();
    }

    public List<TaxLine> findTaxes(long txnId) {
        return jdbc.query("SELECT * FROM taxes WHERE transaction_id = ? ORDER BY position", (rs, n) -> new TaxLine(
                rs.getString("name"), new BigDecimal(rs.getString("rate")), rs.getLong("amount_minor"),
                rs.getInt("inclusive") == 1), txnId);
    }

    public List<LineItem> findItems(long txnId) {
        return jdbc.query("SELECT * FROM line_items WHERE transaction_id = ? ORDER BY position", (rs, n) -> new LineItem(
                rs.getString("description"), rs.getLong("amount_minor"),
                nullableInt(rs, "quantity"), nullableLong(rs, "tax_amount_minor")), txnId);
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
