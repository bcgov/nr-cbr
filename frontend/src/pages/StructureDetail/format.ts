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

/** Where each section's outstanding items are listed, and what the group is called there. */
export const SECTION_TITLES: Record<OutstandingSection, string> = {
  SITE: 'Site',
  DETAILS: 'Details',
  INSPECTIONS: 'Inspections',
};

export type OutstandingGroup = { title: string; items: string[] };

/** The items grouped by section, in page order, leaving out sections with nothing outstanding. */
export const groupOutstanding = (items: OutstandingItem[]): OutstandingGroup[] =>
  (Object.keys(SECTION_TITLES) as OutstandingSection[])
    .map((section) => ({
      title: SECTION_TITLES[section],
      items: items.filter((item) => item.section === section).map((item) => item.label),
    }))
    .filter((group) => group.items.length > 0);
