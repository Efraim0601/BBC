import { TestBed } from '@angular/core/testing';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GlobalLoadingService } from './global-loading.service';

describe('GlobalLoadingService', () => {
  let events: Subject<unknown>;

  beforeEach(() => {
    vi.useFakeTimers();
    events = new Subject<unknown>();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  function setup(): GlobalLoadingService {
    TestBed.configureTestingModule({
      providers: [
        GlobalLoadingService,
        { provide: Router, useValue: { events: events.asObservable() } },
      ],
    });
    return TestBed.inject(GlobalLoadingService);
  }

  it('keeps the page masked until navigation reads have completed', () => {
    const service = setup();

    events.next(new NavigationStart(1, '/students'));
    const request = service.beginRequest();
    events.next(new NavigationEnd(1, '/students', '/students'));

    expect(service.blocking()).toBe(true);
    expect(service.indicatorVisible()).toBe(true);

    service.endRequest(request);
    vi.advanceTimersByTime(60);
    expect(service.blocking()).toBe(true);

    vi.advanceTimersByTime(200);
    expect(service.blocking()).toBe(false);
    expect(service.indicatorVisible()).toBe(false);
  });

  it('shows an indicator even for a fast route without reads', () => {
    const service = setup();

    events.next(new NavigationStart(2, '/apps'));
    events.next(new NavigationEnd(2, '/apps', '/apps'));
    vi.advanceTimersByTime(60);

    expect(service.blocking()).toBe(true);
    expect(service.indicatorVisible()).toBe(true);

    vi.advanceTimersByTime(200);
    expect(service.blocking()).toBe(false);
    expect(service.indicatorVisible()).toBe(false);
  });

  it('ignores completion tickets from a previous navigation', () => {
    const service = setup();

    events.next(new NavigationStart(3, '/students'));
    const staleRequest = service.beginRequest();
    events.next(new NavigationStart(4, '/settings'));
    const currentRequest = service.beginRequest();
    events.next(new NavigationEnd(4, '/settings', '/settings'));

    service.endRequest(staleRequest);
    vi.advanceTimersByTime(60);
    expect(service.blocking()).toBe(true);

    service.endRequest(currentRequest);
    vi.advanceTimersByTime(60);
    expect(service.blocking()).toBe(true);

    vi.advanceTimersByTime(140);
    expect(service.blocking()).toBe(false);
  });
});
