import { EMPTY_HAZARD_VALUES, EMPTY_INSPECTION_VALUES } from './inspectionRules';

import type { InspectionFormResponse } from './inspectionForm';
import type { OutstandingGroup } from '@/pages/StructureDetail/format';

export type TabKey = 'inspection' | 'condition' | 'hazards';

const INSPECTION_FIELDS = new Set(Object.keys(EMPTY_INSPECTION_VALUES));
const HAZARD_FIELDS = new Set(Object.keys(EMPTY_HAZARD_VALUES));

/** The tab a field's message belongs to. */
export const tabOf = (key: string): TabKey => {
  if (key.startsWith('items.')) return 'condition';
  if (HAZARD_FIELDS.has(key)) return 'hazards';
  // The structure's and the inspection's own fields share the Inspection tab.
  return 'inspection';
};

/** Where a message shows: the card's title, and the line under it. */
type Placed = { card: string; order: number; item: string };

/** An Inspection or Hydraulic Hazards field's card. */
const cardOf = (key: string): { card: string; order: number } => {
  if (key === 'inspectionComment') return { card: 'Inspection Comments', order: 2 };
  if (INSPECTION_FIELDS.has(key)) return { card: 'Inspection', order: 1 };
  if (HAZARD_FIELDS.has(key)) return { card: 'Hydraulic Hazards', order: 0 };
  return { card: 'Structure and site', order: 0 };
};

/**
 * An answer's place: its group's card — and its span, pier and stringer, as the card heads them —
 * and the question, so the line says which one. Keyed `items.SUB.Q[.span-ID][.pier-ID][.stringer-N]`.
 */
const placeAnswer = (
  key: string,
  message: string,
  form: InspectionFormResponse | undefined,
): Placed => {
  const [, subsectionCode, questionCode, ...where] = key.split('.');
  const groupIndex = form?.subsections.findIndex((each) => each.code === subsectionCode) ?? -1;
  const group = form?.subsections[groupIndex];
  const question = group?.questions.find((each) => each.code === questionCode);
  const parts: string[] = [];
  let order = Math.max(groupIndex, 0) * 1_000_000;
  for (const part of where) {
    const [kind, id] = part.split('-');
    if (kind === 'span' || kind === 'pier') {
      const list = (kind === 'span' ? form?.spans : form?.piers) ?? [];
      const index = list.findIndex((each) => each.id === id);
      parts.push(`${kind === 'span' ? 'Span' : 'Pier'} ${list[index]?.number ?? id}`);
      order += (index + 1) * 1_000;
    } else if (kind === 'stringer') {
      parts.push(`Stringer ${id}`);
      order += Number(id);
    }
  }
  const title = group?.description ?? subsectionCode;
  // Legacy's messages name the group and question — "(Deck) Deck condition requires a comment." —
  // which the card title already says; the rest are about the question, so it is named first.
  const prefix = `(${title}) `;
  const item = message.startsWith(prefix)
    ? message.slice(prefix.length)
    : `${question?.description ?? questionCode}: ${message}`;
  return { card: [title, ...parts].join(' — '), order, item };
};

/**
 * A tab's messages as the structure page lists what is outstanding: grouped by the card each field
 * is in, in the order the cards are shown — "Abutments — Span 1", "Abutment condition requires a
 * comment."
 */
export const errorGroups = (
  shown: Record<string, string | undefined>,
  tab: TabKey,
  form: InspectionFormResponse | undefined,
): OutstandingGroup[] => {
  const placed: Placed[] = [];
  for (const [key, message] of Object.entries(shown)) {
    if (!message || tabOf(key) !== tab) continue;
    placed.push(
      key.startsWith('items.')
        ? placeAnswer(key, message, form)
        : { ...cardOf(key), item: message },
    );
  }
  placed.sort((a, b) => a.order - b.order);
  const groups: OutstandingGroup[] = [];
  for (const { card, item } of placed) {
    const group = groups.find((each) => each.title === card);
    if (group) group.items.push(item);
    else groups.push({ title: card, items: [item] });
  }
  return groups;
};
