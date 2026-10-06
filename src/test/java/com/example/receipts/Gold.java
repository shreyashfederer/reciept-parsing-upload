package com.example.receipts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

/** Compares an API transaction against fixtures/task-a/gold.json. Decimals compare by value (17.85 == 17.850). */
final class Gold {
    private Gold() {}

    static BigDecimal dec(JsonNode n) {
        return new BigDecimal(n.asText());
    }

    static void assertMatches(ObjectMapper mapper, JsonNode txn, String goldKey) throws IOException {
        JsonNode gold = mapper.readTree(Path.of("fixtures", "task-a", "gold.json").toFile()).get(goldKey);
        assertThat(txn.get("merchant").asText()).isEqualTo(gold.get("merchant").asText());
        assertThat(txn.get("date").asText()).isEqualTo(gold.get("date").asText());
        assertThat(txn.get("currency").asText()).isEqualTo(gold.get("currency").asText());
        assertThat(dec(txn.get("grand_total"))).isEqualByComparingTo(dec(gold.get("grand_total")));
        assertThat(txn.get("itemize_status").asText()).isEqualTo(gold.get("itemize_status").asText());

        assertThat(txn.get("taxes")).hasSize(gold.get("taxes").size());
        for (int i = 0; i < gold.get("taxes").size(); i++) {
            JsonNode a = txn.get("taxes").get(i), e = gold.get("taxes").get(i);
            assertThat(a.get("name").asText()).isEqualTo(e.get("name").asText());
            assertThat(dec(a.get("rate"))).isEqualByComparingTo(dec(e.get("rate")));
            assertThat(dec(a.get("amount"))).isEqualByComparingTo(dec(e.get("amount")));
        }

        assertThat(txn.get("line_items")).hasSize(gold.get("line_items").size());
        for (int i = 0; i < gold.get("line_items").size(); i++) {
            JsonNode a = txn.get("line_items").get(i), e = gold.get("line_items").get(i);
            assertThat(a.get("description").asText()).isEqualTo(e.get("description").asText());
            assertThat(dec(a.get("amount"))).isEqualByComparingTo(dec(e.get("amount")));
        }
    }
}
