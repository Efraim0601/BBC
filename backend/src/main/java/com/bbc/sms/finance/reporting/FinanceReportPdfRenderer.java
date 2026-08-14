package com.bbc.sms.finance.reporting;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Server-side report PDF renderer. The browser is never the source document. */
public final class FinanceReportPdfRenderer {
    private FinanceReportPdfRenderer() {}

    public static byte[] render(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text == null ? "".split("\\n") : text.split("\\n", -1)) lines.add(line);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream fontStream = new ClassPathResource("fonts/Arial.ttf").getInputStream()) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            int index = 0;
            do {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 11);
                    stream.newLineAtOffset(42, 800);
                    int lineCount = 0;
                    while (index < lines.size() && lineCount < 52) {
                        for (String wrapped : wrap(lines.get(index++), 112)) {
                            stream.showText(wrapped);
                            stream.newLineAtOffset(0, -14);
                            lineCount++;
                            if (lineCount >= 52) break;
                        }
                    }
                    stream.endText();
                }
            } while (index < lines.size());
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render finance report PDF", ex);
        }
    }

    private static List<String> wrap(String value, int width) {
        List<String> result = new ArrayList<>();
        String remaining = value == null ? "" : value;
        if (remaining.isBlank()) return List.of("");
        while (remaining.length() > width) {
            int cut = remaining.lastIndexOf(' ', width);
            if (cut < 1) cut = width;
            result.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        result.add(remaining);
        return result;
    }
}
