package com.bbc.sms.finance.documents;

import com.bbc.sms.finance.documents.FinanceDocumentDtos.InvoiceLineView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.InvoiceView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ReceiptLineView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ReceiptView;
import com.bbc.sms.finance.accounts.FinanceAccountDtos.AccountPaymentView;
import com.bbc.sms.finance.accounts.FinanceAccountDtos.ConsolidatedReceiptView;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Server-side bilingual finance PDFs. The bundled Arial font preserves French accents. */
@Component
public class FinancePdfRenderer {
    public record SchoolSnapshot(String code, String name, String authority, String address,
                                 String city, String country, String phone, String email) {}

    public byte[] invoice(InvoiceView invoice, SchoolSnapshot school, String verificationUrl) {
        List<String> lines = new ArrayList<>();
        lines.add(school.name());
        if (school.authority() != null) lines.add(school.authority());
        lines.add(join(school.address(), school.city(), school.country()));
        lines.add(join(school.phone(), school.email()));
        lines.add("");
        lines.add("FACTURE / INVOICE    " + invoice.invoiceNumber());
        lines.add("Statut / Status: " + invoice.status());
        lines.add("Émise le / Issued: " + invoice.issueDate() + "    Échéance / Due: " + invoice.dueDate());
        lines.add("Élève / Student: " + invoice.studentName() + "    Matricule: " + blank(invoice.matricule()));
        lines.add("Classe / Class: " + blank(invoice.className()) + "    Session: " + blank(invoice.sessionLabel()));
        lines.add("Destinataire / Recipient: " + invoice.recipient().name());
        if (invoice.recipient().email() != null) lines.add("Email: " + invoice.recipient().email());
        if (invoice.recipient().phone() != null) lines.add("Téléphone / Phone: " + invoice.recipient().phone());
        if (invoice.recipient().warning() != null) lines.add("Attention / Notice: " + invoice.recipient().warning());
        lines.add("");
        lines.add("Lignes / Lines");
        for (InvoiceLineView line : invoice.lines()) {
            lines.add(line.feeTypeCode() + " — " + line.descriptionFr() + " / " + line.descriptionEn()
                    + " | due " + line.dueDate() + " | " + money(line.amountMinor(), line.currency()));
        }
        lines.add("");
        lines.add("Total / Total: " + money(invoice.totalMinor(), invoice.currency()));
        lines.add("Payé / Paid: " + money(invoice.paidMinor(), invoice.currency()));
        lines.add("Solde dû / Balance due: " + money(invoice.outstandingMinor(), invoice.currency()));
        lines.add("Empreinte / Integrity hash: " + invoice.snapshotHash());
        lines.add("Vérification / Verification: " + verificationUrl + invoice.invoiceNumber());
        return render(lines, "FACTURE / INVOICE " + invoice.invoiceNumber());
    }

    public byte[] receipt(ReceiptView receipt, SchoolSnapshot school, String verificationUrl) {
        List<String> lines = new ArrayList<>();
        lines.add(school.name());
        if (school.authority() != null) lines.add(school.authority());
        lines.add(join(school.address(), school.city(), school.country()));
        lines.add(join(school.phone(), school.email()));
        lines.add("");
        lines.add("REÇU / RECEIPT    " + receipt.receiptNumber());
        lines.add("Statut / Status: " + receipt.status() + "    Date: " + receipt.issueDate());
        lines.add("Élève / Student: " + receipt.studentName() + "    Matricule: " + blank(receipt.matricule()));
        lines.add("Classe / Class: " + blank(receipt.className()) + "    Session: " + blank(receipt.sessionLabel()));
        lines.add("Destinataire / Recipient: " + receipt.recipient().name());
        lines.add("Canal / Channel: " + receipt.channelCode() + "    Référence / Reference: " + blank(receipt.reference()));
        lines.add("");
        lines.add("Affectations / Allocations");
        for (ReceiptLineView line : receipt.lines()) {
            lines.add(line.feeTypeCode() + " — " + line.feeTypeNameFr() + " / " + line.feeTypeNameEn()
                    + " | due " + line.dueDate() + " | " + money(line.allocatedMinor(), line.currency())
                    + " | reste / remaining " + money(line.installmentRemainingMinor(), line.currency()));
        }
        lines.add("");
        lines.add("Reçu / Received: " + money(receipt.amountMinor(), receipt.currency()));
        lines.add("Affecté / Allocated: " + money(receipt.allocatedMinor(), receipt.currency()));
        lines.add("Crédit élève / Student credit: " + money(receipt.creditMinor(), receipt.currency()));
        lines.add("Solde après / Balance after: " + money(receipt.outstandingMinor(), receipt.currency()));
        lines.add("Journal: " + blank(receipt.journalEntryId() == null ? null : receipt.journalEntryId().toString()));
        lines.add("Empreinte / Integrity hash: " + receipt.snapshotHash());
        lines.add("Vérification / Verification: " + verificationUrl + receipt.receiptNumber());
        return render(lines, "REÇU / RECEIPT " + receipt.receiptNumber());
    }

