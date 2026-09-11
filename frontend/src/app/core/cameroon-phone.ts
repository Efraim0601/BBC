/** The fixed +237 UI accepts national digits, or a pasted complete Cameroon number. */
export function cameroonNationalInput(value: string | null | undefined): string {
  const compact = (value ?? '').trim().replace(/[\s().-]/g, '');
  if (compact.startsWith('+237')) return compact.slice(4);
  if (compact.startsWith('00237')) return compact.slice(5);
  if (/^237\d{9}$/.test(compact)) return compact.slice(3);
  return compact;
}

export function cameroonPhoneNumber(value: string | null | undefined): string | null {
  const national = cameroonNationalInput(value);
  return /^[26]\d{8}$/.test(national) ? '+237' + national : null;
}

/** Editing unrelated staff fields must never rewrite an existing foreign contact. */
export function isOtherCountryPhone(value: string | null | undefined): boolean {
  const compact = (value ?? '').trim().replace(/[\s().-]/g, '');
  return /^\+(?!237)\d/.test(compact) || /^00(?!237)\d/.test(compact);
}
