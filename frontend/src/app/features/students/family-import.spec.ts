import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import * as XLSX from 'xlsx';
import { I18nService } from '../../core/i18n.service';
import { StudentApi } from './students.api';
import { FamilyImportComponent } from './family-import';

describe('family import dates', () => {
 let component: any;
 beforeEach(() => {
  TestBed.configureTestingModule({providers:[
   {provide:StudentApi,useValue:{listClassOptions:()=>of([]),familyImportDryRun:vi.fn(),familyImportCommit:vi.fn()}},
   {provide:I18nService,useValue:{lang:()=> 'fr'}}]});
  component=TestBed.runInInjectionContext(()=>new FamilyImportComponent());
  component.classId='qa-class';component.accessMode='NO_PORTAL';
 });
 it.each([['04/03/2014','2014-03-04'],['13/10/2015','2015-10-13'],['29/02/2016','2016-02-29'],['2014-03-04','2014-03-04']])('interprets %s without swapping day and month', (input,expected)=>{
  component.text=`nom,prenom,sexe,date_naissance,pere_nom\nQA,,F,${input},QA Father`;
  const row=component.rows()[0];expect(row.dob).toBe(expected);expect(row.firstName).toBe('');expect(row.guardians[0].accessMode).toBe('NO_PORTAL');
 });
 it('uses the actual Excel date even with a US display format', async()=>{
  const sheet=XLSX.utils.aoa_to_sheet([['nom','prenom','sexe','date_naissance','pere_nom'],['QA','','F',new Date(2014,2,4),'QA Father']]);
  sheet['D2'].z='mm/dd/yyyy';const wb=XLSX.utils.book_new();XLSX.utils.book_append_sheet(wb,sheet,'Students');
  const bytes=XLSX.write(wb,{type:'array',bookType:'xlsx'});
  await component.onFile({target:{files:[{name:'qa.xlsx',arrayBuffer:async()=>bytes}],value:'qa.xlsx'}});
  expect(component.rows()[0].dob).toBe('2014-03-04');
 });
 it('keeps an impossible date invalid instead of rolling into the next month',()=>{
  const value=component.normalizeDate('31/02/2015');expect(component.validIsoDate(value)).toBe(false);
 });
 it.each([{status:'VALIDATED',validRows:0},{status:'COMMITTED',validRows:3}])('does not commit an empty or already committed import', result=>{
  component.result.set({jobId:'qa-job',...result});component.commit();
  expect(TestBed.inject(StudentApi).familyImportCommit).not.toHaveBeenCalled();
 });
 it('blocks repeated commit clicks while the first request is pending',()=>{
  component.result.set({jobId:'qa-job',status:'VALIDATED',validRows:3});component.working.set(true);component.commit();
  expect(TestBed.inject(StudentApi).familyImportCommit).not.toHaveBeenCalled();
 });
});
