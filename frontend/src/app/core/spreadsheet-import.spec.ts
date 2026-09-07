import { describe, expect, it } from 'vitest';
import * as XLSX from 'xlsx';
import { StudentsComponent } from '../features/students/students';
import { StaffComponent } from '../features/staff/staff';
import { studentWorkbookCsv } from './spreadsheet-import';

function roundTrip(rows: unknown[][], bookType: 'xlsx'|'biff8'='xlsx') {
  const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet(rows),'Import');
  const bytes=XLSX.write(book,{bookType,type:'array'});
  const parsed=XLSX.read(bytes,{type:'array'});
  return XLSX.utils.sheet_to_csv(parsed.Sheets[parsed.SheetNames[0]]);
}
describe('supported spreadsheet imports after security upgrade',()=>{
  for(const displayFormat of ['m/d/yy','mm/dd/yyyy','dd/mm/yyyy']) it(`preserves real Excel dates displayed as ${displayFormat}`,()=>{
    const book=XLSX.utils.book_new();
    const sheet=XLSX.utils.aoa_to_sheet([['Nom','Prénom','Date de naissance'],['QA Date','','']]);
    sheet['C2']={t:'n',v:42017,z:displayFormat}; // 13 January 2015, independent of display locale.
    XLSX.utils.book_append_sheet(book,sheet,'Import');
    const bytes=XLSX.write(book,{bookType:'xlsx',type:'array'});
    const component=Object.create(StudentsComponent.prototype) as any;
    const rows=component.parseRows(studentWorkbookCsv(bytes,XLSX));
    expect(rows[0].dob).toBe('2015-01-13');
  });
  for(const format of ['xlsx','biff8'] as const) it(`retains three pupils, accents, optional names and day-first dates (${format})`,()=>{
    const csv=roundTrip([
      ['Nom','Prénom','Sexe','Date de naissance','Parent','Téléphone parent'],
      ['Éléonore','','F','13/01/2015','QA guardian',''],
      ['Test','Marie','F','04/05/2016','',''],
      ['Mononyme','','M','30/12/2014','',''],
    ],format);
    const component=Object.create(StudentsComponent.prototype) as any;
    const rows=component.parseRows(csv);
    expect(rows).toHaveLength(3);expect(rows[0].lastName).toBe('Éléonore');expect(rows[0].firstName).toBe('');
    expect(rows.map((r:any)=>r.dob)).toEqual(['2015-01-13','2016-05-04','2014-12-30']);
  });
  it('retains staff without email and separate teaching roles',()=>{
    const csv=roundTrip([
      ['Nom','Sexe','Type','Email','Téléphone','Rôles','Classe','Section'],
      ['QA French','F','Permanent','','+237690777111','teacher','CE1 A','primary'],
      ['QA English','M','Permanent','','+237690777112','teacher','Class 3 A','primary'],
      ['QA Secondary','F','Permanent','','+237690777113','secondary_teacher','6ème A','secondary'],
    ]);
    const component=Object.create(StaffComponent.prototype) as any;
    const rows=component.parseImportRows(csv);expect(rows).toHaveLength(3);
    expect(rows[0].email).toBeUndefined();expect(rows[2].roles).toContain('secondary_teacher');
  });
});
