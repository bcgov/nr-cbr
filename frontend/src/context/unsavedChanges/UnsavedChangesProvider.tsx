import { useCallback, useMemo, useState, type FC, type ReactNode } from 'react';

import { UnsavedChangesContext } from './UnsavedChangesContext';

/**
 * Holds whether the screen on display has unsaved work.
 *
 * <p><b>Above the router, not inside it.</b> The state has to outlive the navigation it is
 * blocking: a provider mounted per route would be torn down by the very transition it exists to
 * intercept. {@link UnsavedChangesGuard} reads it from inside the router, which is where
 * `useBlocker` can be called.
 *
 * <p>One flag rather than a set of registrations, because one screen is on display at a time. The
 * hook that sets it clears it on unmount, so a page cannot leave the flag raised behind it.
 */
export const UnsavedChangesProvider: FC<{ children: ReactNode }> = ({ children }) => {
  const [isDirty, setIsDirty] = useState(false);
  const setDirty = useCallback((dirty: boolean) => setIsDirty(dirty), []);
  const value = useMemo(() => ({ isDirty, setDirty }), [isDirty, setDirty]);

  return <UnsavedChangesContext.Provider value={value}>{children}</UnsavedChangesContext.Provider>;
};
