import { useCallback, useState } from 'react';

/**
 * Tracks which fields the user has been in and moved on from.
 *
 * The editors hold their errors back until Save, save for the ones no further typing can rescue
 * (see `utils/validation.ts`). This is the middle step: once a field has been *left*, whatever it
 * says about the value now in it can be shown, because the user is no longer part way through
 * writing it — blank included, so a required field left empty says so (see
 * {@link errorsForSettledFields}).
 *
 * Reset whenever the editor opens another record, alongside the save-attempted flag.
 */
export const useSettledFields = () => {
  const [settled, setSettled] = useState<ReadonlySet<string>>(() => new Set());

  const markSettled = useCallback(
    (key: string) => setSettled((prev) => (prev.has(key) ? prev : new Set(prev).add(key))),
    [],
  );

  const resetSettled = useCallback(() => setSettled(new Set()), []);

  return { settled, markSettled, resetSettled };
};
