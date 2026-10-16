import { describe, expect, it } from 'vitest';

import { countOf, outstandingOf } from './outstanding';

import type { InspectionDetailResponse, InspectionItem } from './inspectionDetail';
import type { InspectionFormResponse } from '@/pages/NewInspection/inspectionForm';

const item = (overrides: Partial<InspectionItem>): InspectionItem => ({
  subsectionCode: 'APP',
  questionCode: 'APPQ',
  spanId: null,
  pierId: null,
  stringerNumber: null,
  answer: 'G',
  comment: null,
  ...overrides,
});

const inspection = (overrides: Partial<InspectionDetailResponse> = {}) =>
  ({
    status: { code: 'SUB', description: 'Submitted' },
    inspectionDate: '2024-05-01',
    inspectorName: 'Jo Smith',
    inspectorCompanyName: 'Bridges Ltd',
    hazards: { presentWaterDepth: 1, presentWaterWidth: 2, highWaterClearance: 3 },
    items: [],
    ...overrides,
  }) as InspectionDetailResponse;

const form: InspectionFormResponse = {
  structureId: '7',
  subsections: [
    {
      code: 'APP',
      description: 'Approaches',
      scope: 'STRUCTURE',
      questions: [
        { code: 'APPQ', description: 'Approach condition', perStringer: false, answers: [] },
      ],
    },
    {
      code: 'STR',
      description: 'Stringers',
      scope: 'SPAN',
      questions: [
        { code: 'SQ', description: 'Stringer condition', perStringer: false, answers: [] },
        { code: 'PEEL', description: 'Peeled', perStringer: true, answers: [] },
      ],
    },
    {
      code: 'PIER',
      description: 'Piers',
      scope: 'PIER',
      questions: [{ code: 'PQ', description: 'Pier condition', perStringer: false, answers: [] }],
    },
  ],
  spans: [{ id: '701', number: 1 }],
  piers: [{ id: '801', number: 1 }],
};

describe('outstandingOf', () => {
  it('lets the required fields and measurements wait while In Progress, as legacy', () => {
    const result = outstandingOf(
      inspection({
        status: { code: 'PRO', description: 'In Progress' },
        inspectionDate: null,
        inspectorName: null,
        hazards: { presentWaterDepth: null } as InspectionDetailResponse['hazards'],
        items: [item({ answer: 'P', comment: null })],
      }),
      form,
    );
    expect(result.inspection).toEqual([]);
    expect(result.hazards).toEqual([]);
    // The answer rules hold at any status.
    expect(result.condition).toEqual([
      { title: 'Approaches', items: ['Approach condition — comment'] },
    ]);
  });

  it("lists legacy's required fields and water measurements when blank, past In Progress", () => {
    const result = outstandingOf(
      inspection({
        inspectionDate: null,
        inspectorCompanyName: ' ',
        hazards: {
          presentWaterDepth: null,
          presentWaterWidth: 0,
          highWaterClearance: null,
        } as InspectionDetailResponse['hazards'],
      }),
      undefined,
    );
    expect(result.inspection).toEqual([
      { title: 'Inspection', items: ['Date of Inspection', 'Inspection Company Name'] },
    ]);
    // A measurement of 0 is a measurement.
    expect(result.hazards).toEqual([
      {
        title: 'Hydraulic Hazards',
        items: ['Estimated Present Water Level Depth', 'Estimated High Water Level Clearance'],
      },
    ]);
    expect(result.condition).toEqual([]);
  });

  it("lists only legacy's answer rules: Poor needs a comment, a comment needs an answer", () => {
    const result = outstandingOf(
      inspection({
        items: [
          item({ answer: 'P', comment: null }),
          item({ subsectionCode: 'STR', questionCode: 'PEEL', spanId: '701', stringerNumber: 2 }),
          item({
            subsectionCode: 'STR',
            questionCode: 'PEEL',
            spanId: '701',
            stringerNumber: 3,
            answer: null,
            comment: 'Rot',
          }),
          item({
            subsectionCode: 'PIER',
            questionCode: 'PQ',
            pierId: '801',
            answer: 'UNR',
            comment: 'Under water',
          }),
        ],
      }),
      form,
    );
    // Unanswered questions (Stringer condition, the pier's) are not required.
    expect(result.condition).toEqual([
      { title: 'Approaches', items: ['Approach condition — comment'] },
      { title: 'Stringers — Span 1 — Stringer 3', items: ['Peeled — answer'] },
    ]);
    expect(countOf(result.condition)).toBe(2);
    expect(outstandingOf(inspection(), undefined).inspection).toEqual([]);
  });
});
