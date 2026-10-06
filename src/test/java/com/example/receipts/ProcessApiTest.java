package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ProcessApiTest extends ApiTestBase {

    // Required test 1
    @Test
    void cleanFixtureMatchesGold() throws Exception {
        JsonNode txn = process(upload("receipt-clean.txt"));
        Gold.assertMatches(json, txn, "receipt-clean");
        assertThat(txn.get("itemize_status").asText()).isEqualTo("COMPLETE");
    }

    // Required test 2
    @Test
    void mismatchKeepsPrintedTotalNoBalancingLineNeedsReview() throws Exception {
        JsonNode txn = process(upload("receipt-mismatch.txt"));
        Gold.assertMatches(json, txn, "receipt-mismatch");
        assertThat(Gold.dec(txn.get("grand_total"))).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(txn.get("line_items")).hasSize(2);
        assertThat(txn.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(Gold.dec(txn.get("reconciliation").get("difference"))).isEqualByComparingTo(new BigDecimal("6.60"));
    }

    // Required test 3
    @Test
    void taxOnlyFixtureNeedsReviewWithNoItems() throws Exception {
        JsonNode txn = process(upload("receipt-tax-only.txt"));
        Gold.assertMatches(json, txn, "receipt-tax-only");
        assertThat(txn.get("line_items")).isEmpty();
        assertThat(txn.get("taxes").get(0).get("inclusive").asBoolean()).isTrue();
    }

    // Required test 5
    @Test
    void reprocessingSequentiallyKeepsOneTransactionAndNoDuplicateRows() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        long first = process(receiptId).get("id").asLong();
        long second = process(receiptId).get("id").asLong();
        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE receipt_id = ?", receiptId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM taxes WHERE transaction_id = ?", first)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM line_items WHERE transaction_id = ?", first)).isEqualTo(3);
    }

    @Test
    void reprocessingDoesNotConsumeTransactionIds() throws Exception {
        long first = upload("receipt-clean.txt");
        long firstTxn = process(first).get("id").asLong();
        process(first);
        process(first);
        long secondTxn = process(upload("receipt-mismatch.txt")).get("id").asLong();
        assertThat(secondTxn).isEqualTo(firstTxn + 1);
    }

    // Required test 7
    @Test
    void receiptAndTransactionLookupAgreeOnTransactionId() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        long txnId = process(receiptId).get("id").asLong();
        JsonNode receipt = getJson("/receipts/{id}", receiptId);
        assertThat(receipt.get("processed").asBoolean()).isTrue();
        assertThat(receipt.get("transaction_id").asLong()).isEqualTo(txnId);
        JsonNode byReceipt = getJson("/transactions?receipt_id={id}", receiptId);
        assertThat(byReceipt.isObject()).isTrue();
        assertThat(byReceipt.get("id").asLong()).isEqualTo(txnId);
        assertThat(byReceipt.get("receipt_id").asLong()).isEqualTo(receiptId);
        assertThat(getJson("/transactions/{id}", txnId).get("receipt_id").asLong()).isEqualTo(receiptId);
    }

    // Required test 8
    @Test
    void deletingProcessedReceiptIs409AndTransactionSurvives() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        long txnId = process(receiptId).get("id").asLong();
        mvc.perform(delete("/receipts/{id}", receiptId)).andExpect(status().isConflict());
        mvc.perform(get("/transactions/{id}", txnId)).andExpect(status().isOk());
        mvc.perform(get("/receipts/{id}", receiptId)).andExpect(status().isOk());
    }

    @Test
    void databaseForeignKeyAlsoBlocksDeletingProcessedReceipt() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        process(receiptId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM receipts WHERE id = ?", receiptId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void storedOcrIsReturnedAfterProcess() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        process(receiptId);
        mvc.perform(get("/receipts/{id}/ocr", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocr_text").value(new String(fixture("receipt-clean.txt"), StandardCharsets.UTF_8)));
    }

    @Test
    void missingHeaderFieldsIs422AndNothingPersisted() throws Exception {
        long receiptId = upload("no-merchant.txt", "DATE: 2026-03-12\nCURRENCY: EUR\nTOTAL   5.00\n".getBytes(StandardCharsets.UTF_8), "text/plain");
        mvc.perform(post("/receipts/{id}/process", receiptId)).andExpect(status().isUnprocessableEntity());
        assertThat(count("SELECT COUNT(*) FROM transactions")).isZero();
        mvc.perform(get("/receipts/{id}", receiptId)).andExpect(jsonPath("$.processed").value(false));
    }

    @Test
    void invalidDateIs422AndNothingPersisted() throws Exception {
        long receiptId = upload("bad-date.txt",
                "MERCHANT: X\nDATE: 2026-02-30\nCURRENCY: EUR\nTOTAL   5.00\n".getBytes(StandardCharsets.UTF_8), "text/plain");
        mvc.perform(post("/receipts/{id}/process", receiptId)).andExpect(status().isUnprocessableEntity());
        assertThat(count("SELECT COUNT(*) FROM transactions")).isZero();
    }

    @Test
    void processingNonTextUploadIs422() throws Exception {
        long receiptId = upload("receipt.pdf", "%PDF-1.4 not really".getBytes(StandardCharsets.UTF_8), "application/pdf");
        mvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("stub")));
        assertThat(count("SELECT COUNT(*) FROM transactions")).isZero();
    }

    @Test
    void unknownIdsAre404() throws Exception {
        mvc.perform(post("/receipts/{id}/process", 999_999)).andExpect(status().isNotFound());
        mvc.perform(get("/transactions/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(get("/transactions").param("receipt_id", "999999")).andExpect(status().isNotFound());
    }
}
