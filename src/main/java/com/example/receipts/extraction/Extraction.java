package com.example.receipts.extraction;

import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.ReceiptHeader;
import com.example.receipts.domain.TaxLine;
import java.util.List;

public record Extraction(ReceiptHeader header, List<TaxLine> taxes, List<LineItem> items) {}
