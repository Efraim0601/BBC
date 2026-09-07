/** Use Excel's date value, never the cell's locale-dependent displayed text. */
export function studentWorkbookCsv(data: ArrayBuffer | string | null, xlsx: typeof import('xlsx')): string {
  const book = xlsx.read(data, { type: 'array', cellNF: true });
  const sheet = book.Sheets[book.SheetNames[0]];
  if (!sheet) throw new Error('No worksheet');
  for (const address of Object.keys(sheet)) {
    if (address.startsWith('!')) continue;
    const cell = sheet[address];
    if (cell.t !== 'n' || typeof cell.v !== 'number' || !xlsx.SSF.is_date(String(cell.z ?? ''))) continue;
    const date = xlsx.SSF.parse_date_code(cell.v, { date1904: !!book.Workbook?.WBProps?.date1904 });
    if (!date) continue;
    const iso = `${String(date.y).padStart(4, '0')}-${String(date.m).padStart(2, '0')}-${String(date.d).padStart(2, '0')}`;
    cell.t = 's'; cell.v = iso; cell.w = iso;
    delete cell.z;
  }
  return xlsx.utils.sheet_to_csv(sheet);
}
