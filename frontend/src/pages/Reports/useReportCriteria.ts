import { useCallback, useEffect, useState } from 'react';

import { readSessionState, writeSessionState } from '@/utils/sessionState';

/**
 * A report page's criteria: kept for the browser tab under the report's key, so leaving and coming
 * back finds them as they were left, and forgotten by Clear.
 *
 * <p>Changing the Forest District clears the Management Area: an area belongs to one district, and
 * keeping it would apply a filter the user can no longer see. `resetToken` changes on Clear — key
 * the form on it, so the boxes visited before it stop showing their errors.
 */
export const useReportCriteria = <C extends { orgUnit: string; managementOrgUnit: string }>(
  stateKey: string,
  empty: C,
) => {
  const [criteria, setCriteria] = useState<C>(() => ({
    ...empty,
    ...readSessionState<Partial<C>>(stateKey),
  }));
  const [resetToken, setResetToken] = useState(0);

  useEffect(() => {
    writeSessionState(stateKey, criteria);
  }, [stateKey, criteria]);

  const updateCriteria = useCallback(<K extends keyof C>(field: K, value: C[K]) => {
    setCriteria((current) => ({
      ...current,
      [field]: value,
      ...(field === 'orgUnit' ? { managementOrgUnit: '' } : {}),
    }));
  }, []);

  const reset = useCallback(() => {
    setCriteria(empty);
    setResetToken((token) => token + 1);
  }, [empty]);

  return { criteria, updateCriteria, reset, resetToken };
};
