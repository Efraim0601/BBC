import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { renderStudentListPdf } from './student-list-pdf';

describe('student list PDF export', () => {
  it('creates a readable PDF and paginates a long directory', async () => {
    const rows = Array.from({ length: 35 }, (_, index) => ({
      matricule: `BBC-${index + 1}`,
      name: index === 0 ? 'Élève Aïcha' : `Student ${index + 1}`,
      className: index % 2 ? 'Class 1 A' : 'SIL A',
      subsystem: index % 2 ? 'English' : 'Francophone',
      level: 'Primary',
      sex: index % 2 ? 'F' : 'M',
      parent: `Parent ${index + 1} · +237 600 000 000`,
    }));

    const bytes = await renderStudentListPdf(rows, { french: false, filterSummary: 'All visible students' });
    expect(new TextDecoder().decode(bytes.slice(0, 5))).toBe('%PDF-');
    const document = await PDFDocument.load(bytes);
    expect(document.getPages().length).toBeGreaterThan(1);
  });

  it('still produces a valid PDF for an empty filtered result', async () => {
    const bytes = await renderStudentListPdf([], { french: true, filterSummary: 'Classe CE1' });
    const document = await PDFDocument.load(bytes);
    expect(document.getPages()).toHaveLength(1);
  });
});
