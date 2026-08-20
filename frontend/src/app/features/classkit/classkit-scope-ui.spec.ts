import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { SetupApi } from '../../core/setup.api';
import { ClassKitApi } from './classkit.api';
import { ClasskitComponent } from './classkit';

describe('ClassKit scope boundary', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('turns denied setup lookups into a visible unavailable state without an unhandled error', () => {
    const setupApi = {
      listClasses: vi.fn(() => throwError(() => new Error('403 FORBIDDEN'))),
      listSubjects: vi.fn(() => throwError(() => new Error('403 FORBIDDEN'))),
    } as unknown as SetupApi;
    const classKitApi = { ofClass: vi.fn() } as unknown as ClassKitApi;

    TestBed.configureTestingModule({
      imports: [ClasskitComponent],
      providers: [
        { provide: SetupApi, useValue: setupApi },
        { provide: ClassKitApi, useValue: classKitApi },
        { provide: AuthService, useValue: { can: vi.fn(() => false) } },
        { provide: I18nService, useValue: { lang: signal('fr') } },
      ],
    });
    TestBed.overrideComponent(ClasskitComponent, { set: { template: '' } });

    const fixture: ComponentFixture<ClasskitComponent> = TestBed.createComponent(ClasskitComponent);
    fixture.detectChanges();

    expect(setupApi.listClasses).toHaveBeenCalledTimes(1);
    expect(setupApi.listSubjects).toHaveBeenCalledTimes(1);
    expect((fixture.componentInstance as any).setupUnavailable()).toBe(true);
  });
});
