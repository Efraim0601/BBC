import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { CoursebookApi } from './coursebook.api';
import { CoursebookComponent } from './coursebook';

describe('Coursebook scoped reference lookups', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads assigned classes and curriculum subjects without academic setup access', () => {
    const api = {
      classes: vi.fn(() => of([{ id: 'class-1', name: 'MAT-FR-MS-A', sectionId: 'mat-fr', subsystem: 'FR', level: 'maternelle' }])),
      subjects: vi.fn(() => of([{ id: 'subject-1', code: 'LANGAGE', subsystem: 'FR', label: { fr: 'Langage' }, coef: 1 }])),
      forClass: vi.fn(() => of([])),
      create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    } as unknown as CoursebookApi;

    TestBed.configureTestingModule({
      imports: [CoursebookComponent],
      providers: [
        { provide: CoursebookApi, useValue: api },
        { provide: AuthService, useValue: { can: vi.fn(() => true) } },
        { provide: I18nService, useValue: { lang: signal('fr'), t: vi.fn((key: string) => key) } },
      ],
    });
    TestBed.overrideComponent(CoursebookComponent, { set: { template: '' } });

    const fixture: ComponentFixture<CoursebookComponent> = TestBed.createComponent(CoursebookComponent);
    fixture.detectChanges();

    expect(api.classes).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).classes()).toHaveLength(1);

    (fixture.componentInstance as any).selectClass('MAT-FR-MS-A');
    expect(api.subjects).toHaveBeenCalledWith('MAT-FR-MS-A');
    expect(api.forClass).toHaveBeenCalledWith('MAT-FR-MS-A');
    expect((fixture.componentInstance as any).subjects()[0].code).toBe('LANGAGE');
  });
});
