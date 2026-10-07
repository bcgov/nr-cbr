/**
 * Small values kept for the browser tab — a search to come back to, say. Session storage rather
 * than local: it ends with the tab, so one user's criteria do not greet the next on a shared
 * computer, and two tabs keep two searches.
 *
 * <p>Storage can be full, blocked or unavailable, and a stored value can be from an older shape of
 * the page. Every call here fails quietly to "nothing saved": remembering is a convenience, and a
 * page that cannot remember still works.
 */

/** The value saved under `key`, or null when there is none or it cannot be read. */
export const readSessionState = <T>(key: string): T | null => {
  try {
    const raw = sessionStorage.getItem(key);
    return raw === null ? null : (JSON.parse(raw) as T);
  } catch {
    return null;
  }
};

/** Saves `value` under `key`; does nothing when storage refuses it. */
export const writeSessionState = (key: string, value: unknown): void => {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Not remembered — the page carries on.
  }
};

/** Forgets the value under `key`. */
export const clearSessionState = (key: string): void => {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Nothing to forget, or storage is unavailable.
  }
};
