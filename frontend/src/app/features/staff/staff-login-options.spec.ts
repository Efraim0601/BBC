import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { afterEach, describe, expect, it } from 'vitest';
import { I18nService } from '../../core/i18n.service';
import { StaffLoginOptionsComponent, validStaffUsername, staffPhoneLogin, staffLoginIdentifier } from './staff-login-options';
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

  it('offers email and phone explicitly and asks for the selected contact', async () => {
    const fixture = await render('');
    expect(fixture.nativeElement.querySelectorAll('input[type=radio]').length).toBe(2);
    expect(fixture.nativeElement.querySelector('input[type=checkbox]').disabled).toBe(true);
    expect(fixture.componentInstance.sendEmail()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('generated password will appear');
    expect(fixture.nativeElement.textContent).toContain('Enter a valid email address');
  });

  it('phone choice needs no email, previews the actual login and cancels email delivery', async () => {
    const fixture = await render('qa@example.test');
    fixture.componentRef.setInput('phone', '600 000 001');
    fixture.componentInstance.sendEmail.set(true);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('input[value=phone]').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.loginMethod()).toBe('phone');
    expect(fixture.componentInstance.sendEmail()).toBe(false);
    expect(fixture.nativeElement.querySelector('input[type=checkbox]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('+237600000001');
    fixture.componentRef.setInput('email', '');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('+237600000001');
  });

  it('normalizes phone formats and validates only the chosen credential', () => {
    for (const number of ['600000001', '+237 600 000 001', '00237 600 000 001', '237600000001']) {
      expect(staffPhoneLogin(number)).toBe('+237600000001');
    }
    expect(staffPhoneLogin('123')).toBeNull();
    expect(staffPhoneLogin('phone600000001')).toBeNull();
    expect(staffPhoneLogin('+000000000')).toBeNull();
    expect(staffLoginIdentifier('phone', '', '600000001')).toBe('+237600000001');
    expect(staffLoginIdentifier('email', ' Staff@Example.test ', '')).toBe('staff@example.test');
    expect(staffLoginIdentifier('email', '', '600000001')).toBeNull();
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
    expect(fixture.nativeElement.querySelectorAll('input[type=radio]').length).toBe(0);
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
