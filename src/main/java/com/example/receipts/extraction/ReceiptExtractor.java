package com.example.receipts.extraction;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.Money;
import com.example.receipts.domain.ReceiptHeader;
import com.example.receipts.domain.TaxLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the receipt text layout used by the fixtures. Never invents values:
 * a missing or ambiguous header field is an ExtractionException.
 */
@Component
public class ReceiptExtractor {
    private static final Pattern MERCHANT = Pattern.compile("^MERCHANT:\\s*(.+?)$", CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^DATE:\\s*(\\S+)$", CASE_INSENSITIVE);
    private static final Pattern CURRENCY = Pattern.compile("^CURRENCY:\\s*([A-Za-z]{3})$", CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile("^TOTAL\\s+(-?\\d+\\.\\d{2})$", CASE_INSENSITIVE);
    private static final Pattern SUBTOTAL = Pattern.compile("^SUB\\s*TOTAL\\b.*$", CASE_INSENSITIVE);
    private static final Pattern TAX = Pattern.compile(
            "^(incl\\.?\\s+)?(VAT|GST|HST|PST|MwSt|Tax)\\s+(\\d+(?:\\.\\d+)?)\\s*%\\s+(\\d+\\.\\d{2})$", CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("^(\\S.*?)\\s{2,}(-?\\d+\\.\\d{2})$");

    public Extraction extract(String ocrText) {
        String merchant = null;
        String rawDate = null;
        String currency = null;
        Set<Long> totals = new LinkedHashSet<>();
        List<TaxLine> taxes = new ArrayList<>();

        for (String line : lines(ocrText)) {
            Matcher m;
            if ((m = MERCHANT.matcher(line)).matches()) {
                merchant = m.group(1);
            } else if ((m = DATE.matcher(line)).matches()) {
                rawDate = m.group(1);
            } else if ((m = CURRENCY.matcher(line)).matches()) {
                currency = m.group(1).toUpperCase(Locale.ROOT);
            } else if ((m = TOTAL.matcher(line)).matches()) {
                totals.add(minor(m.group(1)));
            } else if ((m = TAX.matcher(line)).matches()) {
                taxes.add(new TaxLine(
                        m.group(2).toUpperCase(Locale.ROOT),
                        new BigDecimal(m.group(3)).movePointLeft(2),
                        minor(m.group(4)),
                        m.group(1) != null));
            }
        }

        List<String> missing = new ArrayList<>();
        if (merchant == null) missing.add("merchant");
        if (rawDate == null) missing.add("date");
        if (currency == null) missing.add("currency");
        if (totals.isEmpty()) missing.add("total");
        if (!missing.isEmpty()) {
            throw new ExtractionException("Could not extract required fields: " + String.join(", ", missing));
        }
        if (totals.size() > 1) {
            throw new ExtractionException("Receipt has conflicting TOTAL lines: " + totals);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(rawDate);
        } catch (DateTimeParseException e) {
            throw new ExtractionException("DATE is not a valid ISO-8601 date: " + rawDate);
        }

        var header = new ReceiptHeader(merchant, date, currency, totals.iterator().next());
        return new Extraction(header, List.copyOf(taxes), extractItems(ocrText));
    }

    /** Lines of the form "<description>  <amount>" that are not header, subtotal, tax or total lines. */
    public List<LineItem> extractItems(String ocrText) {
        List<LineItem> items = new ArrayList<>();
        for (String line : lines(ocrText)) {
            if (isNonItemLine(line)) {
                continue;
            }
            Matcher m = ITEM.matcher(line);
            if (m.matches()) {
                items.add(LineItem.of(m.group(1).strip(), minor(m.group(2))));
            }
        }
        return List.copyOf(items);
    }

    private static boolean isNonItemLine(String line) {
        return line.startsWith("(")
                || MERCHANT.matcher(line).matches()
                || DATE.matcher(line).matches()
                || CURRENCY.matcher(line).matches()
                || TOTAL.matcher(line).matches()
                || SUBTOTAL.matcher(line).matches()
                || TAX.matcher(line).matches();
    }

    private static List<String> lines(String text) {
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static long minor(String amount) {
        try {
            return Money.toMinor(new BigDecimal(amount));
        } catch (ArithmeticException e) {
            throw new ExtractionException("Amount out of range: " + amount);
        }
    }
}
