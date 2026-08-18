import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { GlobalLoadingService } from './global-loading.service';

/** Opt out a GET that must not participate in initial page readiness. */
export const SKIP_GLOBAL_LOADING = new HttpContextToken<boolean>(() => false);

/**
 * Tracks read requests started while the router is opening a page. Mutations and
 * later in-page refreshes retain their feature-specific busy state and do not
 * freeze the whole application.
 */
export const globalLoadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loading = inject(GlobalLoadingService);
  const tracked = req.method === 'GET' && !req.context.get(SKIP_GLOBAL_LOADING);
  const ticket = tracked ? loading.beginRequest() : null;

  return next(req).pipe(finalize(() => loading.endRequest(ticket)));
};
