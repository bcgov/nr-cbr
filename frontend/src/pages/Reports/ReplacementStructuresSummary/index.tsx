import { useMemo } from 'react';

import { areaGroup, replacementGroup } from '../criteriaGroups';
import MaintainerFields from '../MaintainerFields';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import { EMPTY_CRITERIA, FORMATS } from './types';
import { criteriaErrors } from './validation';

import type { FC } from 'react';

import { useReplacementStructuresSummaryReferenceDataState } from '@/hooks/useConfiguration';
import { useReplacementStructuresSummaryReport } from '@/hooks/useReports';

/** Where the page keeps its criteria for the tab. */
export const REPLACEMENT_STRUCTURES_SUMMARY_STATE_KEY = 'cbr.report.replacementStructuresSummary';

const ID = 'replacement-structures-summary';

/**
 * Replacement Structures Summary (legacy CBR009): the structures due for a load restriction or
 * replacement, filtered by when, by where they are held and by who maintains them.
 *
 * <p>Legacy equivalent: `showReplacementStructureSummaryReport.do` →
 * `replacement_structure_summary.jsp`, gated on `/showReplacementStructureSummaryReport`, which
 * every role that can read holds. Laid out in the reports' four-column grid, in legacy's order:
 * where the structure is held; its site; legacy's two estimated dates and Capital Road; its
 * maintainer. Labels are legacy's, the dates' as on the Structure Inventory reports.
 *
 * <p>The criteria are kept for the browser tab, as on the other reports; Clear goes back to the
 * defaults. Download PDF and Download CSV check the criteria, then fetch the report from the
 * server and save it.
 */
const ReplacementStructuresSummaryPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    REPLACEMENT_STRUCTURES_SUMMARY_STATE_KEY,
    EMPTY_CRITERIA,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const referenceData = useReplacementStructuresSummaryReferenceDataState();
  const errors = useMemo(() => criteriaErrors(criteria), [criteria]);

  const { run, running } = useReportDownload({
    report: useReplacementStructuresSummaryReport(),
    criteria,
    filename: 'ReplacementStructuresSummaryReport',
  });

  return (
    <ReportPageLayout
      id={ID}
      title="Replacement Structures Summary"
      subtitle="The structures due for a load restriction or replacement: filter by when, where they are held and who maintains them."
      codeTablesFailed={referenceData.isError}
    >
      <ReportCriteriaForm
        key={resetToken}
        id={ID}
        criteria={criteria}
        sortOptions={[]}
        errors={errors}
        formats={FORMATS}
        codeTablesLoading={referenceData.isLoading}
        onChange={updateCriteria}
        onRun={run}
        running={running}
        onReset={reset}
      >
        {(fields) => (
          <>
            {/* Where the structure is held */}
            {areaGroup(fields, codeTables, {
              criteria,
              loading: referenceData.isLoading || managementAreasLoading,
            })}

            {/* Its site */}
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

            {/* When it is due, and its road */}
            {replacementGroup(fields)}
            {fields.toggle('capitalRoad', 'Capital Road')}

            {/* Who maintains it */}
            <MaintainerFields
              id={ID}
              clientNumber={criteria.clientNumber}
              clientLocationCode={criteria.clientLocationCode}
              maintainerLabel={criteria.maintainerLabel}
              onChange={updateCriteria}
            />
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default ReplacementStructuresSummaryPage;
