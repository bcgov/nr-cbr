import { useMemo } from 'react';

import type { CommonCodeTables } from './criteriaGroups';

import {
  useBusinessAreas,
  useForestDistricts,
  useManagementAreas,
  usePortableSuperstructureStatusCodes,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
} from '@/hooks/useConfiguration';

/**
 * The lists the Structure Inventory reports' shared boxes draw on — the management areas for the
 * district chosen. A failed lookup is an empty list, not a blocked form: every criterion is
 * optional, and the page reports the failure above the form instead.
 */
export const useCommonCodeTables = (orgUnit: string) => {
  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const portableSuperstructureStatusCodes = usePortableSuperstructureStatusCodes();
  const forestDistricts = useForestDistricts();
  const businessAreas = useBusinessAreas();
  const managementAreas = useManagementAreas(orgUnit);

  const codeTables = useMemo<CommonCodeTables>(
    () => ({
      siteStatusCodes: siteStatusCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      portableSuperstructureStatusCodes: portableSuperstructureStatusCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      businessAreas: businessAreas.data ?? [],
      managementAreas: managementAreas.data ?? [],
    }),
    [
      siteStatusCodes.data,
      siteTypeCodes.data,
      specialAccessCodes.data,
      portableSuperstructureStatusCodes.data,
      forestDistricts.data,
      businessAreas.data,
      managementAreas.data,
    ],
  );

  return { codeTables, managementAreasLoading: managementAreas.isFetching };
};
