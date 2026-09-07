import { afterEach, describe, expect, it } from 'vitest';
import { ScopeService } from './scope.service';

describe('ScopeService persisted context recovery', () => {
  afterEach(() => localStorage.clear());

  it('recovers from malformed JSON without making all parcours available', () => {
    localStorage.setItem('bbc-parcours', '{broken');
    const scope = new ScopeService();
    expect(scope.scope()).toBeNull();
    expect(scope.allMode()).toBe(false);
    expect(scope.resolved()).toBe(false);
  });

  it('rejects unknown level or subsystem values', () => {
    localStorage.setItem('bbc-parcours', JSON.stringify({ level: 'all', subsystem: '*' }));
    expect(new ScopeService().scope()).toBeNull();
  });

  it('preserves an ordinary valid selection', () => {
    localStorage.setItem('bbc-parcours', JSON.stringify({ level: 'primary', subsystem: 'FR' }));
    expect(new ScopeService().header()).toBe('primary:FR');
  });
});
