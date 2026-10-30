import { useMemo } from 'react';

import {
  ownerGroup,
  portableStatus,
  replacementGroup,
  siteGroup,
  specialAccess,
  toggles,
} from '../criteriaGroups';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { SORT_OPTIONS } from '../types';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import { EMPTY_CRITERIA, FORMATS } from './types';
import { criteriaErrors } from './validation';

import type { FC } from 'react';

import { useStructureInventoryDetailedReferenceDataState } from '@/hooks/useConfiguration';
import { useStructureInventoryDetailedReport } from '@/hooks/useReports';

/** Where the page keeps its criteria for the tab. */
export const STRUCTURE_INVENTORY_DETAILED_STATE_KEY = 'cbr.report.structureInventoryDetailed';

const ID = 'structure-inventory-detailed';

/**
 * Structure Inventory – Detailed — a full record per structure (legacy CBR007).
 *
 * <p>Legacy equivalent: `showStructureInventoryDetailedReport.do` →
 * `structure_inventory_detailed.jsp`, gated on `/showStructureInventoryDetailedReport`, which every
 * role that can read holds. Where the Summary is a line per structure, this is a block of labelled
 * fields per structure, and it also comes as a KMZ for a map — the KMZ the inspection reports
 * offer too, for the structures they list.
 *
 * <p>The Summary's form without its construction filters, in the same grid: the site first, then
 * the structure, then who holds it, then legacy's "Replacement Date" pair. Labels are legacy's —
 * "Year Built" here, where the Summary says Year Superstructure Installed.
 *
 * <p>The criteria are kept for the tab, apart from the Summary's; Clear forgets them. Each Download
 * checks the criteria, then fetches the report from the server and saves it: the PDF in legacy's
 * layout, the CSV as legacy's fuller export, the KMZ as a placemark per structure for a map.
 */
const StructureInventoryDetailedPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    STRUCTURE_INVENTORY_DETAILED_STATE_KEY,
    EMPTY_CRITERIA,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const referenceData = useStructureInventoryDetailedReferenceDataState();
  const errors = useMemo(() => criteriaErrors(criteria), [criteria]);

  const { run, running } = useReportDownload({
    report: useStructureInventoryDetailedReport(),
    criteria,
    filename: 'StructureInventoryDetailReport',
  });

  return (
    <ReportPageLayout
      id={ID}
      title="Structure Inventory – Detailed"
      subtitle="A full record per structure: filter by where it stands, who maintains it and when it is due for replacement."
      codeTablesFailed={referenceData.isError}
    >
      <ReportCriteriaForm
        key={resetToken}
        id={ID}
        criteria={criteria}
        sortOptions={SORT_OPTIONS}
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
            {/* The site */}
            {siteGroup(fields, codeTables)}

            {/* The structure */}
            {specialAccess(fields, codeTables)}
            {portableStatus(fields, codeTables)}
            {fields.range('yearBuiltStart', 'yearBuiltEnd', 'Year Built', 'year')}
            {toggles(fields)}

            {/* Who it belongs to, then legacy's "Replacement Date" fieldset */}
            {ownerGroup(fields, codeTables, {
              id: ID,
              criteria,
              onChange: updateCriteria,
              codeTablesLoading: referenceData.isLoading,
              managementAreasLoading,
            })}
            {replacementGroup(fields)}
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default StructureInventoryDetailedPage;
