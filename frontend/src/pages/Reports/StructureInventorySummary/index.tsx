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
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import { EMPTY_CRITERIA, FORMATS } from './types';
import { criteriaErrors } from './validation';

import type { FC } from 'react';

import {
  useCulvertMaterialCodes,
  useCulvertTypeCodes,
  useDeckTypeCodes,
  useStructureInventorySummaryReferenceDataState,
  useStructureTypeClassCodes,
  useSuperstructureTypeCodes,
} from '@/hooks/useConfiguration';
import { useStructureInventorySummaryReport } from '@/hooks/useReports';

/** Where the page keeps its criteria for the tab. */
export const STRUCTURE_INVENTORY_SUMMARY_STATE_KEY = 'cbr.report.structureInventorySummary';

const ID = 'structure-inventory-summary';

/**
 * Structure Inventory – Summary — the first of the Reports (legacy CBR008).
 *
 * <p>Legacy equivalent: `showStructureInventorySummaryReport.do` →
 * `structure_inventory_summary.jsp`, gated on `/showStructureInventorySummaryReport`, which every
 * role that can read holds. One row per structure, filtered by what it is built of, where it
 * stands and who maintains it.
 *
 * <p>Laid out as Structure Search is, in its four-column grid: the structure's own criteria first —
 * its construction filters, which only this report has — then its site's, then who holds it, then
 * legacy's "Replacement Date" pair. Labels are legacy's.
 *
 * <p>The criteria are kept for the browser tab, as on the search pages; Clear forgets them.
 * Legacy's Save As / Delete Report Favourite are not carried over for now. Download PDF and
 * Download CSV check the criteria, then fetch the report from the server and save it.
 */
const StructureInventorySummaryPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    STRUCTURE_INVENTORY_SUMMARY_STATE_KEY,
    EMPTY_CRITERIA,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const structureTypeClassCodes = useStructureTypeClassCodes();
  const superstructureTypeCodes = useSuperstructureTypeCodes();
  const deckTypeCodes = useDeckTypeCodes();
  const culvertTypeCodes = useCulvertTypeCodes();
  const culvertMaterialCodes = useCulvertMaterialCodes();
  const referenceData = useStructureInventorySummaryReferenceDataState();
  const errors = useMemo(() => criteriaErrors(criteria), [criteria]);

  const { run, running } = useReportDownload({
    report: useStructureInventorySummaryReport(),
    criteria,
    filename: 'StructureInventorySummaryReport',
  });

  return (
    <ReportPageLayout
      id={ID}
      title="Structure Inventory – Summary"
      subtitle="One line per structure: filter by what it is built of, where it stands and who maintains it."
      codeTablesFailed={referenceData.isError}
    >
      <ReportCriteriaForm
        key={resetToken}
        id={ID}
        criteria={criteria}
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
            {/* The structure */}
            {fields.codeSelect(
              'structureTypeClassCode',
              'Type/Class',
              'Any type/class',
              structureTypeClassCodes.data ?? [],
            )}
            {fields.codeSelect(
              'superstructureTypeCode',
              'Superstructure Type',
              'Any superstructure type',
              superstructureTypeCodes.data ?? [],
            )}
            {fields.codeSelect(
              'deckTypeCode',
              'Deck Type',
              'Any deck type',
              deckTypeCodes.data ?? [],
            )}
            {portableStatus(fields, codeTables)}

            {fields.codeSelect(
              'culvertTypeCode',
              'Culvert Type',
              'Any culvert type',
              culvertTypeCodes.data ?? [],
            )}
            {fields.codeSelect(
              'culvertMaterialCode',
              'Culvert Material',
              'Any culvert material',
              culvertMaterialCodes.data ?? [],
            )}
            {fields.range(
              'openingWidthMin',
              'openingWidthMax',
              'Opening Width (mm)',
              'millimetres',
            )}
            {fields.range(
              'yearBuiltStart',
              'yearBuiltEnd',
              'Year Superstructure Installed',
              'year',
            )}

            {/* The site it stands on */}
            {siteGroup(fields, codeTables)}
            {specialAccess(fields, codeTables)}
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

export default StructureInventorySummaryPage;
