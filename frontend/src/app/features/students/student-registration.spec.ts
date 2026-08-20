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
});
