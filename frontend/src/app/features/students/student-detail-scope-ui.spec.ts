import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { AuthService } from '../../core/auth.service';
import { PhotoApi } from '../../core/photo.api';
import { StudentApi } from './students.api';
import { StudentDetailComponent } from './student-detail';

describe('Student detail optional-read scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('contains a denied guardian lookup without an unhandled UI error', () => {
    const api = {
      get: vi.fn(() => of({})),
      guardians: vi.fn(() => throwError(() => new Error('403 POLICY_RULE_MISSING'))),
      listClassOptions: vi.fn(() => of([])),
    } as unknown as StudentApi;
    const photoApi = { load: vi.fn(() => of(null)) } as unknown as PhotoApi;

    TestBed.configureTestingModule({
      imports: [StudentDetailComponent],
      providers: [
        { provide: StudentApi, useValue: api },
        { provide: PhotoApi, useValue: photoApi },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'student-1' } } } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
        { provide: AuthService, useValue: { actionState: vi.fn(() => 'DENY') } },
      ],
    });
    TestBed.overrideComponent(StudentDetailComponent, { set: { template: '' } });

    let fixture: ComponentFixture<StudentDetailComponent> | undefined;
    expect(() => {
      fixture = TestBed.createComponent(StudentDetailComponent);
      fixture.detectChanges();
    }).not.toThrow();

    expect(api.guardians).toHaveBeenCalledWith('student-1');
    expect((fixture!.componentInstance as any).guardians()).toEqual([]);
  });
});
