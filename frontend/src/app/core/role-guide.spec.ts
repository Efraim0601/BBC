import { describe, expect, it } from 'vitest';
import { guideHrefForRole } from './role-guide';

describe('guideHrefForRole', () => {
  it.each([
    ['administrator', '/guide/roles/administrator.html'],
    ['admin_primary', '/guide/roles/administrator.html'],
    ['principal', '/guide/roles/principal.html'],
    ['principal_legacy_compat', '/guide/roles/principal.html'],
    ['prefect', '/guide/roles/prefect.html'],
    ['accountant', '/guide/roles/accountant.html'],
    ['finance_collector', '/guide/roles/accountant.html'],
    ['teacher', '/guide/roles/primary-teacher.html'],
    ['secondary_teacher', '/guide/roles/secondary-teacher.html'],
    ['form_teacher', '/guide/roles/secondary-teacher.html'],
    ['parent', '/guide/roles/parent.html'],
  ])('maps %s to its role manual', (role, expected) => {
    expect(guideHrefForRole(role)).toBe(expected);
  });

  it('falls back to the complete guide for unknown or missing roles', () => {
    expect(guideHrefForRole('unknown_role')).toBe('/guide/');
    expect(guideHrefForRole(undefined)).toBe('/guide/');
  });
});
