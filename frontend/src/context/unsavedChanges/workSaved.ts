/**
 * Navigation state saying the work on the page being left has just been saved, so leaving it loses
 * nothing: `navigate(path, { state: WORK_SAVED })`.
 *
 * <p>Needed because a screen cannot stand the guard down in time on its own. It reports being
 * clean through an effect, which runs after the next render — but a save's success handler
 * navigates in the same tick it learns of the success, while the guard still holds the old flag.
 * Add Site did exactly that and asked "Leave without saving?" of a site it had just stored. Carried
 * on the navigation itself, the answer cannot arrive late.
 */
export const WORK_SAVED = { workSaved: true } as const;

/** True when a navigation was marked with {@link WORK_SAVED}. */
export const isWorkSaved = (state: unknown): boolean =>
  typeof state === 'object' &&
  state !== null &&
  (state as { workSaved?: unknown }).workSaved === true;
