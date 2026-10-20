import MaintainerFields from './MaintainerFields';

import type { criteriaFields } from './criteriaFields';
import type { StructureInventoryCommonCriteria } from './types';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';

/** The boxes a report form builds, bound to its criteria. */
export type CriteriaFields<C extends object> = ReturnType<typeof criteriaFields<C>>;

/** The lists the Structure Inventory reports' shared boxes draw on. */
export type CommonCodeTables = {
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  portableSuperstructureStatusCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
};

/**
 * The groups of boxes the Structure Inventory reports' forms share, each in legacy's order and
 * with legacy's labels. A form places them where its own layout wants them.
 */

/** The site: its status and type, its road, and how far along it. */
export const siteGroup = <C extends StructureInventoryCommonCriteria>(
  { codeSelect, text, range }: CriteriaFields<C>,
  codeTables: CommonCodeTables,
) => (
  <>
    {codeSelect('siteStatusCode', 'Site Status', 'Any status', codeTables.siteStatusCodes)}
    {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}
    <div className="report-criteria__paired">
      {text('forestFileId', 'Project File ID#', 10)}
      {text('roadSectionId', 'Br.', 30)}
    </div>
    {range('kiloStart', 'kiloEnd', 'Kilometres', 'kilometres')}
  </>
);

/** Special Access Requirements — a select on the site. */
export const specialAccess = <C extends StructureInventoryCommonCriteria>(
  { codeSelect }: CriteriaFields<C>,
  codeTables: CommonCodeTables,
) =>
  codeSelect(
    'specialAccessCode',
    'Special Access Requirements',
    'Any requirement',
    codeTables.specialAccessCodes,
  );

/** Portable Superstructure Status — a select on the bridge. */
export const portableStatus = <C extends StructureInventoryCommonCriteria>(
  { codeSelect }: CriteriaFields<C>,
  codeTables: CommonCodeTables,
) =>
  codeSelect(
    'portableSuperstructureStatusCode',
    'Portable Superstructure Status',
    'Any status',
    codeTables.portableSuperstructureStatusCodes,
  );

/** The two one-way switches: Downrated Structure? and Capital Road. */
export const toggles = <C extends StructureInventoryCommonCriteria>({
  toggle,
}: CriteriaFields<C>) => (
  <div className="report-criteria__toggles">
    {toggle('downrated', 'Downrated Structure?')}
    {toggle('capitalRoad', 'Capital Road')}
  </div>
);

/** Who the structure belongs to: its district and area, its business area, its maintainer. */
export const ownerGroup = <C extends StructureInventoryCommonCriteria>(
  { orgUnitSelect }: CriteriaFields<C>,
  codeTables: CommonCodeTables,
  options: {
    id: string;
    criteria: C;
    onChange: <K extends keyof C>(field: K, value: C[K]) => void;
    codeTablesLoading: boolean;
    managementAreasLoading: boolean;
  },
) => (
  <>
    {orgUnitSelect('orgUnit', 'Forest District', 'Any district', codeTables.forestDistricts)}
    {orgUnitSelect(
      'managementOrgUnit',
      'Management Area',
      options.criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
      codeTables.managementAreas,
      options.codeTablesLoading || options.managementAreasLoading,
    )}
    {orgUnitSelect(
      'businessAreaOrgUnit',
      'BCTS Business Area',
      'Any business area',
      codeTables.businessAreas,
    )}
    <MaintainerFields
      id={options.id}
      clientNumber={options.criteria.clientNumber}
      clientLocationCode={options.criteria.clientLocationCode}
      maintainerLabel={options.criteria.maintainerLabel}
      onChange={(field, value) => options.onChange(field, value as C[typeof field])}
    />
  </>
);

/** Legacy's "Replacement Date" fieldset: when the structure is due for restriction or replacement. */
export const replacementGroup = <C extends StructureInventoryCommonCriteria>({
  range,
}: CriteriaFields<C>) => (
  <>
    {range(
      'loadRestrictionYearStart',
      'loadRestrictionYearEnd',
      'Estimated Load Restriction',
      'year',
    )}
    {range('replacementYearStart', 'replacementYearEnd', 'Estimated Replacement', 'year')}
  </>
);
