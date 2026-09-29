import type { SiteSearchCriteria } from './types';

/**
 * A kilometre bound: unset, or a decimal that fits `CROSSING_SITE`'s `NUMBER(8,2)` — up to six
 * integer digits and two decimal places.
 *
 * <p>Paired with `SiteSearchCriteria.DECIMAL` in the backend, which must say the same thing. The
 * backend is the one that enforces it, because the browser is not the only caller; this copy exists
 * so the user is told before submitting rather than by a 400.
 *
 * <p>Narrower than legacy, which accepts anything `new Double(...)` parses — `1e3`, a leading `-`,
 * and values far too large for the column all pass its check and then match nothing.
 */
export const KILOMETRE_PATTERN = /^\d{1,6}(\.\d{1,2})?$/;

export const isKilometre = (value: string): boolean =>
  value === '' || KILOMETRE_PATTERN.test(value);

/** The two numeric bounds, and the label they belong to. */
const KILOMETRE_FIELDS = {
  kiloStart: 'Kilometres',
  kiloEnd: 'Kilometres',
} as const satisfies Partial<Record<keyof SiteSearchCriteria, string>>;

export type CriteriaErrors = Partial<Record<keyof SiteSearchCriteria, string>>;

/**
 * Field-level messages for whatever is currently wrong.
 *
 * <p>One entry per offending box, not one per range. Legacy raises a single page-level "Kilometres
 * must be numeric" for the pair, which leaves the user to work out which of the two it means.
 *
 * <p>Each bound is judged on its own. Legacy's `createSearch()` guards the User Km upper bound with
 * a test on `kiloEnd` — the wrong getter — so that box silently does nothing unless an unrelated one
 * is filled. Nothing here may couple two fields for the same reason.
 */
export const criteriaErrors = (criteria: SiteSearchCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  for (const [field, label] of Object.entries(KILOMETRE_FIELDS)) {
    const value = criteria[field as keyof SiteSearchCriteria];
    if (typeof value === 'string' && !isKilometre(value)) {
      errors[field as keyof SiteSearchCriteria] = `${label} must be a number, e.g. 12.5`;
    }
  }
  return errors;
};
