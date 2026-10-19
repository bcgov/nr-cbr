import type {
  InspectionCreateRequest,
  InspectionItemRequest,
  InspectionSubsection,
} from './inspectionForm';

import { toIso } from '@/pages/StructureDetail/dateBox';
import { byteLength, overLimitError } from '@/utils/textLimits';

/** `INSPECTOR_NAME`, `INSPECTOR_COMPANY_NAME` and a typed answer: `VARCHAR2(255)`. */
export const NAME_MAX = 255;
/** The inspector's comments, as legacy's form allows. */
export const COMMENT_MAX = 4000;
/** An answer's comment and the hazard comments: `VARCHAR2(2000)`. */
export const TEXT_MAX = 2000;
const COST_MAX = 999_999;
const METRES_MAX = 99_999.9;
/** Legacy's answers that need a comment: Poor and Unrated. */
export const NEEDS_COMMENT = new Set(['P', 'UNR']);

/**
 * The questions answered with a number: Posted Load Rating, Gravel Deck Depth, Diameter, the rot
 * depths and Average Rot Depth — those legacy range-checks. Legacy only warned when one was not a
 * number, and saved it; here it is refused.
 */
export const NUMERIC_QUESTIONS = new Set(['PLR', 'GDD', 'DMT', 'RDB', 'RDS', 'RDT', 'ASR']);

/** Whether a typed answer reads as a number — `12`, `0.5`, `-3`. */
export const isNumber = (typed: string): boolean => /^-?\d+(\.\d+)?$/.test(typed.trim());

/** Whether an answer's message is about its comment — shown under the comment box, not the answer. */
export const isCommentMessage = (message: string): boolean =>
  message.endsWith(' requires a comment.') || message.startsWith('The comment ');

export const IN_PROGRESS = 'PRO';
/**
 * The statuses an inspection may be added at. Reviewed and Rejected are a P.Eng's verdict on a
 * submitted inspection, set on its own page.
 */
export const ADDABLE_STATUSES = new Set([IN_PROGRESS, 'SUB']);

/** The Inspection tab's values as typed: the date `yyyy/mm/dd`, the cost text until saved. */
export type InspectionValues = {
  typeCode: string;
  statusCode: string;
  inspectionDate: string;
  inspectorName: string;
  inspectorCompanyName: string;
  cost: string;
  professionalInspectionRequired: boolean;
  inspectionComment: string;
};

export const EMPTY_INSPECTION_VALUES: InspectionValues = {
  typeCode: '',
  statusCode: IN_PROGRESS,
  inspectionDate: '',
  inspectorName: '',
  inspectorCompanyName: '',
  cost: '',
  professionalInspectionRequired: false,
  inspectionComment: '',
};

/** The Hydraulic Hazards tab's values as typed. */
export type HazardValues = {
  presentWaterDepth: string;
  presentWaterWidth: string;
  highWaterClearance: string;
  highWater: boolean;
  debris: boolean;
  scour: boolean;
  ice: boolean;
  aggradation: boolean;
  hazardComment: string;
};

export const EMPTY_HAZARD_VALUES: HazardValues = {
  presentWaterDepth: '',
  presentWaterWidth: '',
  highWaterClearance: '',
  highWater: false,
  debris: false,
  scour: false,
  ice: false,
  aggradation: false,
  hazardComment: '',
};

/** One answer as typed, and the question, span, pier and stringer it is for. */
export type Answer = Omit<InspectionItemRequest, 'answer' | 'comment'> & {
  answer: string;
  comment: string;
};

/** Every answer on the Condition tab, by {@link itemKey}. */
export type Answers = Record<string, Answer>;

/**
 * The key an answer is kept and refused under — the server's, so its message lands beside it:
 * `items.DECK.Q1.span-12`, `items.STR.PEEL.span-12.stringer-3`.
 */
export const itemKey = (
  where: Pick<Answer, 'subsectionCode' | 'questionCode' | 'spanId' | 'pierId' | 'stringerNumber'>,
) =>
  [
    'items',
    where.subsectionCode,
    where.questionCode,
    where.spanId === null ? null : `span-${where.spanId}`,
    where.pierId === null ? null : `pier-${where.pierId}`,
    where.stringerNumber === null ? null : `stringer-${where.stringerNumber}`,
  ]
    .filter(Boolean)
    .join('.');

/** A typed whole number, null when blank, or NaN when it is not whole dollars in range. */
const toCost = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= COST_MAX ? Number(trimmed) : Number.NaN;
};

/** Typed metres to one decimal, null when blank, or NaN when not. */
export const toMetres = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+(\.\d)?$/.test(trimmed) && Number(trimmed) <= METRES_MAX
    ? Number(trimmed)
    : Number.NaN;
};

type Messages = Record<string, string | undefined>;

/** Past In Progress, the date, inspector and company are required — legacy's rule. */
const requiredErrorsOf = (values: InspectionValues): Messages => {
  const errors: Messages = {};
  if (!values.typeCode) errors.typeCode = 'Inspection Type is required.';
  if (!values.statusCode) errors.statusCode = 'Inspection Report Status is required.';
  if (!values.statusCode || values.statusCode === IN_PROGRESS) return errors;
  if (!values.inspectionDate.trim()) errors.inspectionDate = 'Date of Inspection is required.';
  if (!values.inspectorName.trim()) errors.inspectorName = 'Inspector Name is required.';
  if (!values.inspectorCompanyName.trim()) {
    errors.inspectorCompanyName = 'Inspection Company Name is required.';
  }
  return errors;
};

