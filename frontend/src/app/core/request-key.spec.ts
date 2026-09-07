import { describe, expect, it, vi } from 'vitest';
import { newRequestKey } from './request-key';

describe('financial request keys', () => {
  it('are unique and safe for HTTP headers', () => {
    const keys = new Set(Array.from({length:100},()=>newRequestKey()));
    expect(keys.size).toBe(100);
    for (const key of keys) expect(key).toMatch(/^[0-9a-f]{32}$/);
  });
  it('does not depend on randomUUID, unavailable on the HTTP test instance', () => {
    const uuid=vi.spyOn(crypto,'randomUUID').mockImplementation(()=>{throw new Error('insecure context');});
    try { expect(newRequestKey()).toHaveLength(32); expect(uuid).not.toHaveBeenCalled(); }
    finally {uuid.mockRestore();}
  });
});
