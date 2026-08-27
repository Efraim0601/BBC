import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { StudentApi } from './students.api';
import { StudentRegistrationComponent } from './student-registration';

describe('StudentRegistrationComponent parent access', () => {
  afterEach(() => TestBed.resetTestingModule());

  function setup(): ComponentFixture<StudentRegistrationComponent> {
    TestBed.configureTestingModule({
      imports: [StudentRegistrationComponent],
      providers: [
        { provide: StudentApi, useValue: { listClassOptions: vi.fn(() => of([])) } },
        { provide: I18nService, useValue: { lang: signal('en') } },
        { provide: Router, useValue: {} },
      ],
    });
    TestBed.overrideComponent(StudentRegistrationComponent, { set: { template: '' } });
    const fixture = TestBed.createComponent(StudentRegistrationComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('allows a guardian without email by defaulting to no portal access', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    component.step.set(2);
    component.guardians[0].displayName = 'Parent Without Email';
    component.guardians[0].relationshipType = 'MOTHER';

    component.next();

    expect(component.step()).toBe(3);
    expect(component.guardians[0].accessMode).toBe('NO_PORTAL');
    expect(component.validStep()).toBe(true);
  });

  it('keeps invitation access when the guardian supplied an email', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    component.step.set(2);
    component.guardians[0].displayName = 'Parent With Email';
    component.guardians[0].relationshipType = 'MOTHER';
    component.guardians[0].email = 'parent@example.test';
    component.guardians[0].accessMode = 'SEND_INVITE';

    component.next();

    expect(component.step()).toBe(3);
    expect(component.guardians[0].accessMode).toBe('SEND_INVITE');
    expect(component.validStep()).toBe(true);
  });

  it('accepts a manually entered date and keeps the API value in ISO format', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    component.student.firstName = 'Test';
    component.student.lastName = 'Student';

    component.onDobTextChange('04152018');

    expect(component.dobText).toBe('04/15/2018');
    expect(component.student.dob).toBe('2018-04-15');
    expect(component.validStep()).toBe(true);
  });

  it('rejects an invalid non-empty date while leaving DOB optional', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    component.student.firstName = 'Test';
    component.student.lastName = 'Student';

    component.onDobTextChange('02/30/2018');
    expect(component.student.dob).toBeNull();
    expect(component.validStep()).toBe(false);

    component.onDobTextChange('');
    expect(component.student.dob).toBeNull();
    expect(component.validStep()).toBe(true);
  });

  it('formats a date selected from the calendar companion input', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;

    component.onDobCalendarChange('2018-04-15');

    expect(component.dobText).toBe('04/15/2018');
    expect(component.student.dob).toBe('2018-04-15');
  });
});
