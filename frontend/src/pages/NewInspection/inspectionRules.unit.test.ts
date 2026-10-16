import { describe, expect, it } from 'vitest';

import {
  EMPTY_HAZARD_VALUES,
  EMPTY_INSPECTION_VALUES,
  answerErrorsOf,
  hazardErrorsOf,
  hazardWarningsOf,
  inspectionErrorsOf,
  isCommentMessage,
  itemKey,
  toCreateRequest,
  toMetres,
  type Answer,
} from './inspectionRules';

import type { InspectionSubsection } from './inspectionForm';

const inspection = (overrides: Partial<typeof EMPTY_INSPECTION_VALUES> = {}) => ({
  ...EMPTY_INSPECTION_VALUES,
  typeCode: 'UNP',
  ...overrides,
});

const subsections: InspectionSubsection[] = [
  {
    code: 'APP',
    description: 'Approaches',
    scope: 'STRUCTURE',
    questions: [
      {
        code: 'APPQ',
        description: 'Approach condition',
        perStringer: false,
        answers: [{ code: 'P', description: 'Poor' }],
      },
      { code: 'PLR', description: 'Posted Load Rating', perStringer: false, answers: [] },
    ],
  },
];

const answer = (overrides: Partial<Answer> = {}): Answer => ({
  subsectionCode: 'APP',
  questionCode: 'APPQ',
  spanId: null,
  pierId: null,
  stringerNumber: null,
  answer: '',
  comment: '',
  ...overrides,
});

describe('itemKey', () => {
  it('names the question and where it was asked, as the server keys it', () => {
    expect(itemKey(answer())).toBe('items.APP.APPQ');
    expect(itemKey(answer({ spanId: 12, stringerNumber: 3 }))).toBe(
      'items.APP.APPQ.span-12.stringer-3',
    );
    expect(itemKey(answer({ pierId: 4 }))).toBe('items.APP.APPQ.pier-4');
  });
});

describe('toMetres', () => {
  it('takes metres to one decimal place, blank as none', () => {
    expect(toMetres(' ')).toBeNull();
    expect(toMetres('12.5')).toBe(12.5);
    expect(toMetres('1.25')).toBeNaN();
    expect(toMetres('100000')).toBeNaN();
    expect(toMetres('-1')).toBeNaN();
  });
});

describe('inspectionErrorsOf', () => {
  it('needs a type and a status, and lets the rest wait while In Progress', () => {
    const errors = inspectionErrorsOf(inspection({ typeCode: '', statusCode: '' }));
    expect(errors.typeCode).toBe('Inspection Type is required.');
    expect(errors.statusCode).toBe('Inspection Report Status is required.');
    expect(errors.inspectionDate).toBeUndefined();
  });

  it('needs the date, inspector and company once Submitted', () => {
    const errors = inspectionErrorsOf(inspection({ statusCode: 'SUB' }));
    expect(errors.inspectionDate).toBe('Date of Inspection is required.');
    expect(errors.inspectorName).toBe('Inspector Name is required.');
    expect(errors.inspectorCompanyName).toBe('Inspection Company Name is required.');
  });

  it('refuses a malformed or future date', () => {
    expect(inspectionErrorsOf(inspection({ inspectionDate: '2026/13/45' })).inspectionDate).toBe(
      'Date of Inspection must be a date, as yyyy/mm/dd.',
    );
    expect(inspectionErrorsOf(inspection({ inspectionDate: '2999/01/01' })).inspectionDate).toBe(
      'Date of Inspection must not be after today.',
    );
    expect(
      inspectionErrorsOf(inspection({ inspectionDate: '2020/01/01' })).inspectionDate,
    ).toBeUndefined();
  });

  it('holds the names to 255, the cost to whole dollars and the comment to 4000', () => {
    const errors = inspectionErrorsOf(
      inspection({
        inspectorName: 'x'.repeat(256),
        inspectorCompanyName: 'x'.repeat(256),
        cost: '12.50',
        inspectionComment: 'x'.repeat(4001),
      }),
    );
    expect(errors.inspectorName).toBe('Inspector Name can be at most 255 characters.');
    expect(errors.inspectorCompanyName).toBe(
      'Inspection Company Name can be at most 255 characters.',
    );
    expect(errors.cost).toBe('Cost of Inspection must be whole dollars from $0 to $999,999.');
    expect(errors.inspectionComment).toBeTruthy();
    expect(inspectionErrorsOf(inspection({ cost: '1000000' })).cost).toBeTruthy();
    expect(inspectionErrorsOf(inspection({ cost: '1200' })).cost).toBeUndefined();
  });
});

