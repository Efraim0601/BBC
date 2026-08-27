package com.bbc.sms.finance.pdf;

import com.bbc.sms.finance.accounts.FinanceAccountDtos.AccountPaymentView;
import com.bbc.sms.finance.accounts.FinanceAccountDtos.ConsolidatedReceiptView;
import com.bbc.sms.finance.documents.FinancePdfRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FinancePdfRendererTest {
    private final FinancePdfRenderer renderer = new FinancePdfRenderer();

    @Test
    void consolidatedReceiptUsesFullA4PagesAndCarriesTheStudentIdentity() throws Exception {
        List<AccountPaymentView> payments = IntStream.range(0, 20).mapToObj(index -> new AccountPaymentView(
                UUID.randomUUID(), "LEGACY_PAYMENT", "REC-" + (index + 1),
                LocalDate.of(2026, 8, 1).plusDays(index), 5_000, 0, 5_000, "XAF",
                "CASH", "Espèces", "Cash", "REF-" + index, 0, 0, "POSTED", null)).toList();
        ConsolidatedReceiptView receipt = new ConsolidatedReceiptView(UUID.randomUUID(),
                "ABOUBAKAR Abdoul Aziz", "BBC-1094", "SIL A / Class 1 A", "2026-2027",
                "CR/2026/000001", LocalDate.of(2026, 8, 27), 100_000, 100_000, 0, 0,
                "XAF", "ISSUED", "abcdef0123456789abcdef0123456789", null, null, null, payments);
        FinancePdfRenderer.SchoolSnapshot school = new FinancePdfRenderer.SchoolSnapshot("BBC",
                "Bayo Bilingual Complex", null, "Maroua", "Maroua", "Cameroun", "600000000", null);

        byte[] bytes = renderer.consolidatedReceipt(receipt, school, "/api/official-documents/verify/");

        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ABOUBAKAR Abdoul Aziz", "BBC-1094", "SIL A / Class 1 A",
                    "CR/2026/000001", "100 000 XAF", "REC-20", "Page 2 / 2");
        }
    }
}