/** A date as typed: a real date, and not after today. */
const dateErrorOf = (typed: string): string | undefined => {
  if (!typed.trim()) return undefined;
  const iso = toIso(typed);
  if (iso === null) return 'Date of Inspection must be a date, as yyyy/mm/dd.';
  return new Date(`${iso}T00:00`) > new Date()
    ? 'Date of Inspection must not be after today.'
    : undefined;
};

/** "Inspector Name can be at most 255 characters.", or nothing when it fits. */
const nameErrorOf = (typed: string, label: string): string | undefined =>
  byteLength(typed.trim()) > NAME_MAX
    ? `${label} can be at most ${NAME_MAX} characters.`
    : undefined;

/**
 * The Inspection tab's rules — legacy's: In Progress, the date, inspector and company may wait;
 * Submitted, they are required.
 */
export const inspectionErrorsOf = (values: InspectionValues): Messages => {
  const errors = requiredErrorsOf(values);
  const date = dateErrorOf(values.inspectionDate);
  if (date) errors.inspectionDate = date;
  const name = nameErrorOf(values.inspectorName, 'Inspector Name');
  if (name) errors.inspectorName = name;
  const company = nameErrorOf(values.inspectorCompanyName, 'Inspection Company Name');
  if (company) errors.inspectorCompanyName = company;
  if (Number.isNaN(toCost(values.cost))) {
    errors.cost = 'Cost of Inspection must be whole dollars from $0 to $999,999.';
  }
  errors.inspectionComment = overLimitError(values.inspectionComment, COMMENT_MAX) || undefined;
  return errors;
};

/** What each measurement is, and legacy's range for its warning. */
const MEASUREMENTS: [keyof HazardValues, string, number][] = [
  ['presentWaterDepth', 'Estimated Present Water Level Depth', 20],
  ['presentWaterWidth', 'Estimated Present Water Level Width', 350],
  ['highWaterClearance', 'Estimated High Water Level Clearance', 100],
];

/** The Hydraulic Hazards tab's rules. */
export const hazardErrorsOf = (values: HazardValues): Messages => {
  const errors: Messages = {};
  for (const [field, label] of MEASUREMENTS) {
    if (Number.isNaN(toMetres(values[field] as string))) {
      errors[field] = `${label} must be metres from 0 to 99999.9, with 1 decimal place at most.`;
    }
  }
  errors.hazardComment = overLimitError(values.hazardComment, TEXT_MAX) || undefined;
  return errors;
};

/** Legacy's range warnings (Inspection.validate), shown as typed; they never stop the save. */
export const hazardWarningsOf = (values: HazardValues): Messages => {
  const warnings: Messages = {};
  for (const [field, label, high] of MEASUREMENTS) {
    const value = toMetres(values[field] as string);
    if (value !== null && !Number.isNaN(value) && value > high) {
      warnings[field] = `${label} is not in the range 0 through ${high}.`;
    }
  }
  return warnings;
};

/**
 * The answers' rules — legacy's: a comment needs an answer, Poor or Unrated needs a comment, and a
 * comment of at most 2000.
 */
export const answerErrorsOf = (answers: Answers, subsections: InspectionSubsection[]): Messages => {
  const errors: Messages = {};
  for (const [key, entry] of Object.entries(answers)) {
    const group = subsections.find((each) => each.code === entry.subsectionCode);
    const question = group?.questions.find((each) => each.code === entry.questionCode);
    const label = `(${group?.description ?? entry.subsectionCode}) ${
      question?.description ?? entry.questionCode
    }`;
    errors[key] = answerProblemOf(entry, label, question?.answers.length === 0);
  }
  return errors;
};

/** What is wrong with one answer, if anything — legacy's rules, and a number where one is asked. */
const answerProblemOf = (entry: Answer, label: string, typed: boolean): string | undefined => {
  const answer = entry.answer.trim();
  const comment = entry.comment.trim();
  if (!answer && comment) return `${label} requires an answer.`;
  if (NEEDS_COMMENT.has(answer) && !comment) return `${label} requires a comment.`;
  if (answer && NUMERIC_QUESTIONS.has(entry.questionCode) && !isNumber(answer)) {
    return `${label} must be a number.`;
  }
  if (byteLength(answer) > NAME_MAX && typed)
    return `${label} can be at most ${NAME_MAX} characters.`;
  return byteLength(comment) > TEXT_MAX
    ? `The comment can be at most ${TEXT_MAX} characters.`
    : undefined;
};

/** The page's values as the server takes them. */
export const toCreateRequest = (
  structureId: string,
  inspection: InspectionValues,
  hazards: HazardValues,
  answers: Answers,
): InspectionCreateRequest => ({
  structureId: Number(structureId),
  typeCode: inspection.typeCode,
  statusCode: inspection.statusCode,
  inspectionDate: inspection.inspectionDate.trim() ? toIso(inspection.inspectionDate) : null,
  inspectorName: inspection.inspectorName,
  inspectorCompanyName: inspection.inspectorCompanyName,
  cost: toCost(inspection.cost),
  professionalInspectionRequired: inspection.professionalInspectionRequired,
  inspectionComment: inspection.inspectionComment,
  presentWaterDepth: toMetres(hazards.presentWaterDepth),
  presentWaterWidth: toMetres(hazards.presentWaterWidth),
  highWaterClearance: toMetres(hazards.highWaterClearance),
  highWater: hazards.highWater,
  debris: hazards.debris,
  scour: hazards.scour,
  ice: hazards.ice,
  aggradation: hazards.aggradation,
  hazardComment: hazards.hazardComment,
  // Answered ones only, as the server keeps them; a comment alone has been refused above.
  items: Object.values(answers).filter((entry) => entry.answer.trim()),
});
