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
        List<String> lines = new ArrayList<>();
        lines.add(school.name());
        if (school.authority() != null) lines.add(school.authority());
        lines.add(join(school.address(), school.city(), school.country()));
        lines.add(join(school.phone(), school.email()));
        lines.add("");
        lines.add("RELEVÉ DES PAIEMENTS / CONSOLIDATED RECEIPT    " + receipt.receiptNumber());
        lines.add("Émis le / Issued: " + receipt.issueDate());
        lines.add("Élève / Student: " + receipt.studentName() + "    Matricule: " + blank(receipt.matricule()));
        lines.add("Classe / Class: " + blank(receipt.className()) + "    Session: " + blank(receipt.sessionLabel()));
        lines.add("");
        lines.add("Situation / Account summary");
        lines.add("Facturé / Billed: " + money(receipt.billedMinor(), receipt.currency()));
        lines.add("Payé / Paid: " + money(receipt.paidMinor(), receipt.currency()));
        lines.add("Solde dû / Balance due: " + money(receipt.outstandingMinor(), receipt.currency()));
        lines.add("Crédit / Credit: " + money(receipt.creditMinor(), receipt.currency()));
        lines.add("");
        lines.add("Versements / Payments");
        for (AccountPaymentView payment : receipt.payments()) {
            lines.add(payment.paymentDate() + " | " + blank(payment.receiptNo()) + " | "
                    + payment.channelLabel() + " | " + blank(payment.treasuryAccountName())
                    + " | " + money(payment.netAmountMinor(), payment.currency())
                    + " | " + payment.status());
            if (payment.reference() != null && !payment.reference().isBlank()) {
                lines.add("  Référence / Reference: " + payment.reference());
            }
        }
        lines.add("");
        lines.add("Total des versements / Total payments: " + money(receipt.paidMinor(), receipt.currency()));
        lines.add("Empreinte / Integrity hash: " + receipt.snapshotHash());
        lines.add("Vérification / Verification: " + verificationUrl + receipt.receiptNumber());
        return render(lines, "RELEVÉ DES PAIEMENTS / CONSOLIDATED RECEIPT " + receipt.receiptNumber());
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
        return String.format(java.util.Locale.FRANCE, "%,d %s", amount, blank(currency));
    }
    private static String join(String... values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + " · " + b).orElse("");
    }
    private static String blank(String value) { return value == null || value.isBlank() ? "—" : value; }
}
