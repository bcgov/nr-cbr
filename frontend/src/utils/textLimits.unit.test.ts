import { describe, expect, it } from 'vitest';

import { byteLength, overLimitError } from '@/utils/textLimits';

describe('byteLength', () => {
  it('counts an empty or missing value as nothing', () => {
    expect(byteLength('')).toBe(0);
    expect(byteLength(undefined)).toBe(0);
  });

  it('counts plain text a character at a time', () => {
    expect(byteLength('Deadman Creek')).toBe(13);
  });

  it('counts what the column counts, which is not characters', () => {
    // The whole reason this is not `value.length`. Every CBR text column is VARCHAR2(n BYTE), so a
    // character counter would read under the limit on a value Oracle rejects with ORA-12899.
    expect('é'.length).toBe(1);
    expect(byteLength('é')).toBe(2);

    expect('—'.length).toBe(1);
    expect(byteLength('—')).toBe(3);

    // An emoji is a surrogate pair in JavaScript and four bytes in UTF-8 — the two units disagree
    // in both directions.
    expect('🌲'.length).toBe(2);
    expect(byteLength('🌲')).toBe(4);
  });
});

describe('overLimitError', () => {
  it('says nothing about a value that fits', () => {
    expect(overLimitError('Deadman Creek', 255)).toBe('');
    expect(overLimitError(undefined, 255)).toBe('');
  });

  it('says nothing at exactly the limit', () => {
    expect(overLimitError('a'.repeat(255), 255)).toBe('');
  });

  it('names both numbers once the value is over', () => {
    expect(overLimitError('a'.repeat(256), 255)).toBe(
      'Too long — the limit is 255 and this entry uses 256.',
    );
  });

  it('avoids the word "characters", because the limit is not characters', () => {
    // 128 accented characters is 256 bytes. Telling the user they had typed 256 characters would
    // read as a bug; telling them the entry "uses" 256 is simply true.
    const message = overLimitError('é'.repeat(128), 255);

    expect(message).toContain('uses 256');
    expect(message).not.toContain('character');
  });
});