describe('hazards', () => {
  it('refuses a measurement that is not metres, and a long comment', () => {
    const errors = hazardErrorsOf({
      ...EMPTY_HAZARD_VALUES,
      presentWaterDepth: 'deep',
      hazardComment: 'x'.repeat(2001),
    });
    expect(errors.presentWaterDepth).toBe(
      'Estimated Present Water Level Depth must be metres from 0 to 99999.9, with 1 decimal place at most.',
    );
    expect(errors.hazardComment).toBeTruthy();
    expect(errors.presentWaterWidth).toBeUndefined();
  });

  it("warns past legacy's ranges, and says nothing of a blank or malformed one", () => {
    const warnings = hazardWarningsOf({
      ...EMPTY_HAZARD_VALUES,
      presentWaterDepth: '21',
      presentWaterWidth: '351',
      highWaterClearance: '100',
    });
    expect(warnings.presentWaterDepth).toBe(
      'Estimated Present Water Level Depth is not in the range 0 through 20.',
    );
    expect(warnings.presentWaterWidth).toBe(
      'Estimated Present Water Level Width is not in the range 0 through 350.',
    );
    expect(warnings.highWaterClearance).toBeUndefined();
    expect(hazardWarningsOf({ ...EMPTY_HAZARD_VALUES, presentWaterDepth: 'x' })).toEqual({});
  });
});

describe('answerErrorsOf', () => {
  const errorOf = (entry: Answer, groups = subsections) =>
    answerErrorsOf({ [itemKey(entry)]: entry }, groups)[itemKey(entry)];

  it("keeps legacy's rules: a comment needs an answer, Poor needs a comment", () => {
    expect(errorOf(answer({ comment: 'Cracked' }))).toBe(
      '(Approaches) Approach condition requires an answer.',
    );
    expect(errorOf(answer({ answer: 'P' }))).toBe(
      '(Approaches) Approach condition requires a comment.',
    );
    expect(errorOf(answer({ answer: 'P', comment: 'Cracked' }))).toBeUndefined();
  });

  it('holds a typed answer to 255 and a comment to 2000', () => {
    expect(errorOf(answer({ questionCode: 'PLR', answer: 'x'.repeat(256) }))).toBe(
      '(Approaches) Posted Load Rating can be at most 255 characters.',
    );
    expect(errorOf(answer({ answer: 'G', comment: 'x'.repeat(2001) }))).toBe(
      'The comment can be at most 2000 characters.',
    );
  });

  it('names a question by its codes when the form does not know it', () => {
    expect(errorOf(answer({ subsectionCode: 'XX', questionCode: 'YY', comment: 'c' }), [])).toBe(
      '(XX) YY requires an answer.',
    );
  });
});

describe('isCommentMessage', () => {
  it('tells a message about the comment from one about the answer', () => {
    expect(isCommentMessage('(Deck) Deck condition requires a comment.')).toBe(true);
    expect(isCommentMessage('The comment can be at most 2000 characters.')).toBe(true);
    expect(isCommentMessage('(Deck) Deck condition requires an answer.')).toBe(false);
  });
});

describe('toCreateRequest', () => {
  it('sends the values as the server takes them, and the answered questions only', () => {
    const request = toCreateRequest(
      '7',
      inspection({ inspectionDate: '2024/05/01', cost: '1200', statusCode: 'SUB' }),
      { ...EMPTY_HAZARD_VALUES, presentWaterDepth: '1.5', scour: true },
      {
        a: answer({ answer: 'G' }),
        b: answer({ questionCode: 'PLR', answer: ' ' }),
      },
    );
    expect(request).toMatchObject({
      structureId: 7,
      typeCode: 'UNP',
      statusCode: 'SUB',
      inspectionDate: '2024-05-01',
      cost: 1200,
      presentWaterDepth: 1.5,
      presentWaterWidth: null,
      scour: true,
    });
    expect(request.items).toHaveLength(1);
    expect(toCreateRequest('7', inspection(), EMPTY_HAZARD_VALUES, {}).inspectionDate).toBeNull();
  });
});
