import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, afterEach, describe, it, expect } from 'vitest';
import { SchoolService } from './school.service';
import { AuthService } from './auth.service';

describe('document school identity',()=>{
 let service:SchoolService,http:HttpTestingController,auth:{sessionVersion:number};
 beforeEach(()=>{
  auth={sessionVersion:1};
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:auth}]});
  service=TestBed.inject(SchoolService);http=TestBed.inject(HttpTestingController);
 });
 afterEach(()=>http.verify());
 it('loads printable identity without asking for administrative settings',()=>{
  service.ensureLoaded();service.ensureLoaded();
  http.expectOne(r=>r.url.endsWith('/settings/school/branding')).flush({name:'BBC QA',academicYear:'2026-2027'});
  expect(service.profile()?.academicYear).toBe('2026-2027');service.ensureLoaded();http.expectNone(()=>true);
 });
 it('discards a response from a previous sign-in',()=>{
  service.ensureLoaded();const old=http.expectOne(r=>r.url.endsWith('/branding'));
  auth.sessionVersion=2;service.ensureLoaded();const current=http.expectOne(r=>r.url.endsWith('/branding'));
  current.flush({name:'Current school'});old.flush({name:'Previous school'});
  expect(service.profile()?.name).toBe('Current school');
 });
 it('clears the previous school while loading the next session',()=>{
  service.ensureLoaded();http.expectOne(r=>r.url.endsWith('/branding')).flush({name:'Previous school'});
  auth.sessionVersion=2;service.ensureLoaded();expect(service.profile()).toBeNull();
  http.expectOne(r=>r.url.endsWith('/branding')).flush({name:'Current school'});
 });
 it('never lets a delayed settings update replace a new session profile',()=>{
  service.update({name:'Old edit'} as any).subscribe();const old=http.expectOne(r=>r.method==='PUT');
  auth.sessionVersion=2;service.ensureLoaded();http.expectOne(r=>r.url.endsWith('/branding')).flush({name:'Current school'});
  old.flush({name:'Old edit'});expect(service.profile()?.name).toBe('Current school');
 });
});
