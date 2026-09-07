package com.bbc.sms.timetable;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.List;

/** Human-readable print export; interchange formats retain their stable identifiers. */
final class TimetablePdfRenderer {
    record Row(String className, String day, String time, String subject, String teacher, String room) {}
    private static final float[] WIDTHS = {62, 75, 169, 155, 62};
    private static final Color INK = new Color(24, 48, 70);
    private TimetablePdfRenderer() {}

    static byte[] render(String session, String validity, String version, List<Row> rows) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("BBC SMS - Emploi du temps / Timetable");
            PDFont regular = font(doc, false), bold = font(doc, true);
            Map<String, List<Row>> classes = new LinkedHashMap<>();
            for (Row row : rows) classes.computeIfAbsent(row.className(), key -> new ArrayList<>()).add(row);
            if (classes.isEmpty()) classes.put("Aucun cours / No lessons", List.of());
            for (var entry : classes.entrySet()) {
                int next = 0, rowLine = 0;
                do {
                    PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                    try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                        stream.setNonStrokingColor(INK); stream.addRect(0, 742, 595.28f, 100); stream.fill();
                        text(stream, bold, 11, 36, 810, "BBC SMS", Color.WHITE);
                        text(stream, bold, 17, 36, 783, "EMPLOI DU TEMPS / TIMETABLE", Color.WHITE);
                        text(stream, regular, 9, 36, 758, session + "  |  " + version, Color.WHITE);
                        text(stream, bold, 17, 36, 712, entry.getKey(), INK);
                        text(stream, regular, 8, 36, 693, validity, INK);
                        float y = 674, x = 36;
                        stream.setNonStrokingColor(new Color(231, 239, 246));
                        stream.addRect(36, y - 28, 523, 28); stream.fill();
                        String[] headers = {"Jour / Day", "Horaire / Time", "Matière / Subject", "Enseignant / Teacher", "Salle / Room"};
                        for (int c = 0; c < headers.length; c++) {
                            text(stream, bold, 8, x + 6, y - 17, headers[c], INK); x += WIDTHS[c];
                        }
                        y -= 28;
                        while (next < entry.getValue().size()) {
                            Row row = entry.getValue().get(next);
                            String[] values = {row.day(), row.time(), row.subject(), row.teacher(), row.room()};
                            List<List<String>> cells = new ArrayList<>(); int lines = 1;
                            for (int c = 0; c < values.length; c++) {
                                List<String> wrapped = wrap(regular, values[c], WIDTHS[c] - 12, 8);
                                cells.add(wrapped); lines = Math.max(lines, wrapped.size());
                            }
                            int shown = lines - rowLine;
                            float height = Math.max(27, shown * 11 + 12);
                            if (y - height < 54) {
                                if (y < 640) break;
                                // Very long configured labels continue on the next page, never loop or clip.
                                shown = Math.max(1, (int)((y - 54 - 12) / 11)); height = shown * 11 + 12;
                            }
                            if (next % 2 == 0) {
                                stream.setNonStrokingColor(new Color(247, 249, 252));
                                stream.addRect(36, y - height, 523, height); stream.fill();
                            }
                            x = 36;
                            for (int c = 0; c < cells.size(); c++) {
                                for (int line = rowLine; line < Math.min(cells.get(c).size(), rowLine + shown); line++)
                                    text(stream, regular, 8, x + 6, y - 15 - (line - rowLine) * 11, cells.get(c).get(line), INK);
                                x += WIDTHS[c];
                            }
                            y -= height; rowLine += shown;
                            if (rowLine >= lines) { next++; rowLine = 0; }
                            stream.setStrokingColor(new Color(220, 228, 236));
                            stream.moveTo(36, y); stream.lineTo(559, y); stream.stroke();
                        }
                        text(stream, regular, 8, 36, 32, "BBC SMS  |  " + entry.getKey() + "  |  Page " + doc.getNumberOfPages(), INK);
                    }
                } while (next < entry.getValue().size());
            }
            doc.save(out); return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("Unable to create timetable PDF", ex); }
    }

    private static PDFont font(PDDocument doc, boolean bold) throws IOException {
        for (String path : List.of("/usr/share/fonts/dejavu/DejaVuSans" + (bold ? "-Bold" : "") + ".ttf",
                "C:/Windows/Fonts/" + (bold ? "arialbd" : "arial") + ".ttf")) {
            File file = new File(path); if (file.isFile()) return PDType0Font.load(doc, file);
        }
        return bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
    private static String printable(PDFont font, String value) throws IOException {
        StringBuilder out = new StringBuilder();
        for (int cp : Objects.toString(value, "—").replaceAll("\\s+", " ").codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            try { font.encode(ch); out.append(ch); } catch (IllegalArgumentException ex) { out.append('?'); }
        }
        return out.toString();
    }
    private static List<String> wrap(PDFont font, String value, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>(); StringBuilder line = new StringBuilder();
        // Character wrapping also handles a long identifier/name without spaces without clipping it.
        for (int cp : printable(font, value).codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            if (font.getStringWidth(line + ch) / 1000 * size > width && !line.isEmpty()) {
                int space = line.lastIndexOf(" ");
                if (space > 0) { lines.add(line.substring(0, space)); line.delete(0, space + 1); }
                else { lines.add(line.toString()); line.setLength(0); }
            }
            line.append(ch);
        }
        lines.add(line.toString()); return lines;
    }
    private static void text(PDPageContentStream stream, PDFont font, float size, float x, float y, String value, Color color) throws IOException {
        stream.setNonStrokingColor(color); stream.beginText(); stream.setFont(font, size);
        stream.newLineAtOffset(x, y); stream.showText(printable(font, value)); stream.endText();
    }
}
