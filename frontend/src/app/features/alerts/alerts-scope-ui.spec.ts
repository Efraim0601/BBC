import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AlertsApi } from './alerts.api';
import { AlertsComponent } from './alerts';

describe('Alerts scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('contains a denied list lookup in an explicit unavailable state', () => {
    const api = {
      list: vi.fn(() => throwError(() => new Error('403 FORBIDDEN'))),
      scan: vi.fn(), ack: vi.fn(), resolve: vi.fn(),
    } as unknown as AlertsApi;

    TestBed.configureTestingModule({
      imports: [AlertsComponent],
      providers: [
        { provide: AlertsApi, useValue: api },
        { provide: AuthService, useValue: { can: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
      ],
    });
    TestBed.overrideComponent(AlertsComponent, { set: { template: '' } });

    const fixture: ComponentFixture<AlertsComponent> = TestBed.createComponent(AlertsComponent);
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).alertsUnavailable()).toBe(true);
  });
});
