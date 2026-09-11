import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { I18nService } from '../i18n.service';
import { cameroonNationalInput, cameroonPhoneNumber } from '../cameroon-phone';
import { CameroonPhoneFieldComponent } from './cameroon-phone-field';

describe('fixed Cameroon phone field', () => {
  afterEach(() => TestBed.resetTestingModule());

  function render(value = '') {
    TestBed.configureTestingModule({ imports: [CameroonPhoneFieldComponent], providers: [
      { provide: I18nService, useValue: { lang: signal('en') } },
    ] });
    const fixture = TestBed.createComponent(CameroonPhoneFieldComponent);
    fixture.componentRef.setInput('fieldId', 'test-phone');
    fixture.componentRef.setInput('label', 'Phone number');
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture;
  }

  it('shows +237 outside the only editable numeric input and adds it to the value', () => {
    const fixture = render();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(fixture.nativeElement.textContent).toContain('+237');
    expect(fixture.nativeElement.querySelectorAll('input')).toHaveLength(1);
    expect(input.inputMode).toBe('numeric');
    input.value = '600000031';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(input.value).toBe('600000031');
    expect(fixture.componentInstance.value()).toBe('+237600000031');
  });

  it('does not duplicate a pasted prefix or keep a prefix when cleared', () => {
    const fixture = render('+237600000031');
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    for (const number of ['+237 600 000 031', '00237 600 000 031', '237600000031']) {
      input.value = number;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(input.value).toBe('600000031');
      expect(fixture.componentInstance.value()).toBe('+237600000031');
    }
    input.value = '';
    input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.value()).toBe('');
  });

  it('preserves existing stored numbers until the user actually edits the field', () => {
    const fixture = render('600000031');
    expect(fixture.componentInstance.value()).toBe('600000031');
    fixture.componentRef.setInput('value', '+33 612345678');
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe('+33 612345678');
    expect(fixture.nativeElement.querySelector('input').readOnly).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('existing number is preserved');
  });

  it('validates exactly nine digits and does not truncate malformed numbers into another login', () => {
    for (const value of ['600000031', '+237600000031', '00237600000031', '237600000031', '+237(600)000-031']) {
      expect(cameroonNationalInput(value)).toBe('600000031');
      expect(cameroonPhoneNumber(value)).toBe('+237600000031');
    }
    for (const value of ['', '60000003', '6000000319', '+237237600000031', '+33612345678', '600000O31', '123456789']) {
      expect(cameroonPhoneNumber(value)).toBeNull();
    }
  });
});
