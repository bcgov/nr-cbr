import { afterEach, describe, expect, it, vi } from 'vitest';

import { clearSessionState, readSessionState, writeSessionState } from './sessionState';

afterEach(() => {
  vi.restoreAllMocks();
  sessionStorage.clear();
});

describe('sessionState', () => {
  it('reads back what was written', () => {
    writeSessionState('k', { page: 3, sort: null });

    expect(readSessionState('k')).toEqual({ page: 3, sort: null });
  });

  it('reads null when nothing was saved, or after it was cleared', () => {
    expect(readSessionState('k')).toBeNull();

    writeSessionState('k', 1);
    clearSessionState('k');

    expect(readSessionState('k')).toBeNull();
  });

  it('reads null rather than throwing on a value that is not JSON', () => {
    sessionStorage.setItem('k', '{not json');

    expect(readSessionState('k')).toBeNull();
  });

  it('carries on when storage refuses a write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    expect(() => writeSessionState('k', 1)).not.toThrow();
  });
});
