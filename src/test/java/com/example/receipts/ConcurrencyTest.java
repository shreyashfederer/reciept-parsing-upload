package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

class ConcurrencyTest extends ApiTestBase {
    private static final int THREADS = 8;

    // Required test 6
    @RepeatedTest(3)
    void concurrentProcessCreatesExactlyOneTransaction() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        List<ResponseEntity<String>> responses = runConcurrently(
                () -> http.postForEntity("/receipts/" + receiptId + "/process", null, String.class));

        Set<Long> ids = new HashSet<>();
        for (ResponseEntity<String> res : responses) {
            assertThat(res.getStatusCode().value()).as(res.getBody()).isEqualTo(200);
            ids.add(json.readTree(res.getBody()).get("id").asLong());
        }
        assertThat(ids).hasSize(1);
        long txnId = ids.iterator().next();
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE receipt_id = ?", receiptId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM taxes WHERE transaction_id = ?", txnId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM line_items WHERE transaction_id = ?", txnId)).isEqualTo(3);
    }

    @Test
    void uniqueConstraintRejectsSecondTransactionForSameReceipt() throws Exception {
        long receiptId = upload("receipt-clean.txt");
        process(receiptId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (receipt_id, merchant, txn_date, currency, grand_total_minor, itemize_status, created_at, updated_at)
                VALUES (?, 'Dup', '2026-01-01', 'EUR', 1, 'NEEDS_REVIEW', 'x', 'x')
                """, receiptId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void concurrentDuplicateUploadsCreateOneReceipt() throws Exception {
        byte[] bytes = fixture("receipt-mismatch.txt");
        List<ResponseEntity<String>> responses = runConcurrently(() -> {
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return "receipt-mismatch.txt";
                }
            });
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            return http.postForEntity("/receipts", new HttpEntity<>(body, headers), String.class);
        });

        Set<Long> ids = new HashSet<>();
        for (ResponseEntity<String> res : responses) {
            assertThat(res.getStatusCode().value()).as(res.getBody()).isIn(200, 201);
            ids.add(json.readTree(res.getBody()).get("receipt_id").asLong());
        }
        assertThat(ids).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM receipts")).isEqualTo(1);
    }

    private <T> List<T> runConcurrently(Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
