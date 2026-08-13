import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { JourneyComponent } from './journey';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { JourneyApi } from './journey.api';
import { SetupApi } from '../../core/setup.api';

describe('JourneyComponent promotion action', () => {
  function create(canJourneyWrite: boolean): ComponentFixture<JourneyComponent> {
    TestBed.configureTestingModule({
      imports: [JourneyComponent],
      providers: [
        { provide: AuthService, useValue: { can: vi.fn(() => canJourneyWrite) } },
        { provide: I18nService, useValue: { lang: vi.fn(() => 'en'), t: vi.fn((key: string) => key) } },
        { provide: JourneyApi, useValue: {} },
        { provide: SetupApi, useValue: { listClasses: vi.fn(() => of([])) } },
      ],
    });
    TestBed.overrideComponent(JourneyComponent, {
      set: { template: `@if (canWrite()) { <a id="promotion-action">End-of-year promotions</a> }` },
    });
    const fixture = TestBed.createComponent(JourneyComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the promotion action to a journey-write principal', () => {
    const fixture = create(true);
    expect(fixture.nativeElement.querySelector('#promotion-action')).not.toBeNull();
  });

  it('hides the promotion action from a read-only principal', () => {
    const fixture = create(false);
    expect(fixture.nativeElement.querySelector('#promotion-action')).toBeNull();
  });
});
