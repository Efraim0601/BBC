import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ScopeService } from './scope.service';
import { Level } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

/**
 * Staff/admin must pick a parcours (Maternelle/Primaire/Secondaire × FR/EN) — or
 * explicitly "all parcours" — before the compartmentalised app opens. Parents have
 * no parcours scope and pass through.
 */
export const scopeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const scope = inject(ScopeService);
  const router = inject(Router);
  const user = auth.user();
  if (!user) return router.createUrlTree(['/login']);
  if (user.role === 'parent') return true;
  return scope.resolved() ? true : router.createUrlTree(['/parcours']);
};

/** Route guard mapped to the same permission matrix as the backend. */
export const permissionGuard = (module: string, level: Level = 'read'): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
    return auth.can(module, level) ? true : router.createUrlTree(['/apps']);
  };
};
