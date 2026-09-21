import { createContext } from 'react';

export type UnsavedChangesContextType = {
  /** True while some screen is holding work that would be lost by navigating away. */
  isDirty: boolean;
  /** Set by the screen that owns the work — see `useUnsavedChangesPrompt`. */
  setDirty: (dirty: boolean) => void;
};

export const UnsavedChangesContext = createContext<UnsavedChangesContextType | undefined>(
  undefined,
);
