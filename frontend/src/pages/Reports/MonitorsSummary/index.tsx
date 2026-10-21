import MaintainerFields from '../MaintainerFields';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import { EMPTY_CRITERIA, FORMATS, SORT_OPTIONS } from './types';

import type { FC } from 'react';

import {
  useMonitoringStatusCodes,
  useMonitorsSummaryReferenceDataState,
} from '@/hooks/useConfiguration';
import { useMonitorsSummaryReport } from '@/hooks/useReports';

/** Where the page keeps its criteria for the tab. */
export const MONITORS_SUMMARY_STATE_KEY = 'cbr.report.monitorsSummary';

const ID = 'monitors-summary';

/** Nothing here can be malformed: every box is a code, free text or a switch. */
const NO_ERRORS = {};

/**
 * Monitors Summary — the monitoring items recorded on inspections, by structure (legacy CBR011).
 *
 * <p>Legacy equivalent: `showMonitorSummaryReport.do` → `monitor_summary.jsp`, gated on
 * `/showMonitorSummaryReport`, which every role that can read holds. Laid out as three rows of the
 * reports' four-column grid: the structure, its site and the items' status; where it is held and
 * its road; then its maintainer and Most Recent Inspections Only, a cell each. Labels are legacy's.
 *
 * <p>The criteria are kept for the browser tab, as on the other reports; Clear forgets them.
 * Download PDF and Download CSV fetch the report from the server and save it.
 */
const MonitorsSummaryPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    MONITORS_SUMMARY_STATE_KEY,
    EMPTY_CRITERIA,
  );
  // No district: this report has no Management Area to look up.
  const { codeTables } = useCommonCodeTables('');
  const monitoringStatusCodes = useMonitoringStatusCodes();
  const referenceData = useMonitorsSummaryReferenceDataState();

  const { run, running } = useReportDownload({
    report: useMonitorsSummaryReport(),
    criteria,
    filename: 'MonitorsSummaryReport',
  });

  return (
    <ReportPageLayout
      id={ID}
      title="Monitors Summary"
      subtitle="The monitoring items found on inspections, by structure: filter by their status, and where the structure stands."
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
            {/* The structure, its site and its items */}
            {fields.text('structureName', 'Structure #', 14)}
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
            {fields.codeSelect(
              'monitoringStatusCode',
              'Monitoring Status',
              'Any status',
              monitoringStatusCodes.data ?? [],
            )}

            {/* Where it is held, and its road */}
            {fields.orgUnitSelect(
              'orgUnit',
              'Forest District',
              'Any district',
              codeTables.forestDistricts,
            )}
            {fields.orgUnitSelect(
              'businessAreaOrgUnit',
              'BCTS Business Area',
              'Any business area',
              codeTables.businessAreas,
            )}
            {fields.text('forestServiceRoad', 'Forest Service Road', 20)}
            <div className="report-criteria__paired">
              {fields.text('forestFileId', 'Project File ID#', 10)}
              {fields.text('roadSectionId', 'Br.', 30)}
            </div>

            {/* Who maintains it, and the switch */}
            <MaintainerFields
              id={ID}
              clientNumber={criteria.clientNumber}
              clientLocationCode={criteria.clientLocationCode}
              maintainerLabel={criteria.maintainerLabel}
              onChange={updateCriteria}
            />
            {fields.toggle('mostRecentInspections', 'Most Recent Inspections Only?')}
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default MonitorsSummaryPage;
