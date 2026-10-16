import type { InspectionDetailResponse, InspectionItem } from './inspectionDetail';
import type {
  BridgePart,
  InspectionFormResponse,
  InspectionQuestion,
  InspectionSubsection,
} from '@/pages/NewInspection/inspectionForm';
import type { OutstandingGroup } from '@/pages/StructureDetail/format';

/** The tabs that list what is outstanding. */
export type OutstandingTab = 'inspection' | 'condition' | 'hazards';

export type InspectionOutstanding = Record<OutstandingTab, OutstandingGroup[]>;

const IN_PROGRESS = 'PRO';

/** Legacy's answers that need a comment: Poor and Unrated. */
const NEEDS_COMMENT = new Set(['P', 'UNR']);

const blank = (value: string | number | null | undefined) =>
  value === null || value === undefined || String(value).trim() === '';

/** The fields a group lists: those of `[label, value]` that are blank. */
const missing = (fields: [string, string | number | null | undefined][]) =>
  fields.filter(([, value]) => blank(value)).map(([label]) => label);

const partLabel = (kind: string, part: BridgePart) => `${kind} ${part.number ?? part.id}`;

/** Where a block of questions is asked: a span or pier, a stringer, and how it is headed. */
type Block = {
  title: string;
  spanId: string | null;
  pierId: string | null;
  stringerNumber: number | null;
  questions: InspectionQuestion[];
};

/** The stringers recorded for a group and span — the only ones whose questions can be missing. */
const stringersOf = (items: InspectionItem[], subsection: string, spanId: string | null) =>
  [
    ...new Set(
      items
        .filter(
          (item) =>
            item.subsectionCode === subsection &&
            item.spanId === spanId &&
            item.stringerNumber !== null,
        )
        .map((item) => item.stringerNumber as number),
    ),
  ].sort((a, b) => a - b);

/** A group's blocks, as its card shows them: per span or pier, then per stringer. */
const blocksOf = (
  subsection: InspectionSubsection,
  form: InspectionFormResponse,
  items: InspectionItem[],
): Block[] => {
  const groupQuestions = subsection.questions.filter((question) => !question.perStringer);
  const stringerQuestions = subsection.questions.filter((question) => question.perStringer);
  const wheres: { title: string; spanId: string | null; pierId: string | null }[] = [];
  if (subsection.scope === 'SPAN') {
    for (const span of form.spans) {
      wheres.push({
        title: `${subsection.description} — ${partLabel('Span', span)}`,
        spanId: span.id,
        pierId: null,
      });
    }
  } else if (subsection.scope === 'PIER') {
    for (const pier of form.piers) {
      wheres.push({
        title: `${subsection.description} — ${partLabel('Pier', pier)}`,
        spanId: null,
        pierId: pier.id,
      });
    }
  } else {
    wheres.push({ title: subsection.description, spanId: null, pierId: null });
  }
  return wheres.flatMap((where) => [
    { ...where, stringerNumber: null, questions: groupQuestions },
    ...stringersOf(items, subsection.code, where.spanId).map((number) => ({
      ...where,
      title: `${where.title} — Stringer ${number}`,
      stringerNumber: number,
      questions: stringerQuestions,
    })),
  ]);
};

/** Where an answer is kept: its group, question, span, pier and stringer. */
const keyOf = (
  subsection: string,
  question: string,
  where: { spanId: string | null; pierId: string | null; stringerNumber: number | null },
) => [subsection, question, where.spanId, where.pierId, where.stringerNumber].join('|');

/**
 * What is wrong with one answer by legacy's two rules, or nothing: a comment needs an answer, and a
 * Poor or Unrated answer needs a comment. A question left unanswered is allowed.
 */
const problemOf = (question: InspectionQuestion, item: InspectionItem | undefined) => {
  if (item === undefined) return null;
  if (blank(item.answer) && !blank(item.comment)) return `${question.description} — answer`;
  if (NEEDS_COMMENT.has(item.answer ?? '') && blank(item.comment)) {
    return `${question.description} — comment`;
  }
  return null;
};

/**
 * The Condition tab's outstanding items — legacy's rules only (see {@link problemOf}) — grouped as
 * the cards head them.
 */
const conditionOutstanding = (
  inspection: InspectionDetailResponse,
  form: InspectionFormResponse,
): OutstandingGroup[] => {
  const answered = new Map(
    inspection.items.map((item) => [keyOf(item.subsectionCode, item.questionCode, item), item]),
  );
  return form.subsections.flatMap((subsection) =>
    blocksOf(subsection, form, inspection.items)
      .map((block) => ({
        title: block.title,
        items: block.questions
          .map((question) =>
            problemOf(question, answered.get(keyOf(subsection.code, question.code, block))),
          )
          .filter((problem): problem is string => problem !== null),
      }))
      .filter((group) => group.items.length > 0),
  );
};

/**
 * What a saved inspection lacks by legacy's rules, by the tab and card it belongs to
 * (`cbr-inspection-page.local.md` §8):
 *
 * <ul>
 *   <li>past In Progress — Submitted, Reviewed, … — Date of Inspection, Inspector Name, Inspection
 *       Company Name and the three water measurements are required (legacy's page marks the
 *       measurements "Required field."); In Progress, they may all wait;
 *   <li>at any status, a Poor or Unrated answer needs a comment, and a comment needs an answer.
 * </ul>
 *
 * Inspections from legacy can be Submitted, or Reviewed, with gaps: they show.
 *
 * @param form the questions the structure is asked; Condition lists nothing until it has loaded
 */
export const outstandingOf = (
  inspection: InspectionDetailResponse,
  form: InspectionFormResponse | undefined,
): InspectionOutstanding => {
  // Legacy's FRM.validate: past In Progress, these are required; In Progress, they may wait.
  const pastInProgress = Boolean(inspection.status?.code) && inspection.status.code !== IN_PROGRESS;
  if (!pastInProgress) {
    return {
      inspection: [],
      condition: form ? conditionOutstanding(inspection, form) : [],
      hazards: [],
    };
  }
  const required = missing([
    ['Date of Inspection', inspection.inspectionDate],
    ['Inspector Name', inspection.inspectorName],
    ['Inspection Company Name', inspection.inspectorCompanyName],
  ]);
  const measurements = missing([
    ['Estimated Present Water Level Depth', inspection.hazards.presentWaterDepth],
    ['Estimated Present Water Level Width', inspection.hazards.presentWaterWidth],
    ['Estimated High Water Level Clearance', inspection.hazards.highWaterClearance],
  ]);
  return {
    inspection: required.length > 0 ? [{ title: 'Inspection', items: required }] : [],
    condition: form ? conditionOutstanding(inspection, form) : [],
    hazards: measurements.length > 0 ? [{ title: 'Hydraulic Hazards', items: measurements }] : [],
  };
};

/** How many items a tab's groups hold. */
export const countOf = (groups: OutstandingGroup[]): number =>
  groups.reduce((sum, group) => sum + group.items.length, 0);
