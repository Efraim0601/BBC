import { PDFDocument, PDFFont, PDFPage, StandardFonts, rgb } from 'pdf-lib';

export interface StudentListPdfRow {
  matricule: string;
  name: string;
  className: string;
  subsystem: string;
  level: string;
  sex: string;
  parent: string;
}

export interface StudentListPdfOptions {
  french: boolean;
  filterSummary: string;
}

const PAGE_WIDTH = 842;
const PAGE_HEIGHT = 595;
const MARGIN = 28;
const TABLE_WIDTH = PAGE_WIDTH - (MARGIN * 2);
const HEADER_HEIGHT = 22;
const ROW_HEIGHT = 18;
const FOOTER_Y = 24;
const HEADER_COLOR = rgb(0.04, 0.16, 0.27);
const BORDER_COLOR = rgb(0.82, 0.86, 0.91);
const MUTED_COLOR = rgb(0.35, 0.42, 0.5);
const ALT_ROW_COLOR = rgb(0.97, 0.98, 0.99);

// The visible student directory is deliberately compact enough to print in
// landscape A4. Excel remains the detailed/editable export; PDF is the clean
// paper-friendly directory view.
const COLUMNS = [
  { key: 'matricule', en: 'ID', fr: 'Matricule', width: 72 },
  { key: 'name', en: 'Student', fr: 'Élève', width: 180 },
  { key: 'className', en: 'Class', fr: 'Classe', width: 126 },
  { key: 'subsystem', en: 'System', fr: 'Système', width: 76 },
  { key: 'level', en: 'Level', fr: 'Niveau', width: 92 },
  { key: 'sex', en: 'Sex', fr: 'Sexe', width: 48 },
  { key: 'parent', en: 'Parent', fr: 'Parent', width: TABLE_WIDTH - 594 },
] as const;

/** Render the already-scoped and already-filtered directory rows to a PDF. */
export async function renderStudentListPdf(
  rows: StudentListPdfRow[],
  options: StudentListPdfOptions,
): Promise<Uint8Array> {
  const document = await PDFDocument.create();
  document.setTitle(options.french ? 'Liste des élèves' : 'Student directory');
  document.setAuthor('BBC SMS');

  const normal = await document.embedFont(StandardFonts.Helvetica);
  const bold = await document.embedFont(StandardFonts.HelveticaBold);
  const pages: PDFPage[] = [];
  let page = addPage(document, pages, normal, bold, rows.length, options);
  let y = drawTableHeader(page, bold, options.french);

  if (!rows.length) {
    drawText(page, normal, options.french ? 'Aucun résultat.' : 'No results.', MARGIN + 8, y - 13, 8, MUTED_COLOR);
  } else {
    rows.forEach((row, index) => {
      if (y - ROW_HEIGHT < FOOTER_Y + 14) {
        drawFooter(page, normal, pages.length);
        page = addPage(document, pages, normal, bold, rows.length, options);
        y = drawTableHeader(page, bold, options.french);
      }
      drawTableRow(page, normal, row, y, index % 2 === 1);
      y -= ROW_HEIGHT;
    });
  }

  drawFooter(page, normal, pages.length);
  return document.save();
}

function addPage(
  document: PDFDocument,
  pages: PDFPage[],
  normal: PDFFont,
  bold: PDFFont,
  count: number,
  options: StudentListPdfOptions,
): PDFPage {
  const page = document.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
  pages.push(page);
  const title = options.french ? 'Liste des élèves' : 'Student directory';
  const countLabel = options.french
    ? `${count} résultat${count === 1 ? '' : 's'}`
    : `${count} result${count === 1 ? '' : 's'}`;
  drawText(page, bold, title, MARGIN, PAGE_HEIGHT - MARGIN, 16, HEADER_COLOR);
  drawText(page, normal, `${countLabel} · ${options.filterSummary}`, MARGIN, PAGE_HEIGHT - MARGIN - 19, 8, MUTED_COLOR);
  return page;
}

function drawTableHeader(page: PDFPage, font: PDFFont, french: boolean): number {
  const top = PAGE_HEIGHT - 79;
  let x = MARGIN;
  for (const column of COLUMNS) {
    page.drawRectangle({
      x,
      y: top - HEADER_HEIGHT,
      width: column.width,
      height: HEADER_HEIGHT,
      color: HEADER_COLOR,
      borderColor: HEADER_COLOR,
      borderWidth: 0.5,
    });
    drawText(page, font, french ? column.fr : column.en, x + 5, top - 15, 7.5, rgb(1, 1, 1));
    x += column.width;
  }
  return top - HEADER_HEIGHT;
}

function drawTableRow(page: PDFPage, font: PDFFont, row: StudentListPdfRow, top: number, alternate: boolean): void {
  let x = MARGIN;
  for (const column of COLUMNS) {
    page.drawRectangle({
      x,
      y: top - ROW_HEIGHT,
      width: column.width,
      height: ROW_HEIGHT,
      color: alternate ? ALT_ROW_COLOR : rgb(1, 1, 1),
      borderColor: BORDER_COLOR,
      borderWidth: 0.45,
    });
    const value = row[column.key];
    const text = fitText(font, value, column.width - 10, 7.2);
    drawText(page, font, text, x + 5, top - 12.5, 7.2, rgb(0.1, 0.15, 0.2));
    x += column.width;
  }
}

function drawFooter(page: PDFPage, font: PDFFont, pageNumber: number): void {
  drawText(page, font, `BBC SMS · ${new Date().toISOString().slice(0, 10)}`, MARGIN, FOOTER_Y, 7, MUTED_COLOR);
  const label = `Page ${pageNumber}`;
  drawText(page, font, label, PAGE_WIDTH - MARGIN - font.widthOfTextAtSize(label, 7), FOOTER_Y, 7, MUTED_COLOR);
}

function drawText(page: PDFPage, font: PDFFont, value: string, x: number, y: number, size: number, color: ReturnType<typeof rgb>): void {
  const text = encodable(font, value);
  page.drawText(text, { x, y, size, font, color });
}

function fitText(font: PDFFont, value: string, maxWidth: number, size: number): string {
  const text = encodable(font, value);
  if (font.widthOfTextAtSize(text, size) <= maxWidth) return text;
  const suffix = '...';
  let result = '';
  for (const character of text) {
    const candidate = result + character + suffix;
    if (font.widthOfTextAtSize(candidate, size) > maxWidth) break;
    result += character;
  }
  return result ? result + suffix : suffix;
}

/** Standard PDF fonts use WinAnsi; preserve French accents and replace the rest safely. */
function encodable(font: PDFFont, value: string): string {
  const text = String(value ?? '').replace(/\s+/g, ' ').trim();
  try {
    font.encodeText(text);
    return text;
  } catch {
    const folded = text.normalize('NFKD').replace(/[\u0300-\u036f]/g, '');
    const safe = folded.replace(/[^\x20-\x7E\xA0-\xFF]/g, '?');
    try {
      font.encodeText(safe);
      return safe;
    } catch {
      return safe.replace(/[^\x20-\x7E]/g, '?');
    }
  }
}
