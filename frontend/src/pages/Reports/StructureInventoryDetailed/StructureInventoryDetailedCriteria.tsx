import { criteriaFields } from '../criteriaFields';
import MaintainerFields from '../MaintainerFields';
import { ReportButtons, SortBy } from '../ReportOutput';

import {
  FORMATS,
  type ReportFormat,
  type StructureInventoryDetailedCriteria as Criteria,
} from './types';
import { criteriaErrors } from './validation';

import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { FC, SubmitEventHandler } from 'react';

import { useFieldErrors } from '@/hooks/useFieldErrors';

export type CodeTables = {
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  portableSuperstructureStatusCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
};

type Props = {
  criteria: Criteria;
  codeTables: CodeTables;
  /** Disables the selects while their contents are still unknown. */
  codeTablesLoading?: boolean;
  /** Management areas alone — the one list that reloads mid-form, when the district changes. */
  managementAreasLoading?: boolean;
  onChange: <K extends keyof Criteria>(field: K, value: Criteria[K]) => void;
  /** Runs the report as this format, once the criteria pass. */
  onRun: (format: ReportFormat) => void;
  /** The format being produced, while one is: the buttons wait, and that one says so. */
  running?: ReportFormat | null;
  onReset: () => void;
};

const ID = 'structure-inventory-detailed';

/**
 * The Structure Inventory – Detailed criteria form — legacy's `structure_inventory_detailed.jsp`.
 *
 * <p>The Summary's form without its construction filters, in the same grid: the site first, then
 * the structure, then who holds it, then legacy's "Replacement Date" pair, and the order below.
 * Labels are legacy's — "Year Built" here, where the Summary says Year Superstructure Installed.
 * A Download button per format, KMZ among them, as legacy offers it on this report alone.
 */
const StructureInventoryDetailedCriteriaForm: FC<Props> = ({
  criteria,
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onRun,
  onReset,
  running = null,
}) => {
  const { shown, leave, submit: check } = useFieldErrors(criteriaErrors(criteria));
  const { text, codeSelect, orgUnitSelect, range, toggle } = criteriaFields({
    id: ID,
    criteria,
    shown,
    leave,
    onChange,
    codeTablesLoading,
  });

  const run = (format: ReportFormat) => {
    if (check()) {
      onRun(format);
    }
  };

  /** Enter in a box runs the PDF, the primary button — unless a report is already on its way. */
  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    if (running === null) {
      run('PDF');
    }
  };

  return (
    <form onSubmit={submit} data-testid={`${ID}-form`} className="report-criteria__form">
      <div className="report-criteria__grid">
        {/* The site */}
        {codeSelect('siteStatusCode', 'Site Status', 'Any status', codeTables.siteStatusCodes)}
        {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}
        <div className="report-criteria__paired">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>
        {range('kiloStart', 'kiloEnd', 'Kilometres', 'kilometres')}

        {/* The structure */}
        {codeSelect(
          'specialAccessCode',
          'Special Access Requirements',
          'Any requirement',
          codeTables.specialAccessCodes,
        )}
        {codeSelect(
          'portableSuperstructureStatusCode',
          'Portable Superstructure Status',
          'Any status',
          codeTables.portableSuperstructureStatusCodes,
        )}
        {range('yearBuiltStart', 'yearBuiltEnd', 'Year Built', 'year')}
        <div className="report-criteria__toggles">
          {toggle('downrated', 'Downrated Structure?')}
          {toggle('capitalRoad', 'Capital Road')}
        </div>

        {/* Who it belongs to */}
        {orgUnitSelect('orgUnit', 'Forest District', 'Any district', codeTables.forestDistricts)}
        {orgUnitSelect(
          'managementOrgUnit',
          'Management Area',
          criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
          codeTables.managementAreas,
          codeTablesLoading || managementAreasLoading,
        )}
        {orgUnitSelect(
          'businessAreaOrgUnit',
          'BCTS Business Area',
          'Any business area',
          codeTables.businessAreas,
        )}
        <MaintainerFields
          id={ID}
          clientNumber={criteria.clientNumber}
          clientLocationCode={criteria.clientLocationCode}
          maintainerLabel={criteria.maintainerLabel}
          onChange={onChange}
        />

        {/* Legacy's "Replacement Date" fieldset */}
        {range(
          'loadRestrictionYearStart',
          'loadRestrictionYearEnd',
          'Estimated Load Restriction',
          'year',
        )}
        {range('replacementYearStart', 'replacementYearEnd', 'Estimated Replacement', 'year')}
      </div>

      <SortBy id={ID} sortBy={criteria.sortBy} onChange={(sortBy) => onChange('sortBy', sortBy)} />
      <ReportButtons id={ID} formats={FORMATS} running={running} onRun={run} onReset={onReset} />
    </form>
  );
};

export default StructureInventoryDetailedCriteriaForm;
