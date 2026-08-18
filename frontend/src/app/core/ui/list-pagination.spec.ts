import { describe, expect, it } from 'vitest';
import { clampPage, paginateRows } from './list-pagination';

describe('list pagination helpers', () => {
  it('returns the requested page without mutating the source list', () => {
    const rows = Array.from({ length: 32 }, (_, index) => index + 1);

    expect(paginateRows(rows, 2, 10)).toEqual([11, 12, 13, 14, 15, 16, 17, 18, 19, 20]);
    expect(rows).toHaveLength(32);
  });

  it('clamps a stale page after filtering reduces the result count', () => {
    expect(clampPage(8, 12, 10)).toBe(2);
    expect(paginateRows([1, 2, 3], 8, 10)).toEqual([1, 2, 3]);
  });

  it('handles empty lists and invalid sizes safely', () => {
    expect(clampPage(0, 0, 0)).toBe(1);
    expect(paginateRows([], -4, 0)).toEqual([]);
  });
});
