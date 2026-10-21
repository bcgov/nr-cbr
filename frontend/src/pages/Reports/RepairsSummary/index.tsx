import { useMemo } from 'react';

import MaintainerFields from '../MaintainerFields';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import {
  EMPTY_CRITERIA,
  FORMATS,
  REPAIR_PRIORITY_GROUPS,
  REPAIR_STATUS_GROUPS,
  SORT_OPTIONS,
  repairClassOptions,
} from './types';

import type { FC } from 'react';

import {
  useRepairClassCodes,
  useRepairPriorityCodes,
  useRepairsSummaryReferenceDataState,
  useRepairStatusCodes,
} from '@/hooks/useConfiguration';
import { useRepairsSummaryReport } from '@/hooks/useReports';

/** Where the page keeps its criteria for the tab. */
export const REPAIRS_SUMMARY_STATE_KEY = 'cbr.report.repairsSummary';

const ID = 'repairs-summary';

/** Nothing here can be malformed: every box is a code, free text or a switch. */
const NO_ERRORS = {};

/**
 * Repairs Summary — the repairs recorded on inspections, by structure (legacy CBR005).
 *
 * <p>Legacy equivalent: `showRepairSummaryReport.do` → `repair_summary.jsp`, gated on
 * `/showRepairSummaryReport`, which every role that can read holds. Laid out as four rows of the
 * reports' four-column grid: the structure and its site; the repairs — status, priority and
 * Structural Safety, each with legacy's own groups of codes after the codes — and the road; where
 * the structure is held; then its maintainer and the two switches, a cell each. Labels and defaults
 * are legacy's: Outstanding repairs, sorted by project and branch.
 *
 * <p>The criteria are kept for the browser tab, as on the other reports; Clear goes back to the
 * defaults. Download PDF and Download CSV fetch the report from the server and save it.
 */
const RepairsSummaryPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    REPAIRS_SUMMARY_STATE_KEY,
    EMPTY_CRITERIA,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const repairStatusCodes = useRepairStatusCodes();
  const repairPriorityCodes = useRepairPriorityCodes();
  const repairClassCodes = useRepairClassCodes();
  const referenceData = useRepairsSummaryReferenceDataState();

  const statusOptions = useMemo(
    () => [...(repairStatusCodes.data ?? []), ...REPAIR_STATUS_GROUPS],
    [repairStatusCodes.data],
  );
  const priorityOptions = useMemo(
    () => [...(repairPriorityCodes.data ?? []), ...REPAIR_PRIORITY_GROUPS],
    [repairPriorityCodes.data],
  );
  const classOptions = useMemo(
    () => repairClassOptions(repairClassCodes.data ?? []),
    [repairClassCodes.data],
  );

  const { run, running } = useReportDownload({
    report: useRepairsSummaryReport(),
    criteria,
    filename: 'RepairsSummaryReport',
  });

  return (
    <ReportPageLayout
      id={ID}
      title="Repairs Summary"
      subtitle="The repairs found on inspections, by structure: filter by their status, priority and safety, and where the structure stands."
      codeTablesFailed={referenceData.isError}
    >
      <ReportCriteriaForm
        key={resetToken}
        id={ID}
        criteria={criteria}
        sortOptions={SORT_OPTIONS}
        errors={NO_ERRORS}
        formats={FORMATS}
        codeTablesLoading={referenceData.isLoading}
        onChange={updateCriteria}
        onRun={run}
        running={running}
        onReset={reset}
      >
        {(fields) => (
          <>
            {/* The structure and its site */}
            {fields.text('structureName', 'Structure #', 14)}
            {fields.text('siteId', 'Site #', 10)}
            {fields.codeSelect(
              'siteStatusCode',
              'Site Status',
              'Any status',
              codeTables.siteStatusCodes,
            )}
            {fields.codeSelect(
              'siteTypeCode',
              'Site Type',
              'Any site type',
              codeTables.siteTypeCodes,
            )}

            {/* The repairs, and the road */}
            {fields.codeSelect('repairStatusCode', 'Repair Status', 'Any status', statusOptions)}
            {fields.codeSelect(
              'repairPriorityCode',
              'Repair Priority',
              'Any priority',
              priorityOptions,
            )}
            {fields.codeSelect(
              'repairClassCode',
              'Structural Safety',
              'Any structural safety',
              classOptions,
            )}
            {fields.text('forestServiceRoad', 'Forest Service Road', 20)}

            {/* Where the structure is held */}
            {fields.orgUnitSelect(
              'orgUnit',
              'Forest District',
              'Any district',
              codeTables.forestDistricts,
            )}
            {fields.orgUnitSelect(
              'managementOrgUnit',
              'Management Area',
              criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
              codeTables.managementAreas,
              referenceData.isLoading || managementAreasLoading,
            )}
            {fields.orgUnitSelect(
              'businessAreaOrgUnit',
              'BCTS Business Area',
              'Any business area',
              codeTables.businessAreas,
            )}
            <div className="report-criteria__paired">
              {fields.text('forestFileId', 'Project File ID#', 10)}
              {fields.text('roadSectionId', 'Br.', 30)}
            </div>

            {/* Who maintains it, and the two switches — a cell each */}
            <MaintainerFields
              id={ID}
              clientNumber={criteria.clientNumber}
              clientLocationCode={criteria.clientLocationCode}
              maintainerLabel={criteria.maintainerLabel}
              onChange={updateCriteria}
            />
            {fields.toggle('downrated', 'Downrated Structure?')}
            {fields.toggle('mostRecentInspections', 'Most Recent Inspections Only?')}
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default RepairsSummaryPage;
