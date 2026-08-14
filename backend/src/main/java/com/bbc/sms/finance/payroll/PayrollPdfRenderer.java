package com.bbc.sms.finance.payroll;

import com.bbc.sms.finance.payroll.PayrollDtos.EmployeeView;
import com.bbc.sms.finance.payroll.PayrollDtos.LineView;
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

/** Server-rendered bilingual payslips; no browser print view is used as source. */
@Component
public class PayrollPdfRenderer {
    public record SchoolSnapshot(String code, String name, String authority, String address,
                                 String city, String country, String phone, String email) {}

    public byte[] render(EmployeeView employee, PayrollDtos.PeriodView period,
                         SchoolSnapshot school, String payslipNumber, String snapshotHash,
                         String verificationBase) {
        List<String> lines = new ArrayList<>();
        lines.add(school.name());
        if (school.authority() != null) lines.add(school.authority());
        lines.add(join(school.address(), school.city(), school.country()));
        lines.add(join(school.phone(), school.email()));
        lines.add("");
        lines.add("BULLETIN DE PAIE / PAYSLIP    " + payslipNumber);
        lines.add("Période / Period: " + period.code() + "    " + period.startDate() + " → " + period.endDate());
        lines.add("Employé / Employee: " + employee.employeeName() + "    Code: " + employee.employeeCode());
        lines.add("Type / Employment: " + employee.employmentType() + "    Mode: " + employee.employmentMode());
        lines.add("Formule / Formula: " + blank(employee.formula()));
        lines.add("");
        lines.add("Composants / Components");
        for (LineView line : employee.lines()) {
            lines.add(line.componentCode() + " — " + line.componentNameFr() + " / " + line.componentNameEn()
                    + " | " + line.source() + " | " + money(line.amountMinor()));
        }
        lines.add("");
        lines.add("Brut / Gross: " + money(employee.grossMinor()));
        lines.add("Retenues / Deductions: " + money(employee.deductionMinor()));
        lines.add("Net à payer / Net pay: " + money(employee.netMinor()));
        lines.add("Coût employeur / Employer cost: " + money(employee.employerCostMinor()));
        lines.add("Empreinte / Integrity hash: " + snapshotHash);
        lines.add("Vérification / Verification: " + verificationBase + payslipNumber);
        return render(lines, "BULLETIN DE PAIE / PAYSLIP " + payslipNumber);
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
            throw new IllegalStateException("Échec de génération du bulletin de paie", ex);
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

    private static String money(long value) { return String.format(java.util.Locale.FRANCE, "%,d XAF", value); }
    private static String join(String... values) { return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).reduce((a,b) -> a + " · " + b).orElse(""); }
    private static String blank(String value) { return value == null || value.isBlank() ? "—" : value; }
}
