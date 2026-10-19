import { describe, expect, it } from 'vitest';

import {
  answerWarningOf,
  asksDiameterOrWidth,
  requiredStringerCodes,
  stringerErrorsOf,
  stringersOf,
} from './stringerRules';

import type { InspectionQuestion } from './inspectionForm';
import type { Answers } from './inspectionRules';

const question = (code: string, description: string): InspectionQuestion => ({
  code,
  description,
  perStringer: true,
  answers: code === 'PEEL' ? [{ code: 'Y', description: 'Yes' }] : [],
});

const bridge = [
  question('PEEL', 'Peeled'),
  question('DMT', 'Diameter'),
  question('WXD', 'Width X Depth'),
  question('RDB', 'Rot Depth (Bottom)'),
  question('RDS', 'Rot Depth (Side)'),
  question('RDT', 'Rot Depth (Top)'),
  question('WS', 'Wood Species'),
];
const culvert = [
  question('ASR', 'Average Rot Depth'),
  question('DMT', 'Diameter'),
  question('WSCS', 'Stringer Species'),
];
const v = (answer: string, comment = '') => ({ answer, comment });

describe('requiredStringerCodes', () => {
  it("marks a bridge stringer's required questions, and both Diameter and Width X Depth", () => {
    expect(asksDiameterOrWidth('STR', bridge)).toBe(true);
    expect([...requiredStringerCodes('STR', bridge)].sort()).toEqual(
      ['DMT', 'PEEL', 'RDB', 'RDS', 'RDT', 'WS', 'WXD'].sort(),
    );
  });

  it("marks a wood box culvert stringer's rot depth, diameter and species", () => {
    expect(asksDiameterOrWidth('WDC', culvert)).toBe(false);
    expect([...requiredStringerCodes('WDC', culvert)].sort()).toEqual(['ASR', 'DMT', 'WSCS']);
  });
});

describe('stringerErrorsOf', () => {
  it("needs a bridge stringer's Peeled, rot depths and species, and its diameter or width x depth", () => {
    expect(stringerErrorsOf('STR', bridge, {})).toEqual({
      PEEL: 'Peeled is required.',
      DMT: 'Diameter OR Width X Depth is required.',
      RDB: 'Rot Depth (Bottom) is required.',
      RDS: 'Rot Depth (Side) is required.',
      RDT: 'Rot Depth (Top) is required.',
      WS: 'Wood Species is required.',
    });
  });

  it('takes the diameter or the width x depth, not both', () => {
    const complete = {
      PEEL: v('Y'),
      RDB: v('1'),
      RDS: v('1'),
      RDT: v('1'),
      WS: v('Fir'),
    };
    expect(stringerErrorsOf('STR', bridge, { ...complete, DMT: v('300') })).toEqual({});
    expect(stringerErrorsOf('STR', bridge, { ...complete, WXD: v('200x300') })).toEqual({});
    expect(
      stringerErrorsOf('STR', bridge, { ...complete, DMT: v('300'), WXD: v('200x300') }),
    ).toEqual({ DMT: 'Diameter OR Width X Depth must be answered, not both.' });
  });

  it("needs all three of a wood box culvert's, and keeps the answer rules", () => {
    expect(stringerErrorsOf('WDC', culvert, { DMT: v('', 'note') })).toEqual({
      ASR: 'Average Rot Depth is required.',
      DMT: 'Diameter is required.',
      WSCS: 'Stringer Species is required.',
    });
    expect(
      stringerErrorsOf('WDC', culvert, { ASR: v('2'), DMT: v('P'), WSCS: v('', 'cedar?') }),
    ).toEqual({ DMT: 'Diameter requires a comment.', WSCS: 'Stringer Species is required.' });
  });
});

describe('numbers', () => {
  it('refuses a non-number in a question answered with one, and leaves Width X Depth as text', () => {
    const complete = {
      PEEL: v('Y'),
      RDB: v('b'),
      RDS: v('1.5'),
      RDT: v('-2'),
      WS: v('Fir'),
      WXD: v('300x400'),
    };
    expect(stringerErrorsOf('STR', bridge, complete)).toEqual({
      RDB: 'Rot Depth (Bottom) must be a number.',
    });
  });
});

describe('answerWarningOf', () => {
  it("warns outside legacy's ranges, and says nothing of other questions or text", () => {
    expect(answerWarningOf(question('DMT', 'Diameter'), '150')).toBe(
      'Diameter is not in the range 200 through 1200.',
    );
    expect(answerWarningOf(question('DMT', 'Diameter'), '450')).toBeUndefined();
    expect(answerWarningOf(question('PLR', 'Posted Load Rating'), '250')).toBe(
      'Posted Load Rating is not in the range 0 through 200.',
    );
    expect(answerWarningOf(question('DMT', 'Diameter'), 'big')).toBeUndefined();
    expect(answerWarningOf(question('WS', 'Wood Species'), '9999')).toBeUndefined();
  });
});

describe('stringersOf', () => {
  it('lists the stringers with something recorded, by span', () => {
    const answers: Answers = {
      a: {
        subsectionCode: 'STR',
        questionCode: 'PEEL',
        spanId: 1,
        pierId: null,
        stringerNumber: 2,
        answer: 'Y',
        comment: '',
      },
      b: {
        subsectionCode: 'STR',
        questionCode: 'PEEL',
        spanId: 1,
        pierId: null,
        stringerNumber: 1,
        answer: '',
        comment: 'note',
      },
      c: {
        subsectionCode: 'STR',
        questionCode: 'PEEL',
        spanId: 1,
        pierId: null,
        stringerNumber: 3,
        answer: '',
        comment: '',
      },
      d: {
        subsectionCode: 'STR',
        questionCode: 'PEEL',
        spanId: 2,
        pierId: null,
        stringerNumber: 4,
        answer: 'Y',
        comment: '',
      },
    };
    expect(stringersOf(answers, 'STR', { spanId: 1, pierId: null })).toEqual([1, 2]);
  });
});
