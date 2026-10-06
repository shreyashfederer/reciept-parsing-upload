package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.receipts.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ProcessAtomicityTest extends ApiTestBase {

    @MockitoSpyBean
    TransactionRepository transactionRepository;

    @Test
    void crashAfterHeaderAndTaxesRollsBackEverything() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        doThrow(new IllegalStateException("simulated crash while writing items"))
                .when(transactionRepository).replaceItems(anyLong(), anyList());

        mvc.perform(post("/receipts/{id}/process", receiptId)).andExpect(status().isInternalServerError());

        assertThat(count("SELECT COUNT(*) FROM transactions")).isZero();
        assertThat(count("SELECT COUNT(*) FROM taxes")).isZero();
        assertThat(count("SELECT COUNT(*) FROM line_items")).isZero();
        assertThat(count("SELECT COUNT(*) FROM receipts WHERE id = ? AND processed_at IS NULL", receiptId)).isEqualTo(1);
        mvc.perform(get("/receipts/{id}/ocr", receiptId)).andExpect(status().isConflict());
    }
}
