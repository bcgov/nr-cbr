import { NEEDS_COMMENT, NUMERIC_QUESTIONS, isNumber, type Answers } from './inspectionRules';

import type { InspectionQuestion } from './inspectionForm';

/** One stringer's answers as typed in its form, by question code. */
export type StringerValues = Record<string, { answer: string; comment: string }>;

/**
 * Legacy's required stringer questions (`bridge_stringer_tab.jsp`, `culvert_stringer_tab.jsp`):
 * a bridge's stringer needs Peeled, the three rot depths and the species; a wood box culvert's, its
 * rot depth, diameter and species.
 */
const REQUIRED: Record<string, string[]> = {
  STR: ['PEEL', 'RDB', 'RDS', 'RDT', 'WS'],
  WDC: ['ASR', 'DMT', 'WSCS'],
};
const DIAMETER = 'DMT';
const WIDTH_BY_DEPTH = 'WXD';

/** Legacy's range warnings on answers (`Inspection.validate`): shown, never stopping a save. */
const RANGES: Record<string, [number, number]> = {
  PLR: [0, 200],
  GDD: [0, 2],
  DMT: [200, 1200],
  RDB: [0, 150],
  RDS: [0, 150],
  RDT: [0, 150],
  ASR: [0, 150],
};

/** "Posted Load Rating is not in the range 0 through 200.", when a number typed is outside it. */
export const answerWarningOf = (
  question: InspectionQuestion,
  answer: string,
): string | undefined => {
  const range = RANGES[question.code];
  const typed = answer.trim();
  if (!range || !isNumber(typed)) return undefined;
  const [low, high] = range;
  const value = Number(typed);
  return value < low || value > high
    ? `${question.description} is not in the range ${low} through ${high}.`
    : undefined;
};

/** Whether the stringer is asked both Diameter and Width X Depth — one of them, not both, required. */
export const asksDiameterOrWidth = (subsectionCode: string, questions: InspectionQuestion[]) =>
  subsectionCode === 'STR' &&
  questions.some((question) => question.code === DIAMETER) &&
  questions.some((question) => question.code === WIDTH_BY_DEPTH);

/** The stringer questions marked required: legacy's, and Diameter and Width X Depth, one of which is. */
export const requiredStringerCodes = (
  subsectionCode: string,
  questions: InspectionQuestion[],
): Set<string> =>
  new Set([
    ...(REQUIRED[subsectionCode] ?? []),
    ...(asksDiameterOrWidth(subsectionCode, questions) ? [DIAMETER, WIDTH_BY_DEPTH] : []),
  ]);

const blank = (value: { answer: string; comment: string } | undefined) => !value?.answer.trim();

/**
 * Why one stringer cannot be saved, by question code — legacy's stringer rules, then the answer
 * rules every question has: a comment needs an answer, Poor or Unrated needs a comment.
 */
export const stringerErrorsOf = (
  subsectionCode: string,
  questions: InspectionQuestion[],
  values: StringerValues,
): Record<string, string> => {
  const errors: Record<string, string> = {};
  for (const question of questions) {
    if ((REQUIRED[subsectionCode] ?? []).includes(question.code) && blank(values[question.code])) {
      errors[question.code] = `${question.description} is required.`;
    }
  }
  if (asksDiameterOrWidth(subsectionCode, questions)) {
    const diameter = !blank(values[DIAMETER]);
    const widthByDepth = !blank(values[WIDTH_BY_DEPTH]);
    if (!diameter && !widthByDepth) {
      errors[DIAMETER] = 'Diameter OR Width X Depth is required.';
    } else if (diameter && widthByDepth) {
      errors[DIAMETER] = 'Diameter OR Width X Depth must be answered, not both.';
    }
  }
  for (const question of questions) {
    const value = values[question.code];
    const answer = value?.answer.trim() ?? '';
    const comment = value?.comment.trim() ?? '';
    if (errors[question.code]) continue;
    if (!answer && comment) errors[question.code] = `${question.description} requires an answer.`;
    else if (NEEDS_COMMENT.has(answer) && !comment) {
      errors[question.code] = `${question.description} requires a comment.`;
    } else if (answer && NUMERIC_QUESTIONS.has(question.code) && !isNumber(answer)) {
      errors[question.code] = `${question.description} must be a number.`;
    }
  }
  return errors;
};

/** Where a set of stringers is: a span, or none for a wood box culvert. */
export type StringerWhere = { spanId: number | null; pierId: number | null };

/** The stringers recorded for the group and span: those with an answer or a comment. */
export const stringersOf = (
  answers: Answers,
  subsectionCode: string,
  where: StringerWhere,
): number[] =>
  [
    ...new Set(
      Object.values(answers)
        .filter(
          (each) =>
            each.subsectionCode === subsectionCode &&
            each.spanId === where.spanId &&
            each.pierId === where.pierId &&
            each.stringerNumber !== null &&
            (each.answer.trim() !== '' || each.comment.trim() !== ''),
        )
        .map((each) => each.stringerNumber as number),
    ),
  ].sort((a, b) => a - b);
