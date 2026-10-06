package com.example.receipts.ocr;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** OCR is stubbed: a text/plain upload stands in for the scanned image and its bytes are the OCR output. */
@Component
public class StubTextOcrProvider implements OcrProvider {
    @Override
    public String extractText(String filename, String contentType, byte[] content) {
        boolean isText = (contentType != null && contentType.startsWith("text/"))
                || (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (!isText) {
            throw new OcrUnavailableException(
                    "OCR is a stub: only text/plain receipt fixtures can be processed (got " + contentType + ")");
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
