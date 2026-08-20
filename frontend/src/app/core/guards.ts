import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ScopeService } from './scope.service';
import { Level } from './models';
import { catchError, map, of } from 'rxjs';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

/** The parent portal is a role boundary, not an empty shell for staff users. */
export const parentGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
  return auth.user()?.role === 'parent' ? true : router.createUrlTree(['/apps']);
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
  const global = user.parcoursScopeMode === 'GLOBAL'
    || (user.parcoursScopeMode == null && (user.allowedParcours ?? []).length === 0);
  if (global) return scope.resolved() ? true : router.createUrlTree(['/parcours']);

  const selected = scope.scope();
  const valid = !scope.allMode() && selected != null
    && (user.allowedParcours ?? []).some((allowed) =>
      allowed.level === selected.level && allowed.subsystem === selected.subsystem);
  if (valid) return true;

  // A previous administrator may have left "All parcours" or another scope in
  // this browser. Never reuse it after a restricted principal signs in.
  scope.clear();
  return router.createUrlTree(['/parcours']);
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

/**
 * Opens a resource-scoped module for action-authorized users whose legacy
 * module matrix is empty.  CONTEXT_REQUIRED is deliberate here: the module
 * establishes the resource context and the API remains the authorization
 * boundary for each row/action.
 */
export const contextualActionGuard = (actionCode: string): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
    const redirect = router.createUrlTree(['/apps']);
    const allowed = () => {
      const state = auth.actionState(actionCode);
      return state === 'ALLOW' || state === 'CONTEXT_REQUIRED';
    };
    if (!auth.capabilities() || auth.actionState(actionCode) === 'LOADING') {
      return auth.loadCapabilities().pipe(
        map(() => allowed() ? true : redirect),
        catchError(() => of(redirect)),
      );
    }
    return allowed() ? true : redirect;
  };
};

/** Action-aware guard; waits for server capabilities instead of falling back to module write. */
export const actionGuard = (actionCode: string): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
    const redirect = router.createUrlTree(['/apps']);
    if (!auth.capabilities() || auth.actionState(actionCode) === 'LOADING') {
      return auth.loadCapabilities().pipe(
        map(() => auth.canAction(actionCode) ? true : redirect),
        catchError(() => of(redirect)),
      );
    }
    return auth.canAction(actionCode) ? true : redirect;
  };
};
