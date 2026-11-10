import { describe, expect, it } from 'vitest';

import {
  canChangeStatus,
  cardErrorsOf,
  isReopening,
  offeredStatuses,
  reopenErrorsOf,
  requiredOf,
  statusHelpOf,
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

  it('reopens Reviewed or Accepted, for anyone who edits, back to In Progress or Submitted', () => {
    expect(canChangeStatus('RVD', false)).toBe(true);
    expect(canChangeStatus('ACC', false)).toBe(true);
    expect(isReopening('RVD')).toBe(true);
    expect(isReopening('ACC')).toBe(true);
    expect(isReopening('REJ')).toBe(false);
    expect(isReopening(null)).toBe(false);

    const values = valuesOf(inspection({ status: { code: 'RVD', description: 'Reviewed' } }));
    expect(reopenErrorsOf(values).statusCode).toBe(
      'Choose In Progress or Submitted to reopen this inspection.',
    );
    expect(reopenErrorsOf({ ...values, statusCode: 'REJ' }).statusCode).toBeDefined();
    expect(reopenErrorsOf({ ...values, statusCode: 'SUB' }).statusCode).toBeUndefined();
    expect(reopenErrorsOf({ ...values, statusCode: 'PRO' }).statusCode).toBeUndefined();
  });

  it('offers the card statuses, the current one, and a reviewer Reviewed and Rejected', () => {
    const all = ['PRO', 'REJ', 'RVD', 'SUB', 'ACC'].map((code) => ({ code }));
    const codes = (current: string | null, reviewer: boolean) =>
      offeredStatuses(all, current, reviewer).map(({ code }) => code);

    expect(codes('PRO', false)).toEqual(['PRO', 'SUB']);
    expect(codes('PRO', true)).toEqual(['PRO', 'REJ', 'RVD', 'SUB']);
    expect(codes('REJ', true)).toEqual(['PRO', 'REJ', 'RVD', 'SUB']);
    // Reopening: back to the card's two, the status it has shown.
    expect(codes('RVD', true)).toEqual(['PRO', 'RVD', 'SUB']);
    expect(codes('ACC', true)).toEqual(['PRO', 'SUB', 'ACC']);
  });

  it('says what saving the picked status does beyond the card', () => {
    expect(statusHelpOf('REJ', 'SUB', '2026-11-10')).toBe(
      "Saving reschedules the structure's next routine inspection to 2027/11/10, then yearly.",
    );
    expect(statusHelpOf('REJ', 'REJ', '2026-11-10')).toBeUndefined();
    expect(statusHelpOf('SUB', 'RVD', '2026-11-10')).toMatch(/^Saving clears the review/);
    expect(statusHelpOf('RVD', 'RVD', '2026-11-10')).toBeUndefined();
    expect(statusHelpOf('SUB', 'PRO', '2026-11-10')).toBeUndefined();
  });
});
