package com.example.receipts.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.TaxLine;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceiptExtractorTest {
    private final ReceiptExtractor extractor = new ReceiptExtractor();

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("fixtures", "task-a", name));
    }

    @Test
    void clean() throws Exception {
        Extraction e = extractor.extract(fixture("receipt-clean.txt"));
        assertThat(e.header().merchant()).isEqualTo("Cafe Mitte");
        assertThat(e.header().date()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(e.header().currency()).isEqualTo("EUR");
        assertThat(e.header().grandTotalMinor()).isEqualTo(1785);
        assertThat(e.taxes()).containsExactly(new TaxLine("VAT", new BigDecimal("0.19"), 285, false));
        assertThat(e.items()).containsExactly(
                LineItem.of("Espresso", 350), LineItem.of("Sandwich", 890), LineItem.of("Mineral water", 260));
    }

    @Test
    void taxOnlyHasInclusiveTaxAndNoItems() throws Exception {
        Extraction e = extractor.extract(fixture("receipt-tax-only.txt"));
        assertThat(e.header().merchant()).isEqualTo("Berlin Taxi GmbH");
        assertThat(e.header().grandTotalMinor()).isEqualTo(2400);
        assertThat(e.taxes()).containsExactly(new TaxLine("VAT", new BigDecimal("0.19"), 383, true));
        assertThat(e.items()).isEmpty();
    }

    @Test
    void mismatchKeepsPrintedTotal() throws Exception {
        Extraction e = extractor.extract(fixture("receipt-mismatch.txt"));
        assertThat(e.header().grandTotalMinor()).isEqualTo(1850);
        assertThat(e.taxes()).containsExactly(new TaxLine("VAT", new BigDecimal("0.19"), 190, false));
        assertThat(e.items()).containsExactly(LineItem.of("Water", 400), LineItem.of("Snacks", 600));
    }

    @Test
    void missingRequiredFieldsAreNamed() {
        assertThatThrownBy(() -> extractor.extract("DATE: 2026-03-12\nCURRENCY: EUR\n"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("merchant")
                .hasMessageContaining("total");
    }

    @Test
    void invalidCalendarDateIsRejected() {
        assertThatThrownBy(() -> extractor.extract("MERCHANT: X\nDATE: 2026-02-30\nCURRENCY: EUR\nTOTAL   5.00\n"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("2026-02-30");
    }

    @Test
    void conflictingTotalsAreRejected() {
        assertThatThrownBy(() -> extractor.extract("MERCHANT: X\nDATE: 2026-03-12\nCURRENCY: EUR\nTOTAL   5.00\nTOTAL   6.00\n"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("conflicting");
    }

    @Test
    void outOfRangeAmountIsAnExtractionFailure() {
        assertThatThrownBy(() -> extractor.extract("MERCHANT: X\nDATE: 2026-03-12\nCURRENCY: EUR\nTOTAL   99999999999999999999.99\n"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void commaDecimalTotalIsNotGuessed() {
        assertThatThrownBy(() -> extractor.extract("MERCHANT: X\nDATE: 2026-03-12\nCURRENCY: EUR\nTOTAL   17,85\n"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("total");
    }
}
