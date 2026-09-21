import { useContext } from 'react';

import { UnsavedChangesContext, type UnsavedChangesContextType } from './UnsavedChangesContext';

/**
 * The unsaved-changes flag.
 *
 * <p>Falls back to a no-op rather than throwing when no provider is above it. The guard that reads
 * this is rendered by `Layout`, which a test or a Storybook story may mount on its own; a throw
 * there would fail for the absence of a feature the story is not about. Nothing is lost by the
 * fallback — with no provider there is no navigation to block either.
 */
export const useUnsavedChanges = (): UnsavedChangesContextType => {
  const context = useContext(UnsavedChangesContext);
  return context ?? { isDirty: false, setDirty: () => {} };
};
