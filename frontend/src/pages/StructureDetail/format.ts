import type { CodeValue, OutstandingItem, OutstandingSection } from './structureResponse';

/** A code as the user reads it: its description, or the bare code when the table has none. */
export const describe = (value: CodeValue | null | undefined): string =>
  value?.description || value?.code || '';

/**
 * Whether a code means "Other", which legacy follows with an "If Other, please specify" comment.
 * Design vehicle spells it out; every other table uses `OTH`.
 */
export const isOther = (value: CodeValue | null | undefined): boolean =>
  value?.code === 'OTH' || value?.code === 'OTHER';

export const yesNo = (value: boolean): string => (value ? 'Yes' : 'No');

const dollars = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  maximumFractionDigits: 0,
});

/** "$12,500" — whole dollars, as the columns hold them. Empty when nothing is stored. */
export const money = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : dollars.format(value);

/** A stored number as text, or empty — so `0` shows as 0 rather than as "nothing stored". */
export const number = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value);

/**
 * One tab's outstanding items, grouped by the section (card) they belong to — as nr-frep groups its
 * checklist's by record — in the order the server lists them.
 */
export type OutstandingGroup = { title: string; items: string[] };

export const groupOutstanding = (
  items: OutstandingItem[],
  section: OutstandingSection,
): OutstandingGroup[] => {
  const groups: OutstandingGroup[] = [];
  for (const item of items.filter((each) => each.section === section)) {
    const group = groups.find((each) => each.title === item.card);
    if (group) group.items.push(item.label);
    else groups.push({ title: item.card, items: [item.label] });
  }
  return groups;
};
