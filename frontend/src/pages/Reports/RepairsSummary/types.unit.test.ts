import { describe, expect, it } from 'vitest';

import { repairClassOptions } from './types';

describe('repairClassOptions', () => {
  it('lists each repair class, then every pair of them, as legacy builds them', () => {
    expect(
      repairClassOptions([
        { code: 'S', description: 'Structural' },
        { code: 'N', description: 'Non-structural' },
        { code: 'U', description: 'Unknown' },
      ]),
    ).toEqual([
      { code: 'S', description: 'Structural' },
      { code: 'N', description: 'Non-structural' },
      { code: 'U', description: 'Unknown' },
      { code: 'S&N', description: 'Structural and Non-structural' },
      { code: 'S&U', description: 'Structural and Unknown' },
      { code: 'N&U', description: 'Non-structural and Unknown' },
    ]);
  });

  it('has nothing to pair when there is one class or none', () => {
    expect(repairClassOptions([{ code: 'S', description: 'Structural' }])).toHaveLength(1);
    expect(repairClassOptions([])).toEqual([]);
  });
});
