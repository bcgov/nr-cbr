import { describe, expect, it } from 'vitest';

import {
  canChangeStatus,
  cardErrorsOf,
  requiredOf,
  toUpdateRequest,
  valuesOf,
} from './inspectionEditRules';

import type { InspectionDetailResponse } from './inspectionDetail';

const inspection = (overrides: Partial<InspectionDetailResponse> = {}) =>
  ({
    type: { code: 'ROUT', description: 'Routine' },
    status: { code: 'PRO', description: 'In Progress' },
    inspectionDate: null,
    inspectorName: 'Jo',
    inspectorCompanyName: null,
    cost: null,
    professionalInspectionRequired: false,
    inspectionComment: null,
    ...overrides,
  }) as InspectionDetailResponse;

describe('the Inspection card', () => {
  it('reads the stored inspection as the form types it, and back', () => {
    const values = valuesOf(inspection({ inspectionDate: '2024-05-01', cost: 1200 }));
    expect(values).toMatchObject({ inspectionDate: '2024/05/01', cost: '1200', statusCode: 'PRO' });
    expect(toUpdateRequest(values)).toMatchObject({ inspectionDate: '2024-05-01', cost: 1200 });
    expect(toUpdateRequest({ ...values, inspectionDate: ' ', cost: '' })).toMatchObject({
      inspectionDate: null,
      cost: null,
    });
  });

  it('requires what was given, and everything past In Progress', () => {
    const stored = inspection();
    const values = { ...valuesOf(stored), inspectorName: '' };
    expect(cardErrorsOf(values, stored).inspectorName).toBe('Inspector Name is required.');
    expect(cardErrorsOf(values, stored).inspectionDate).toBeUndefined();
    expect(requiredOf(values, stored)).toEqual({
      inspectionDate: false,
      inspectorName: true,
      inspectorCompanyName: false,
    });
    expect(requiredOf({ ...values, statusCode: 'SUB' }, stored).inspectionDate).toBe(true);
  });

  it('moves the status between In Progress and Submitted; out of Rejected, a P.Eng only', () => {
    expect(canChangeStatus('PRO', false)).toBe(true);
    expect(canChangeStatus('SUB', false)).toBe(true);
    expect(canChangeStatus('REJ', false)).toBe(false);
    expect(canChangeStatus('REJ', true)).toBe(true);
    expect(canChangeStatus('OFL', true)).toBe(false);
  });
});
