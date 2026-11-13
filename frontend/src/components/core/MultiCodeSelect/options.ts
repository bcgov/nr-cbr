/**
 * The choices a `MultiCodeSelect` offers, built from the code tables and org units — kept apart
 * from the component so the page forms can share them (and fast refresh keeps working).
 */

/** One choice: what the server receives, and what the list shows. */
export type MultiCodeOption = { value: string; text: string };

/** The criteria of `C` that are multi-selects — its `string[]` fields. */
export type MultiFields<C> = {
  [K in keyof C]: C[K] extends string[] ? K : never;
}[keyof C] &
  string;

/** "ACT - Active"; a missing half degrades to the other rather than "ACT - undefined". */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

/** A code table as choices, each "ACT - Active", in the table's order. */
export const codeOptions = (
  options: readonly { code: string; description: string | null }[],
): MultiCodeOption[] =>
  options.map((option) => ({ value: option.code, text: label(option.code, option.description) }));

/**
 * Org units as choices — the number is the value, as `CROSSING_SITE` holds it (a code is not unique
 * across the org hierarchy); "DCK - Chilliwack …" is shown.
 */
export const orgUnitOptions = (
  options: readonly { orgUnitNo: string; orgUnitCode: string | null; orgUnitName: string | null }[],
): MultiCodeOption[] =>
  options.map((option) => ({
    value: option.orgUnitNo,
    text: label(option.orgUnitCode, option.orgUnitName),
  }));

/**
 * The value that means "no value at all" — the server's `CriteriaValues.NONE`. Never an org unit
 * number, so it cannot clash with one.
 */
export const IS_NULL_VALUE = 'NONE';

/**
 * The choices with "Is null" first — BCTS Business Area's, as the UAT pick list asked (2026-11),
 * finding structures held by no business area. Ticked beside an area, it finds either.
 */
export const withIsNull = (options: readonly MultiCodeOption[]): MultiCodeOption[] => [
  { value: IS_NULL_VALUE, text: 'Is null' },
  ...options,
];
