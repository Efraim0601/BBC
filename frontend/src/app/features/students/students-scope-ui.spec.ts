import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { StudentApi } from './students.api';
import { StudentRegistrationComponent } from './student-registration';

describe('registrar student class-option boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('contains a denied class-option lookup without an unhandled UI error', () => {
    const api = {
      listClassOptions: vi.fn(() => throwError(() => new Error('403 FORBIDDEN'))),
    } as unknown as StudentApi;

    TestBed.configureTestingModule({
      imports: [StudentRegistrationComponent],
      providers: [
        { provide: StudentApi, useValue: api },
        { provide: I18nService, useValue: { lang: vi.fn(() => 'en') } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    TestBed.overrideComponent(StudentRegistrationComponent, { set: { template: '' } });
    const fixture: ComponentFixture<StudentRegistrationComponent> = TestBed.createComponent(StudentRegistrationComponent);
    fixture.detectChanges();

    expect(api.listClassOptions).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).classes()).toEqual([]);
    expect((fixture.componentInstance as any).classOptionsUnavailable()).toBe(true);
  });
});
