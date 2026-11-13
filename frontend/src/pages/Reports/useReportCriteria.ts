import { useCallback, useEffect, useState } from 'react';

import { areasAfterDistrictChange, restoreCriteria } from '@/utils/multiSelect';
import { readSessionState, writeSessionState } from '@/utils/sessionState';

/**
 * The Management Area after the Forest District changes. A single-choice district (Structure Detail
 * Report) clears it; a multi-select one keeps the areas when a district is added and clears them
 * when one is removed — see areasAfterDistrictChange.
 */
const areasAfter = (
  before: string | string[],
  after: unknown,
  areas: string | string[] | undefined,
): string | string[] =>
  Array.isArray(before) && Array.isArray(after) && Array.isArray(areas)
    ? areasAfterDistrictChange(before, after as string[], areas)
    : '';

/**
 * A report page's criteria: kept for the browser tab under the report's key, so leaving and coming
 * back finds them as they were left, and forgotten by Clear.
 *
 * <p>Changing the Forest District clears the Management Area, on a report that has one: an area
 * belongs to one district, and keeping it would apply a filter the user can no longer see. Where
 * the district is a multi-select, only removing a district clears it. `resetToken` changes on Clear — key
 * the form on it, so the boxes visited before it stop showing their errors.
 */
export const useReportCriteria = <
  C extends { orgUnit: string | string[]; managementOrgUnit?: string | string[] },
>(
  stateKey: string,
  empty: C,
) => {
  // restoreCriteria turns a filter saved as one value, before it became a multi-select, into a
  // list of that value.
  const [criteria, setCriteria] = useState<C>(() =>
    restoreCriteria(empty, readSessionState<Partial<C>>(stateKey)),
  );
  const [resetToken, setResetToken] = useState(0);

  useEffect(() => {
    writeSessionState(stateKey, criteria);
  }, [stateKey, criteria]);

  const updateCriteria = useCallback(<K extends keyof C>(field: K, value: C[K]) => {
    setCriteria((current) => ({
      ...current,
      [field]: value,
      ...(field === 'orgUnit' && 'managementOrgUnit' in current
        ? { managementOrgUnit: areasAfter(current.orgUnit, value, current.managementOrgUnit) }
        : {}),
    }));
  }, []);

  const reset = useCallback(() => {
    setCriteria(empty);
    setResetToken((token) => token + 1);
  }, [empty]);

  return { criteria, updateCriteria, reset, resetToken };
};
