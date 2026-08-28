import { describe, expect, it } from 'vitest';
import { canPickParcoursLevel, hasNoAssignedParcours } from './parcours-picker';

describe('parcours picker scope', () => {
  it('shows no choices for an explicitly scoped Principal without assignments', () => {
    const user = { parcoursScopeMode: 'EXPLICIT', allowedParcours: [] } as const;

    expect(hasNoAssignedParcours(user)).toBe(true);
    expect(canPickParcoursLevel(user, 'maternelle')).toBe(false);
    expect(canPickParcoursLevel(user, 'primary')).toBe(false);
    expect(canPickParcoursLevel(user, 'secondary')).toBe(false);
  });

  it('shows only assigned levels and preserves global access', () => {
    const principal = {
      parcoursScopeMode: 'EXPLICIT',
      allowedParcours: [{ level: 'secondary', subsystem: 'FR' }],
    } as const;
    const accountant = { parcoursScopeMode: 'GLOBAL', allowedParcours: [] } as const;

    expect(hasNoAssignedParcours(principal)).toBe(false);
    expect(canPickParcoursLevel(principal, 'secondary')).toBe(true);
    expect(canPickParcoursLevel(principal, 'primary')).toBe(false);
    expect(canPickParcoursLevel(accountant, 'maternelle')).toBe(true);
    expect(canPickParcoursLevel(accountant, 'primary')).toBe(true);
    expect(canPickParcoursLevel(accountant, 'secondary')).toBe(true);
  });
});
