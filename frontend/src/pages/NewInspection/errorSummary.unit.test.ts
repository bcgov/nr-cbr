import { describe, expect, it } from 'vitest';

import { errorGroups, tabOf } from './errorSummary';

import type { InspectionFormResponse } from './inspectionForm';

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
      questions: [{ code: 'PEEL', description: 'Peeled', perStringer: true, answers: [] }],
    },
    {
      code: 'PIER',
      description: 'Piers',
      scope: 'PIER',
      questions: [{ code: 'PQ', description: 'Pier condition', perStringer: false, answers: [] }],
    },
  ],
  spans: [
    { id: '701', number: 1 },
    { id: '702', number: 2 },
  ],
  piers: [{ id: '801', number: 1 }],
};

describe('tabOf', () => {
  it("puts a field's message on the tab it is shown on", () => {
    expect(tabOf('items.APP.APPQ')).toBe('condition');
    expect(tabOf('presentWaterDepth')).toBe('hazards');
    expect(tabOf('inspectorName')).toBe('inspection');
    expect(tabOf('structureId')).toBe('inspection');
  });
});

describe('errorGroups', () => {
  it('groups the Inspection tab by card, in the order the cards are shown', () => {
    const groups = errorGroups(
      {
        inspectionComment: 'Inspection Comments can be at most 4000 characters.',
        inspectorName: 'Inspector Name is required.',
        structureId: 'Structure # is required.',
        presentWaterDepth: 'elsewhere',
        typeCode: undefined,
      },
      'inspection',
      form,
    );
    expect(groups).toEqual([
      { title: 'Structure and site', items: ['Structure # is required.'] },
      { title: 'Inspection', items: ['Inspector Name is required.'] },
      {
        title: 'Inspection Comments',
        items: ['Inspection Comments can be at most 4000 characters.'],
      },
    ]);
  });

  it('heads the Hydraulic Hazards tab with its card', () => {
    expect(errorGroups({ hazardComment: 'Too long.' }, 'hazards', form)).toEqual([
      { title: 'Hydraulic Hazards', items: ['Too long.'] },
    ]);
  });

  it('names each answer by its group, span, pier and stringer, in the order shown', () => {
    const groups = errorGroups(
      {
        'items.PIER.PQ.pier-801': '(Piers) Pier condition requires a comment.',
        'items.STR.PEEL.span-702.stringer-2': 'This question is answered twice.',
        'items.STR.PEEL.span-701.stringer-1': '(Stringers) Peeled requires an answer.',
        'items.APP.APPQ': '(Approaches) Approach condition requires a comment.',
      },
      'condition',
      form,
    );
    expect(groups).toEqual([
      { title: 'Approaches', items: ['Approach condition requires a comment.'] },
      { title: 'Stringers — Span 1 — Stringer 1', items: ['Peeled requires an answer.'] },
      {
        title: 'Stringers — Span 2 — Stringer 2',
        items: ['Peeled: This question is answered twice.'],
      },
      { title: 'Piers — Pier 1', items: ['Pier condition requires a comment.'] },
    ]);
  });

  it('falls back to the codes when the questions have not loaded', () => {
    expect(
      errorGroups({ 'items.DECK.DQ.span-9': 'Choose a span.' }, 'condition', undefined),
    ).toEqual([{ title: 'DECK — Span 9', items: ['DQ: Choose a span.'] }]);
  });
});
