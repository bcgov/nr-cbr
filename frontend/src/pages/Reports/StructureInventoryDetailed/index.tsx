import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import PageTitle from '@/components/core/PageTitle';

import StructureInventoryDetailedCriteriaForm, {
  type CodeTables,
} from './StructureInventoryDetailedCriteria';
import {
  EMPTY_CRITERIA,
  type ReportFormat,
  type StructureInventoryDetailedCriteria,
} from './types';

import '../reportCriteria.scss';

import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import {
  useBusinessAreas,
  useForestDistricts,
  useManagementAreas,
  usePortableSuperstructureStatusCodes,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInventoryDetailedReferenceDataState,
} from '@/hooks/useConfiguration';
import { readSessionState, writeSessionState } from '@/utils/sessionState';

/** Where the page keeps its criteria for the tab. */
export const STRUCTURE_INVENTORY_DETAILED_STATE_KEY = 'cbr.report.structureInventoryDetailed';

/**
 * Structure Inventory – Detailed — a full record per structure (legacy CBR007).
 *
 * <p>Legacy equivalent: `showStructureInventoryDetailedReport.do` →
 * `structure_inventory_detailed.jsp`, gated on `/showStructureInventoryDetailedReport`, which every
 * role that can read holds. Where the Summary is a line per structure, this is a block of labelled
 * fields per structure, and it alone of the reports also comes as a KMZ for a map.
 *
 * <p>The criteria are kept for the browser tab, as on the Summary; Clear forgets them.
 *
 * <p>The Download buttons check the criteria and stop there: the report itself is not built yet.
 */
const StructureInventoryDetailedPage: FC = () => {
  const [criteria, setCriteria] = useState<StructureInventoryDetailedCriteria>(() => ({
    ...EMPTY_CRITERIA,
    ...readSessionState<Partial<StructureInventoryDetailedCriteria>>(
      STRUCTURE_INVENTORY_DETAILED_STATE_KEY,
    ),
  }));
  const [resetToken, setResetToken] = useState(0);
  const { display } = useNotification();

  useEffect(() => {
    writeSessionState(STRUCTURE_INVENTORY_DETAILED_STATE_KEY, criteria);
  }, [criteria]);

  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const portableSuperstructureStatusCodes = usePortableSuperstructureStatusCodes();
  const forestDistricts = useForestDistricts();
  const businessAreas = useBusinessAreas();
  const managementAreas = useManagementAreas(criteria.orgUnit);

  const referenceData = useStructureInventoryDetailedReferenceDataState();

  // A failed lookup is an empty list, not a blocked form — every criterion is optional. The
  // failure is reported above the form instead.
  const codeTables = useMemo<CodeTables>(
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

  const updateCriteria = useCallback(
    <K extends keyof StructureInventoryDetailedCriteria>(
      field: K,
      value: StructureInventoryDetailedCriteria[K],
    ) => {
      setCriteria((current) => ({
        ...current,
        [field]: value,
        // A management area belongs to one district; keeping it after the district changes would
        // apply a filter the user can no longer see.
        ...(field === 'orgUnit' ? { managementOrgUnit: '' } : {}),
      }));
    },
    [],
  );

  const run = useCallback(
    (format: ReportFormat) => {
      display({
        kind: 'info',
        title: 'This report is not available yet',
        subtitle: `The criteria are valid. The ${format} is still being built.`,
        timeout: 6000,
      });
    },
    [display],
  );

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setResetToken((token) => token + 1);
  }, []);

  return (
    <Grid fullWidth className="default-grid report-criteria-grid">
      <PageTitle
        title="Structure Inventory – Detailed"
        subtitle="A full record per structure: filter by where it stands, who maintains it and when it is due for replacement."
        breadCrumbs={[{ name: 'Reports', path: '/reports' }]}
      />

      {referenceData.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Some filters could not be loaded"
            subtitle={
              'One or more of the dropdown lists is unavailable, so those filters are empty. ' +
              'Every other criterion still works.'
            }
            data-testid="structure-inventory-detailed-codes-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        {/* Keyed on Clear, so the boxes visited before it stop showing their errors. */}
        <StructureInventoryDetailedCriteriaForm
          key={resetToken}
          criteria={criteria}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={updateCriteria}
          onRun={run}
          onReset={reset}
        />
      </Column>
    </Grid>
  );
};

export default StructureInventoryDetailedPage;
