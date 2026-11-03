import { describe, expect, it } from 'vitest';

import {
  EMPTY_STRUCTURE_VALUES,
  structureErrorsOf,
  structureWarningsOf,
  toStructureRequest,
  type StructureValues,
} from './structureRules';

const costs = (installationCost: string, materialCost = ''): StructureValues => ({
  ...EMPTY_STRUCTURE_VALUES,
  installationCost,
  materialCost,
});

describe('Installation Cost (CBR-816)', () => {
  it('takes up to $4,999,999 and refuses more', () => {
    expect(structureErrorsOf(costs('4999999'), false).installationCost).toBeUndefined();
    expect(structureErrorsOf(costs('5000000'), false).installationCost).toBe(
      'Installation Cost must be whole dollars from $0 to $4,999,999.',
    );
  });

  it('warns above $1,000,000, and not at it', () => {
    expect(structureWarningsOf(costs('1000000')).installationCost).toBeUndefined();
    expect(structureWarningsOf(costs('1000001')).installationCost).toBe(
      'Installation Cost is not in the range $0 through $1,000,000.',
    );
    // Past the ceiling the error speaks instead.
    expect(structureWarningsOf(costs('5000000')).installationCost).toBeUndefined();
  });

  it('sends a cost over $999,999 as the number typed', () => {
    expect(toStructureRequest(costs('2500000')).installationCost).toBe(2_500_000);
  });

  it("leaves Material Cost at $999,999, the column's", () => {
    expect(structureErrorsOf(costs('', '1000000'), false).materialCost).toBe(
      'Material Cost must be whole dollars from $0 to $999,999.',
    );
  });
});
