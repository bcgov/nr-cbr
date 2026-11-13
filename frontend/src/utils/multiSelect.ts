/**
 * Helpers for the search and report filters that take several values (UAT "CBR Pick list",
 * 2026-11): Forest District and the rest, held in the criteria as `string[]`, empty meaning "any".
 */

/** The picks as one query parameter — the server reads `ACT,BAR` as "ACT or BAR". */
export const joinValues = (values: readonly string[]): string =>
  values
    .map((value) => value.trim())
    .filter((value) => value !== '')
    .join(',');

/**
 * The districts as a stable key for the management-area lookup: sorted, so picking D1 then D2 and
 * D2 then D1 share one cache entry. Accepts a single district too, for the forms not yet changed.
 */
export const districtsKey = (districts: string | readonly string[]): string =>
  typeof districts === 'string' ? districts.trim() : joinValues([...districts].sort());

/**
 * The management areas to keep when the districts change.
 *
 * <p>Adding a district keeps every area picked — each still belongs to a chosen district. Removing
 * one clears them: an area of the removed district would stay applied, invisible in a list that no
 * longer offers it, and nothing on the screen says which district an area came from.
 */
export const areasAfterDistrictChange = (
  before: readonly string[],
  after: readonly string[],
  areas: readonly string[],
): string[] => (before.every((district) => after.includes(district)) ? [...areas] : []);

/**
 * Criteria saved for the tab, made to fit the page as it is now. A filter that has become a
 * multi-select was saved as a string by the page before it; it comes back as a list of that one
 * value, or an empty list, instead of breaking the form.
 */
export const restoreCriteria = <C extends object>(empty: C, saved: Partial<C> | null): C => {
  const restored = { ...empty, ...saved } as Record<string, unknown>;
  for (const [field, blank] of Object.entries(empty)) {
    const value = restored[field];
    if (Array.isArray(blank) && !Array.isArray(value)) {
      restored[field] = typeof value === 'string' && value.trim() !== '' ? [value] : [];
    }
  }
  return restored as C;
};
