import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { afterEach, describe, expect, it } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { StaffLoginOptionsComponent, validStaffUsername } from './staff-login-options';
import { staffWhatsappNumber } from './staff-credentials-dialog';

describe('staff credential delivery choices', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render(email: string): Promise<ComponentFixture<StaffLoginOptionsComponent>> {
    TestBed.configureTestingModule({
      imports: [StaffLoginOptionsComponent],
      providers: [{ provide: I18nService, useValue: { lang: signal('en') } }],
    });
    const fixture = TestBed.createComponent(StaffLoginOptionsComponent);
    fixture.componentRef.setInput('email', email);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('allows username entry without contact details and disables only email delivery', async () => {
    const fixture = await render('');
    expect(fixture.nativeElement.querySelector('input[type=text]').disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('input[type=checkbox]').disabled).toBe(true);
    expect(fixture.componentInstance.sendEmail()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('username and password will appear');
  });

  it('keeps email delivery unchecked even when an email address is available', async () => {
    const fixture = await render('qa@example.test');
    const checkbox = fixture.nativeElement.querySelector('input[type=checkbox]');
    expect(checkbox.disabled).toBe(false);
    expect(checkbox.checked).toBe(false);
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.sendEmail()).toBe(true);
  });

  it('accepts automatic usernames and rejects names the server cannot accept', () => {
    expect(validStaffUsername('')).toBe(true);
    expect(validStaffUsername(' teacher.test_1 ')).toBe(true);
    expect(validStaffUsername('ab')).toBe(false);
    expect(validStaffUsername('teacher test')).toBe(false);
    expect(validStaffUsername('a'.repeat(65))).toBe(false);
  });

  it('keeps an existing legacy username read-only without applying new-account validation', async () => {
    const fixture = await render('');
    fixture.componentRef.setInput('existing', true);
    fixture.componentRef.setInput('username', 'ab');
    fixture.detectChanges();
    await fixture.whenStable();
    const username = fixture.nativeElement.querySelector('input[type=text]');
    expect(username.readOnly).toBe(true);
    expect(username.getAttribute('aria-invalid')).toBe('false');
    expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
  });

  it('normalizes international WhatsApp numbers without guessing a destination country', () => {
    expect(staffWhatsappNumber('+237 600 000 001')).toBe('237600000001');
    expect(staffWhatsappNumber('00237 600 000 001')).toBe('237600000001');
    expect(staffWhatsappNumber('600000001')).toBeNull();
    expect(staffWhatsappNumber('')).toBeNull();
    expect(staffWhatsappNumber('+000000000')).toBeNull();
  });
});
