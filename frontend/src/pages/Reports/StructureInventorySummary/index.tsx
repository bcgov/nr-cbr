import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import PageTitle from '@/components/core/PageTitle';

import StructureInventorySummaryCriteriaForm, {
  type CodeTables,
} from './StructureInventorySummaryCriteria';
import { EMPTY_CRITERIA, type ReportFormat, type StructureInventorySummaryCriteria } from './types';

import '../reportCriteria.scss';

import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import {
  useBusinessAreas,
  useCulvertMaterialCodes,
  useCulvertTypeCodes,
  useDeckTypeCodes,
  useForestDistricts,
  useManagementAreas,
  usePortableSuperstructureStatusCodes,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInventorySummaryReferenceDataState,
  useStructureTypeClassCodes,
  useSuperstructureTypeCodes,
} from '@/hooks/useConfiguration';
import { useStructureInventorySummaryReport } from '@/hooks/useReports';
import { apiErrorMessage } from '@/utils/apiError';
import { saveFile } from '@/utils/saveFile';
import { readSessionState, writeSessionState } from '@/utils/sessionState';

/**
 * "StructureInventorySummaryReport_2026-10-20.pdf" — the name the server gives it: legacy's pattern
 * for its reports' files, dated today.
 */
const reportFilename = (format: ReportFormat): string => {
  const today = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const date = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
  return `StructureInventorySummaryReport_${date}.${format.toLowerCase()}`;
};

/** Where the page keeps its criteria for the tab. */
export const STRUCTURE_INVENTORY_SUMMARY_STATE_KEY = 'cbr.report.structureInventorySummary';

/**
 * Structure Inventory – Summary — the first of the Reports (legacy CBR008).
 *
 * <p>Legacy equivalent: `showStructureInventorySummaryReport.do` →
 * `structure_inventory_summary.jsp`, gated on `/showStructureInventorySummaryReport`, which every
 * role that can read holds. One row per structure, filtered by what it is built of, where it
 * stands and who maintains it.
 *
 * <p>The criteria are kept for the browser tab, as on the search pages, so leaving and coming back
 * finds them as they were left; Clear forgets them. Legacy's Save As / Delete Report Favourite are
 * not carried over for now.
 *
 * <p>Download PDF and Download CSV check the criteria, then fetch the report from the server and
 * save it. Legacy's PDF layout is the server's; the CSV is the same columns.
 */
const StructureInventorySummaryPage: FC = () => {
  const [criteria, setCriteria] = useState<StructureInventorySummaryCriteria>(() => ({
    ...EMPTY_CRITERIA,
    ...readSessionState<Partial<StructureInventorySummaryCriteria>>(
      STRUCTURE_INVENTORY_SUMMARY_STATE_KEY,
    ),
  }));
  const [resetToken, setResetToken] = useState(0);
  const { display } = useNotification();

  useEffect(() => {
    writeSessionState(STRUCTURE_INVENTORY_SUMMARY_STATE_KEY, criteria);
  }, [criteria]);

  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const structureTypeClassCodes = useStructureTypeClassCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const portableSuperstructureStatusCodes = usePortableSuperstructureStatusCodes();
  const culvertTypeCodes = useCulvertTypeCodes();
  const superstructureTypeCodes = useSuperstructureTypeCodes();
  const culvertMaterialCodes = useCulvertMaterialCodes();
  const deckTypeCodes = useDeckTypeCodes();
  const forestDistricts = useForestDistricts();
  const businessAreas = useBusinessAreas();
  const managementAreas = useManagementAreas(criteria.orgUnit);

  const referenceData = useStructureInventorySummaryReferenceDataState();

  // A failed lookup is an empty list, not a blocked form — every criterion is optional. The
  // failure is reported above the form instead.
  const codeTables = useMemo<CodeTables>(
    () => ({
      siteStatusCodes: siteStatusCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      structureTypeClassCodes: structureTypeClassCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      portableSuperstructureStatusCodes: portableSuperstructureStatusCodes.data ?? [],
      culvertTypeCodes: culvertTypeCodes.data ?? [],
      superstructureTypeCodes: superstructureTypeCodes.data ?? [],
      culvertMaterialCodes: culvertMaterialCodes.data ?? [],
      deckTypeCodes: deckTypeCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      businessAreas: businessAreas.data ?? [],
      managementAreas: managementAreas.data ?? [],
    }),
    [
      siteStatusCodes.data,
      siteTypeCodes.data,
      structureTypeClassCodes.data,
      specialAccessCodes.data,
      portableSuperstructureStatusCodes.data,
      culvertTypeCodes.data,
      superstructureTypeCodes.data,
      culvertMaterialCodes.data,
      deckTypeCodes.data,
      forestDistricts.data,
      businessAreas.data,
      managementAreas.data,
    ],
  );

  const updateCriteria = useCallback(
    <K extends keyof StructureInventorySummaryCriteria>(
      field: K,
      value: StructureInventorySummaryCriteria[K],
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

  const report = useStructureInventorySummaryReport();
  /** The format being produced, while it is; the buttons wait for it. */
  const running = report.isPending ? (report.variables?.format ?? null) : null;

  const run = useCallback(
    (format: ReportFormat) => {
      report.mutate(
        { criteria, format },
        {
          onSuccess: (file) => saveFile(file, reportFilename(format)),
          onError: (error) => {
            display({
              kind: 'error',
              title: `The ${format} could not be produced`,
              subtitle: apiErrorMessage(
                error,
                'Try again, or narrow the criteria. Contact support if this continues.',
              ),
              timeout: 0,
            });
          },
        },
      );
    },
    [report, criteria, display],
  );

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setResetToken((token) => token + 1);
  }, []);

  return (
    <Grid fullWidth className="default-grid report-criteria-grid">
      <PageTitle
        title="Structure Inventory – Summary"
        subtitle="One line per structure: filter by what it is built of, where it stands and who maintains it."
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
            data-testid="structure-inventory-summary-codes-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        {/* Keyed on Clear, so the boxes visited before it stop showing their errors. */}
        <StructureInventorySummaryCriteriaForm
          key={resetToken}
          criteria={criteria}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={updateCriteria}
          onRun={run}
          running={running}
          onReset={reset}
        />
      </Column>
    </Grid>
  );
};

export default StructureInventorySummaryPage;
