import { useEffect } from 'react';

import { useUnsavedChanges } from '@/context/unsavedChanges/useUnsavedChanges';

/**
 * Declares that this screen is holding work that navigating away would lose.
 *
 * <p>Call it with whatever the screen already knows about being edited:
 *
 * ```tsx
 * useUnsavedChangesPrompt(isDirty);
 * ```
 *
 * <p>Everything else is handled once, for the whole application, by `UnsavedChangesGuard` — the
 * dialog, the router blocker and the browser's own tab-close warning. A screen never renders a
 * prompt of its own, so the wording cannot drift from one form to the next.
 *
 * <p><b>Clears on unmount</b>, which is what makes a stale flag impossible: a screen that left
 * with work outstanding would otherwise block the next navigation on the page that replaced it.
 */
export const useUnsavedChangesPrompt = (isDirty: boolean): void => {
  const { setDirty } = useUnsavedChanges();

  useEffect(() => {
    setDirty(isDirty);
    return () => setDirty(false);
  }, [isDirty, setDirty]);
};
