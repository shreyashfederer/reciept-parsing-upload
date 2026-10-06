package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TransactionApiTest extends ApiTestBase {

    private static final String MISMATCH_UNBALANCED = """
            {"items": [{"description": "Water", "amount": 4.00},
                       {"description": "Snacks", "amount": 6.00},
                       {"description": "Minibar", "amount": 5.00}]}""";
    private static final String MISMATCH_BALANCED = """
            {"items": [{"description": "Water", "amount": 4.00},
                       {"description": "Snacks", "amount": 6.00},
                       {"description": "Minibar", "amount": 6.60}]}""";

    private ResultActions patchItems(long txnId, String body) throws Exception {
        return mvc.perform(patch("/transactions/{id}/items", txnId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Required test 4
    @Test
    void patchThatDoesNotReconcileIs409AndPersistsNothing() throws Exception {
        long txnId = process(upload("receipt-mismatch.txt")).get("id").asLong();

        patchItems(txnId, MISMATCH_UNBALANCED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reconciliation.reconciles").value(false))
                .andExpect(jsonPath("$.reconciliation.items_total").value(15.00))
                .andExpect(jsonPath("$.reconciliation.exclusive_tax_total").value(1.90))
                .andExpect(jsonPath("$.reconciliation.grand_total").value(18.50))
                .andExpect(jsonPath("$.reconciliation.difference").value(1.60));

        JsonNode after = getJson("/transactions/{id}", txnId);
        assertThat(after.get("line_items")).hasSize(2);
        assertThat(Gold.dec(after.get("grand_total"))).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(after.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(count("SELECT COUNT(*) FROM line_items WHERE transaction_id = ?", txnId)).isEqualTo(2);
    }

    // Required test 9
    @Test
    void completeOnMismatchIs409AndStatusStaysNeedsReview() throws Exception {
        long txnId = process(upload("receipt-mismatch.txt")).get("id").asLong();
        mvc.perform(post("/transactions/{id}/complete", txnId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reconciliation.difference").value(6.60));
        assertThat(getJson("/transactions/{id}", txnId).get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void reconcilingPatchThenCompleteSetsComplete() throws Exception {
        long txnId = process(upload("receipt-mismatch.txt")).get("id").asLong();
        patchItems(txnId, MISMATCH_BALANCED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line_items.length()").value(3))
                .andExpect(jsonPath("$.itemize_status").value("NEEDS_REVIEW"));
        mvc.perform(post("/transactions/{id}/complete", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"));
    }

    @Test
    void taxOnlyCanBeItemizedByUserWithGrossAmount() throws Exception {
        long txnId = process(upload("receipt-tax-only.txt")).get("id").asLong();
        mvc.perform(post("/transactions/{id}/complete", txnId)).andExpect(status().isConflict());
        patchItems(txnId, """
                {"items": [{"description": "Trip fare", "amount": 24.00, "tax_amount": 3.83}]}""")
                .andExpect(status().isOk());
        mvc.perform(post("/transactions/{id}/complete", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"));
    }

    @Test
    void reitemizeReplacesOnlyItemsFromStoredOcr() throws Exception {
        long receiptId = upload("receipt-mismatch.txt");
        JsonNode before = process(receiptId);
        long txnId = before.get("id").asLong();
        patchItems(txnId, MISMATCH_BALANCED).andExpect(status().isOk());

        JsonNode after = json.readTree(mvc.perform(post("/transactions/{id}/itemize", txnId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(after.get("id").asLong()).isEqualTo(txnId);
        assertThat(after.get("line_items")).hasSize(2);
        assertThat(after.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(after.get("merchant")).isEqualTo(before.get("merchant"));
        assertThat(after.get("date")).isEqualTo(before.get("date"));
        assertThat(after.get("currency")).isEqualTo(before.get("currency"));
        assertThat(after.get("grand_total")).isEqualTo(before.get("grand_total"));
        assertThat(after.get("taxes")).isEqualTo(before.get("taxes"));
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE receipt_id = ?", receiptId)).isEqualTo(1);
    }

    @Test
    void reitemizeCleanIsComplete() throws Exception {
        long txnId = process(upload("receipt-clean.txt")).get("id").asLong();
        mvc.perform(post("/transactions/{id}/itemize", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"))
                .andExpect(jsonPath("$.line_items.length()").value(3));
    }

    @Test
    void patchValidationErrorsAre400() throws Exception {
        long txnId = process(upload("receipt-clean.txt")).get("id").asLong();
        patchItems(txnId, """
                {"items": [{"description": "Espresso", "amount": 3.505}]}""").andExpect(status().isBadRequest());
        patchItems(txnId, """
                {"items": [{"description": " ", "amount": 3.50}]}""").andExpect(status().isBadRequest());
        patchItems(txnId, "{}").andExpect(status().isBadRequest());
        patchItems(txnId, """
                {"items": [null]}""").andExpect(status().isBadRequest());
        patchItems(txnId, """
                {"items": [{"description": "Espresso", "amount": 17.85, "quantity": 1.9}]}""").andExpect(status().isBadRequest());
        assertThat(count("SELECT COUNT(*) FROM line_items WHERE transaction_id = ?", txnId)).isEqualTo(3);
    }

    @Test
    void unknownTransactionIs404() throws Exception {
        mvc.perform(post("/transactions/{id}/itemize", 999_999)).andExpect(status().isNotFound());
        mvc.perform(post("/transactions/{id}/complete", 999_999)).andExpect(status().isNotFound());
        patchItems(999_999, MISMATCH_BALANCED).andExpect(status().isNotFound());
    }
}
