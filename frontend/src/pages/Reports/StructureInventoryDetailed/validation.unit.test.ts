import { describe, expect, it } from 'vitest';

import { EMPTY_CRITERIA, type StructureInventoryDetailedCriteria } from './types';
import { criteriaErrors } from './validation';

const errorsFor = (overrides: Partial<StructureInventoryDetailedCriteria>) =>
  criteriaErrors({ ...EMPTY_CRITERIA, ...overrides });

describe('criteriaErrors', () => {
  it('accepts no criteria at all, as legacy runs the report with none', () => {
    expect(criteriaErrors(EMPTY_CRITERIA)).toEqual({});
  });

  it('accepts well-formed bounds', () => {
    expect(
      errorsFor({
        kiloStart: '1.5',
        kiloEnd: '20',
        yearBuiltStart: '1990',
        replacementYearEnd: '2040',
      }),
    ).toEqual({});
  });

  it('flags each bad box on its own', () => {
    expect(
      errorsFor({ kiloEnd: 'ten', yearBuiltStart: '90', loadRestrictionYearEnd: '20300' }),
    ).toEqual({
      kiloEnd: 'Kilometres must be a number, e.g. 12.5',
      yearBuiltStart: 'Enter a year as yyyy, e.g. 2030',
      loadRestrictionYearEnd: 'Enter a year as yyyy, e.g. 2030',
    });
  });
});
