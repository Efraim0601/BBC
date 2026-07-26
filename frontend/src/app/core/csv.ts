/**
 * CSV download shared by every module's "Exporter / Export" button.
 *
 * The BOM matters: without it Excel on Windows reads the file as latin-1 and
 * mangles every accented name (Aïcha, Cédric, Élève…), which is most of them here.
 */

export type CsvCell = string | number | boolean | null | undefined;

const quote = (v: CsvCell): string => `"${String(v ?? '').replace(/"/g, '""')}"`;

export function toCsv(headers: string[], rows: CsvCell[][]): string {
  return [headers.map(quote).join(','), ...rows.map((r) => r.map(quote).join(','))].join('\r\n');
}

/** Trigger a browser download of `rows` as CSV. Header-only files (empty rows) are allowed for import templates. */
export function downloadCsv(filename: string, headers: string[], rows: CsvCell[][]): boolean {
  const blob = new Blob(['\uFEFF' + toCsv(headers, rows)], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  return true;
}

/** `students-2026-07-16.csv` — stamps an export so successive downloads don't collide. */
export function stampedName(prefix: string): string {
  return `${prefix}-${new Date().toISOString().slice(0, 10)}.csv`;
}
