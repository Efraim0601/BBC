package com.bbc.sms.timetable;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TimetableExportsTest {
    @Test void calendarFoldingPreservesUnicodeAndUsesStandardLineEndings() {
        String value="SUMMARY:"+"Mathématiques ".repeat(15);
        String output=TimetableVersionService.calendarOutput(value+"\n");
        assertEquals(value+"\r\n",output.replace("\r\n ",""));
        for(String line:output.split("\r\n")) assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=75);
    }
    @Test void calendarStartsWithinTheEffectiveRangeAndUsesSixDigitTimes() {
        LocalDate tuesday = LocalDate.of(2026,9,1);
        assertEquals(LocalDate.of(2026,9,7),TimetableVersionService.firstCalendarOccurrence(tuesday,0));
        assertEquals(tuesday,TimetableVersionService.firstCalendarOccurrence(tuesday,1));
        assertEquals("20260901T073000",TimetableVersionService.calendarTime(tuesday,LocalTime.of(7,30)));
        assertEquals("20260901T225959Z",TimetableVersionService.calendarUntil(tuesday,"Africa/Douala"));
    }
    @Test void printsNamesAndRepeatsHeadersAcrossClassPages() throws Exception {
        List<TimetablePdfRenderer.Row> rows = new ArrayList<>();
        for(int i=0;i<45;i++) rows.add(new TimetablePdfRenderer.Row("6ème A","Lun / Mon","07:30 - 08:15","Mathématiques", "BACHIROU", "Salle A"));
        rows.add(new TimetablePdfRenderer.Row("CE1 A","Mar / Tue","08:15 - 09:00","Lecture", "SARATOU", "B"));
        byte[] bytes=TimetablePdfRenderer.render("2026-2027","Du 01/09/2026 au 31/07/2027","V2 · PUBLISHED",rows);
        try(PDDocument pdf=PDDocument.load(bytes)) {
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(pdf.getNumberOfPages()>=3);
            assertTrue(text.contains("6ème A")); assertTrue(text.contains("CE1 A"));
            assertTrue(text.contains("Mathématiques")); assertTrue(text.contains("BACHIROU"));
            assertTrue(text.contains("07:30 - 08:15"));
            assertFalse(text.contains("Teacher ID"));
            assertEquals(pdf.getNumberOfPages(),text.split("Jour / Day",-1).length-1);
        }
    }
    @Test void veryLongTextContinuesWithoutTruncationOrPaginationLoop() throws Exception {
        var row=new TimetablePdfRenderer.Row("CE1 A","Lun / Mon","07:30 - 08:15","START "+"Sujet très long ".repeat(300)+" END", "Mme Exemple", "A");
        byte[] bytes=TimetablePdfRenderer.render("2026-2027","Septembre","V1",List.of(row));
        try(PDDocument pdf=PDDocument.load(bytes)) {
            assertTrue(pdf.getNumberOfPages()>1);
            String text=new PDFTextStripper().getText(pdf); assertTrue(text.contains("START")); assertTrue(text.contains("END"));
        }
    }
}
