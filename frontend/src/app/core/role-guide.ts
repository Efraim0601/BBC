const ROLE_GUIDE_BY_ROLE: Readonly<Record<string, string>> = {
  administrator: 'administrator',
  admin_maternelle: 'administrator',
  admin_primary: 'administrator',
  admin_secondary: 'administrator',
  principal: 'principal',
  principal_legacy_compat: 'principal',
  prefect: 'prefect',
  accountant: 'accountant',
  econome: 'accountant',
  finance_collector: 'accountant',
  teacher: 'primary-teacher',
  secondary_teacher: 'secondary-teacher',
  form_teacher: 'secondary-teacher',
  parent: 'parent',
};

/** Returns the operational manual that best matches the authenticated role. */
export function guideHrefForRole(role: string | null | undefined, baseHref = '/'): string {
  const slug = role ? ROLE_GUIDE_BY_ROLE[role.toLowerCase()] : undefined;
  const pathname = new URL(baseHref, 'https://bbc.invalid/').pathname;
  const basePath = pathname.endsWith('/') ? pathname : `${pathname}/`;
  return slug ? `${basePath}guide/roles/${slug}.html` : `${basePath}guide/index.html`;
}
