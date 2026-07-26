import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ScopeService } from './scope.service';

/**
 * Attaches the Bearer access token (and the active parcours scope as `X-Parcours`);
 * on a 401 tries one silent refresh (single-flight via AuthService), then retries
 * the original request. Only a rejected refresh (401/403) forces logout — network
 * blips keep the session so the user is not kicked out spuriously.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const scope = inject(ScopeService);
  const token = auth.accessToken;

  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const parcours = scope.header();
  if (parcours) headers['X-Parcours'] = parcours;

  const authed = Object.keys(headers).length ? req.clone({ setHeaders: headers }) : req;

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.includes('/auth/');
      const isPublicApi = req.url.includes('/public/');
      if (err.status === 401 && !isAuthCall && !isPublicApi) {
        return auth.refresh().pipe(
          switchMap((res) =>
            next(
              req.clone({
                setHeaders: { ...headers, Authorization: `Bearer ${res.accessToken}` },
              }),
            ),
          ),
          catchError((refreshErr: unknown) => {
            if (auth.isSessionInvalid(refreshErr)) {
              auth.logout('expired');
            }
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};
