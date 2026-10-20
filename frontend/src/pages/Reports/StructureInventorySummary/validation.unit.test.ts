import { describe, expect, it } from 'vitest';

import { EMPTY_CRITERIA, type StructureInventorySummaryCriteria } from './types';
import { criteriaErrors } from './validation';

const errorsFor = (overrides: Partial<StructureInventorySummaryCriteria>) =>
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
        loadRestrictionYearEnd: '2030',
        replacementYearStart: '2040',
        openingWidthMin: '600',
        openingWidthMax: '999999',
      }),
    ).toEqual({});
  });

  it('flags each bad box on its own', () => {
    expect(
      errorsFor({
        kiloEnd: 'ten',
        yearBuiltStart: '90',
        replacementYearEnd: '20300',
        openingWidthMin: '1.5',
        openingWidthMax: '1000000',
      }),
    ).toEqual({
      kiloEnd: 'Kilometres must be a number, e.g. 12.5',
      yearBuiltStart: 'Enter a year as yyyy, e.g. 2030',
      replacementYearEnd: 'Enter a year as yyyy, e.g. 2030',
      openingWidthMin: 'Opening width must be whole millimetres, e.g. 1200',
      openingWidthMax: 'Opening width must be whole millimetres, e.g. 1200',
    });
  });
});
