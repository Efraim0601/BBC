import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { LoginComponent } from './login';

describe('phone or existing identifier sign-in', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    const auth = { login: vi.fn(() => of({ user: { role: 'accountant' } })), forgotPassword: vi.fn(() => of({ message: 'OK' })) };
    TestBed.configureTestingModule({ imports: [LoginComponent], providers: [
      { provide: AuthService, useValue: auth },
      { provide: Router, useValue: { navigate: vi.fn() } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      { provide: I18nService, useValue: { lang: signal('en'), t: (key: string) => key } },
    ] });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance as any;
    return { fixture, component, auth };
  }

  it('keeps email and legacy username sign-in unchanged', async () => {
    const { component, auth } = await render();
    component.username = ' admin ';
    component.password = 'ExistingPassword';
    component.submit();
    expect(auth.login).toHaveBeenLastCalledWith('admin', 'ExistingPassword');
    component.username = 'staff@example.test';
    component.submit();
    expect(auth.login).toHaveBeenLastCalledWith('staff@example.test', 'ExistingPassword');
  });

  it('shows the fixed prefix in phone mode and posts a single complete prefix', async () => {
    const { fixture, component, auth } = await render();
    component.chooseIdentifier('phone');
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#login-phone') as HTMLInputElement;
    expect(input.inputMode).toBe('numeric');
    expect(fixture.nativeElement.querySelector('#login-identifier')).toBeNull();
    expect(input.parentElement!.textContent).toContain('+237');
    for (const value of ['600000031', '+237 600 000 031', '00237600000031']) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      component.password = 'GeneratedPassword';
      component.submit();
      expect(auth.login).toHaveBeenLastCalledWith('+237600000031', 'GeneratedPassword');
    }
  });

  it('rejects incomplete or too-long numbers before any login request', async () => {
    const { component, auth } = await render();
    component.chooseIdentifier('phone');
    component.password = 'GeneratedPassword';
    for (const value of ['', '+237', '+23760000003', '+2376000000319']) {
      component.phone = value;
      component.submit();
      expect(component.error()).toContain('9 phone-number digits');
    }
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('uses the same prefix for recovery and preserves typed values when switching modes', async () => {
    const { fixture, component, auth } = await render();
    component.username = 'legacy.user';
    component.chooseIdentifier('phone');
    component.phone = '+237600000031';
    component.openForgot();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#forgot-phone').value).toBe('600000031');
    component.submitForgot();
    expect(auth.forgotPassword).toHaveBeenCalledWith('+237600000031');
    component.chooseIdentifier('identifier');
    component.backToLogin();
    expect(component.signInIdentifier()).toBe('legacy.user');
    component.chooseIdentifier('phone');
    expect(component.signInIdentifier()).toBe('+237600000031');
  });
});
