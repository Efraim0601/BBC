import { Injectable, inject, signal } from '@angular/core';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
} from '@angular/router';

const REQUEST_SETTLE_MS = 60;
const MINIMUM_INDICATOR_MS = 260;

/**
 * Coordinates route navigation with the read requests that populate a page.
 *
 * `blocking` masks the routed page immediately, so an empty table/card is never
 * mistaken for a real empty state. The indicator is shown for the entire cycle
 * so the user always understands why the page content is temporarily hidden.
 */
@Injectable({ providedIn: 'root' })
export class GlobalLoadingService {
  private readonly router = inject(Router);

  private cycle = 0;
  private requestCount = 0;
  private routeReady = false;
  private indicatorShownAt = 0;
  private settleTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  readonly blocking = signal(false);
  readonly indicatorVisible = signal(false);

  constructor() {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.beginNavigation();
        return;
      }
      if (
        event instanceof NavigationEnd
        || event instanceof NavigationCancel
        || event instanceof NavigationError
      ) {
        this.routeReady = true;
        this.tryToSettle();
      }
    });
  }

  /** Register a page-populating request. Returns null outside a navigation cycle. */
  beginRequest(): number | null {
    if (!this.blocking()) return null;

    this.requestCount += 1;
    this.clearTimer('settle');
    return this.cycle;
  }

  /** Complete a request ticket. Tickets from an older navigation are ignored. */
  endRequest(ticket: number | null): void {
    if (ticket === null || ticket !== this.cycle || !this.blocking()) return;

    this.requestCount = Math.max(0, this.requestCount - 1);
    this.tryToSettle();
  }

  private beginNavigation(): void {
    this.cycle += 1;
    this.requestCount = 0;
    this.routeReady = false;
    this.blocking.set(true);
    this.clearTimer('settle');
    this.clearTimer('hide');
    this.indicatorShownAt = Date.now();
    this.indicatorVisible.set(true);
  }

  private tryToSettle(): void {
    if (!this.blocking() || !this.routeReady || this.requestCount > 0) return;

    this.clearTimer('settle');
    const cycle = this.cycle;
    this.settleTimer = setTimeout(() => {
      this.settleTimer = null;
      if (cycle !== this.cycle || !this.routeReady || this.requestCount > 0) return;
      this.finishNavigation(cycle);
    }, REQUEST_SETTLE_MS);
  }

  private finishNavigation(cycle: number): void {
    if (cycle !== this.cycle) return;

    if (!this.indicatorVisible()) {
      this.blocking.set(false);
      return;
    }
    const remaining = Math.max(0, MINIMUM_INDICATOR_MS - (Date.now() - this.indicatorShownAt));
    if (remaining === 0) {
      this.blocking.set(false);
      this.indicatorVisible.set(false);
      return;
    }

    this.hideTimer = setTimeout(() => {
      this.hideTimer = null;
      if (cycle !== this.cycle) return;
      this.blocking.set(false);
      this.indicatorVisible.set(false);
    }, remaining);
  }

  private clearTimer(kind: 'settle' | 'hide'): void {
    const timer = kind === 'settle' ? this.settleTimer : this.hideTimer;
    if (timer !== null) clearTimeout(timer);
    if (kind === 'settle') this.settleTimer = null;
    if (kind === 'hide') this.hideTimer = null;
  }
}