    public byte[] consolidatedReceipt(ConsolidatedReceiptView receipt, SchoolSnapshot school,
                                      String verificationUrl) {
        return renderConsolidatedReceipt(receipt, school, verificationUrl);
    }

    private byte[] renderConsolidatedReceipt(ConsolidatedReceiptView receipt, SchoolSnapshot school,
                                             String verificationUrl) {
        final int firstPageRows = 11;
        final int followingPageRows = 17;
        int remaining = Math.max(0, receipt.payments().size() - firstPageRows);
        int pageCount = 1 + (int) Math.ceil(remaining / (double) followingPageRows);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream fontStream = new ClassPathResource("fonts/Arial.ttf").getInputStream()) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            int paymentIndex = 0;
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    drawConsolidatedHeader(stream, font, receipt, school, pageNumber > 1);
                    float tableTop;
                    int capacity;
                    if (pageNumber == 1) {
                        drawStudentIdentity(stream, font, receipt);
                        drawAccountSummary(stream, font, receipt);
                        tableTop = 500;
                        capacity = firstPageRows;
                    } else {
                        drawText(stream, font, "VERSEMENTS (SUITE) / PAYMENTS (CONTINUED)", 48, 690, 10, 15, 118, 110);
                        tableTop = 665;
                        capacity = followingPageRows;
                    }
                    drawPaymentTableHeader(stream, font, tableTop);
                    float rowTop = tableTop - 24;
                    int end = Math.min(receipt.payments().size(), paymentIndex + capacity);
                    if (paymentIndex == end) {
                        drawText(stream, font, "Aucun versement enregistré / No payment recorded", 60,
                                rowTop - 24, 9, 100, 116, 139);
                    }
                    while (paymentIndex < end) {
                        drawPaymentRow(stream, font, receipt.payments().get(paymentIndex), rowTop,
                                paymentIndex % 2 == 1);
                        rowTop -= 34;
                        paymentIndex++;
                    }
                    drawConsolidatedFooter(stream, font, receipt, verificationUrl, pageNumber, pageCount);
                }
            }
            document.setDocumentInformation(documentInfo(
                    "Relevé des paiements / Consolidated receipt " + receipt.receiptNumber(), school.name()));
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Échec de génération du relevé des paiements", ex);
        }
    }

    private void drawConsolidatedHeader(PDPageContentStream stream, PDType0Font font,
                                        ConsolidatedReceiptView receipt, SchoolSnapshot school,
                                        boolean continuation) throws Exception {
        fill(stream, 0, 830, 595, 12, 15, 118, 110);
        fill(stream, 48, 752, 48, 48, 18, 52, 83);
        drawText(stream, font, "BBC", 57, 769, 13, 246, 196, 83);
        drawText(stream, font, clipped(font, blank(school.name()), 255, 16), 110, 781, 16, 18, 52, 83);
        drawText(stream, font, clipped(font, join(school.address(), school.city(), school.country()), 260, 8),
                110, 764, 8, 100, 116, 139);
        drawText(stream, font, clipped(font, join(school.phone(), school.email()), 260, 8),
                110, 751, 8, 100, 116, 139);

        drawTextRight(stream, font, continuation
                        ? "RELEVÉ DES PAIEMENTS - SUITE"
                        : "RELEVÉ DES PAIEMENTS",
                547, 782, 9, 15, 118, 110);
        drawTextRight(stream, font, "PAYMENT STATEMENT", 547, 767, 8, 15, 118, 110);
        drawTextRight(stream, font, clipped(font, receipt.receiptNumber(), 170, 10),
                547, 749, 10, 18, 52, 83);
        strokeLine(stream, 48, 728, 547, 728, 1.4f, 15, 118, 110);
    }

    private void drawStudentIdentity(PDPageContentStream stream, PDType0Font font,
                                     ConsolidatedReceiptView receipt) throws Exception {
        fillAndStroke(stream, 48, 626, 499, 78, 248, 250, 252, 219, 228, 236);
        drawText(stream, font, "ÉLÈVE / STUDENT", 62, 682, 7, 100, 116, 139);
        drawText(stream, font, clipped(font, blank(receipt.studentName()), 278, 14),
                62, 660, 14, 18, 52, 83);
        drawText(stream, font, "MATRICULE / ID", 350, 682, 7, 100, 116, 139);
        drawText(stream, font, clipped(font, blank(receipt.matricule()), 180, 10),
                350, 662, 10, 18, 52, 83);
        drawText(stream, font, clipped(font, "Classe / Class: " + blank(receipt.className()), 270, 8),
                62, 640, 8, 100, 116, 139);
        drawText(stream, font, clipped(font, "Session: " + blank(receipt.sessionLabel()), 180, 8),
                350, 640, 8, 100, 116, 139);
    }

    private void drawAccountSummary(PDPageContentStream stream, PDType0Font font,
                                    ConsolidatedReceiptView receipt) throws Exception {
        String[] labels = { "FACTURÉ / BILLED", "PAYÉ / PAID", "SOLDE DÛ / BALANCE", "CRÉDIT / CREDIT" };
        long[] values = { receipt.billedMinor(), receipt.paidMinor(), receipt.outstandingMinor(), receipt.creditMinor() };
        float x = 48;
        float width = 121.25f;
        for (int i = 0; i < labels.length; i++) {
            if (i == 2) fillAndStroke(stream, x, 543, width, 62, 240, 253, 250, 94, 234, 212);
            else fillAndStroke(stream, x, 543, width, 62, 255, 255, 255, 219, 228, 236);
            drawText(stream, font, labels[i], x + 10, 584, 6.4f, 100, 116, 139);
            drawText(stream, font, clipped(font, money(values[i], receipt.currency()), width - 20, 10),
                    x + 10, 560, 10, 18, 52, 83);
            x += width + 5;
        }
        drawText(stream, font, "HISTORIQUE DES VERSEMENTS / PAYMENT HISTORY", 48, 516, 9, 18, 52, 83);
        drawTextRight(stream, font, receipt.payments().size() + " opération(s) / transaction(s)",
                547, 516, 7.5f, 100, 116, 139);
    }

    private void drawPaymentTableHeader(PDPageContentStream stream, PDType0Font font, float top) throws Exception {
        fill(stream, 48, top - 24, 499, 24, 18, 52, 83);
        drawText(stream, font, "DATE", 56, top - 16, 7, 255, 255, 255);
        drawText(stream, font, "REÇU / RECEIPT", 116, top - 16, 7, 255, 255, 255);
        drawText(stream, font, "MODE / METHOD", 257, top - 16, 7, 255, 255, 255);
        drawTextRight(stream, font, "MONTANT / AMOUNT", 539, top - 16, 7, 255, 255, 255);
    }

    private void drawPaymentRow(PDPageContentStream stream, PDType0Font font, AccountPaymentView payment,
                                float top, boolean alternate) throws Exception {
        fillAndStroke(stream, 48, top - 34, 499, 34,
                alternate ? 248 : 255, alternate ? 250 : 255, alternate ? 252 : 255,
                226, 232, 240);
        drawText(stream, font, String.valueOf(payment.paymentDate()), 56, top - 14, 7.4f, 18, 52, 83);
        drawText(stream, font, clipped(font, blank(payment.receiptNo()), 132, 7.6f), 116, top - 13, 7.6f, 18, 52, 83);
        drawText(stream, font, clipped(font, blank(payment.reference()), 132, 6.5f), 116, top - 26, 6.5f, 100, 116, 139);
        drawText(stream, font, clipped(font, blank(payment.channelLabel()), 150, 7.4f), 257, top - 13, 7.4f, 18, 52, 83);
        drawText(stream, font, clipped(font, blank(payment.treasuryAccountName()), 150, 6.5f), 257, top - 26, 6.5f, 100, 116, 139);
        drawTextRight(stream, font, money(payment.netAmountMinor(), payment.currency()),
                539, top - 19, 8, 18, 52, 83);
    }

    private void drawConsolidatedFooter(PDPageContentStream stream, PDType0Font font,
                                        ConsolidatedReceiptView receipt, String verificationUrl,
                                        int pageNumber, int pageCount) throws Exception {
        strokeLine(stream, 48, 66, 547, 66, .7f, 203, 213, 225);
        String verification = "Vérification / Verification: " + verificationUrl + receipt.receiptNumber();
        drawText(stream, font, clipped(font, verification, 380, 6.5f), 48, 49, 6.5f, 100, 116, 139);
        drawText(stream, font, "Empreinte / Hash: " + receipt.snapshotHash().substring(0, Math.min(16, receipt.snapshotHash().length())),
                48, 37, 6.2f, 100, 116, 139);
        drawTextRight(stream, font, "Page " + pageNumber + " / " + pageCount, 547, 43, 7, 18, 52, 83);
        fill(stream, 0, 0, 595, 9, 15, 118, 110);
    }

    private static org.apache.pdfbox.pdmodel.PDDocumentInformation documentInfo(String title, String author) {
        org.apache.pdfbox.pdmodel.PDDocumentInformation info = new org.apache.pdfbox.pdmodel.PDDocumentInformation();
        info.setTitle(title);
        info.setAuthor(author == null ? "BBC SMS" : author);
        return info;
    }

    private static void drawText(PDPageContentStream stream, PDType0Font font, String value,
                                 float x, float y, float size, int red, int green, int blue) throws Exception {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(red, green, blue);
        stream.newLineAtOffset(x, y);
        stream.showText(value == null ? "" : value.replace('\n', ' '));
        stream.endText();
    }

    private static void drawTextRight(PDPageContentStream stream, PDType0Font font, String value,
                                      float right, float y, float size, int red, int green, int blue) throws Exception {
        String text = value == null ? "" : value;
        float width = font.getStringWidth(text) / 1000f * size;
        drawText(stream, font, text, right - width, y, size, red, green, blue);
    }

    private static void fill(PDPageContentStream stream, float x, float y, float width, float height,
                             int red, int green, int blue) throws Exception {
        stream.setNonStrokingColor(red, green, blue);
        stream.addRect(x, y, width, height);
        stream.fill();
    }

    private static void fillAndStroke(PDPageContentStream stream, float x, float y, float width, float height,
                                      int fillRed, int fillGreen, int fillBlue,
                                      int lineRed, int lineGreen, int lineBlue) throws Exception {
        stream.setNonStrokingColor(fillRed, fillGreen, fillBlue);
        stream.setStrokingColor(lineRed, lineGreen, lineBlue);
        stream.setLineWidth(.6f);
        stream.addRect(x, y, width, height);
        stream.fillAndStroke();
    }

    private static void strokeLine(PDPageContentStream stream, float x1, float y1, float x2, float y2,
                                   float width, int red, int green, int blue) throws Exception {
        stream.setStrokingColor(red, green, blue);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private static String clipped(PDType0Font font, String value, float maxWidth, float size) throws Exception {
        String text = blank(value).replace('\n', ' ');
        if (font.getStringWidth(text) / 1000f * size <= maxWidth) return text;
        String suffix = "...";
        while (!text.isEmpty() && font.getStringWidth(text + suffix) / 1000f * size > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    private byte[] render(List<String> lines, String title) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream fontStream = new ClassPathResource("fonts/Arial.ttf").getInputStream()) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            int index = 0;
            do {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 15);
                    stream.newLineAtOffset(48, 790);
                    stream.showText(title);
                    stream.setFont(font, 9);
                    stream.newLineAtOffset(0, -26);
                    int count = 0;
                    while (index < lines.size() && count < 46) {
                        for (String wrapped : wrap(lines.get(index++), 105)) {
                            stream.showText(wrapped);
                            stream.newLineAtOffset(0, -14);
                        }
                        count++;
                    }
                    stream.endText();
                }
            } while (index < lines.size());
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Échec de génération du PDF financier", ex);
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

    private static String money(long amount, String currency) {
        return String.format(java.util.Locale.US, "%,d %s", amount, blank(currency)).replace(',', ' ');
    }
    private static String join(String... values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + " · " + b).orElse("");
    }
    private static String blank(String value) { return value == null || value.isBlank() ? "—" : value; }
}
