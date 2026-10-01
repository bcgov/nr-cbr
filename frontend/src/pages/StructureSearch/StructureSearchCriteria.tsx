import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, Select, SelectItem, TextInput, Toggle } from '@carbon/react';

import ClientCombo from '@/components/core/ClientCombo';

import { criteriaErrors } from './validation';

import type { CodeOption, OrgUnitOption, StructureSearchCriteria as Criteria } from './types';
import type { ClientSuggestion } from '@/types/client';
import type { FC, SubmitEventHandler } from 'react';

import { useClientLocations } from '@/hooks/useClientSearch';
import { clientLabel, locationLabel } from '@/utils/clientSearch';

export type CodeTables = {
  structureTypeClassCodes: CodeOption[];
  superstructureTypeCodes: CodeOption[];
  structureCurbTypeCodes: CodeOption[];
  culvertTypeCodes: CodeOption[];
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  specialEquipmentCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
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
  onSearch: () => void;
  onReset: () => void;
  /** Bumped on Reset, to remount the maintainer lookup — see the same prop on Site Search. */
  resetToken: number;
};

/** "PB - Permanent Bridge"; a missing half degrades to the other rather than "PB - undefined". */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

/**
 * The Structure Search criteria form.
 *
 * <p>Every criterion from the legacy `structure_search.jsp` with its legacy label, except User
 * Kilometres (see `StructureSearchCriteria`) and the three maintainer boxes, which are one lookup
 * plus a location here, as on Site Search. Laid out in the same four-column grid as Site Search:
 * the structure's own criteria first, then the site's, then the replacement dates — the groups
 * legacy's form has, in its order.
 */
const StructureSearchCriteriaForm: FC<Props> = ({
  criteria,
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onSearch,
  onReset,
  resetToken,
}) => {
  const errors = criteriaErrors(criteria);
  const hasErrors = Object.keys(errors).length > 0;

  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    // The messages sit beside their boxes; the backend refuses the same values with a 400.
    if (hasErrors) {
      return;
    }
    onSearch();
  };

  const locations = useClientLocations(criteria.clientNumber);

  /** A maintainer was picked: it replaces any typed name, and clears the old location. */
  const selectMaintainer = (client: ClientSuggestion | null) => {
    onChange('clientNumber', client?.clientNumber ?? '');
    onChange('maintainerLabel', client ? clientLabel(client) : '');
    onChange('primaryUserName', '');
    onChange('clientLocationCode', '');
  };

  /** Typed text that is not a pick still searches, on the client name. */
  const typeMaintainer = (term: string) => {
    onChange('primaryUserName', term);
    onChange('clientNumber', '');
    onChange('clientLocationCode', '');
    onChange('maintainerLabel', '');
  };

  const text = (
    field: keyof Criteria,
    labelText: string,
    maxLength: number,
    extra?: { placeholder?: string; inputMode?: 'decimal' | 'numeric'; hideLabel?: boolean },
  ) => (
    <TextInput
      id={`structure-search-${field}`}
      data-testid={`structure-search-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={String(criteria[field])}
      invalid={field in errors}
      invalidText={errors[field]}
      onChange={(event) => onChange(field, event.target.value as never)}
      {...extra}
    />
  );

  const codeSelect = (
    field: keyof Criteria,
    labelText: string,
    anyText: string,
    options: CodeOption[],
  ) => (
    <Select
      id={`structure-search-${field}`}
      data-testid={`structure-search-${field}`}
      labelText={labelText}
      disabled={codeTablesLoading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.code}
          value={option.code}
          text={label(option.code, option.description)}
        />
      ))}
    </Select>
  );

  const orgUnitSelect = (
    field: keyof Criteria,
    labelText: string,
    anyText: string,
    options: OrgUnitOption[],
    loading = codeTablesLoading,
  ) => (
    <Select
      id={`structure-search-${field}`}
      data-testid={`structure-search-${field}`}
      labelText={labelText}
      disabled={loading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.orgUnitNo}
          value={option.orgUnitNo}
          text={label(option.orgUnitCode, option.orgUnitName)}
        />
      ))}
    </Select>
  );

  /**
   * A "from – to" pair, as on Site Search: the legend names the pair and the boxes carry hidden
   * labels and a placeholder. Kilometres hold `999999.99` at most; a year is four digits.
   */
  const range = (
    from: keyof Criteria,
    to: keyof Criteria,
    labelText: string,
    kind: 'kilometres' | 'year',
  ) => {
    const box = (field: keyof Criteria, end: 'From' | 'To') =>
      kind === 'year'
        ? text(field, `${labelText} ${end}`, 4, {
            inputMode: 'numeric',
            hideLabel: true,
            placeholder: `${end} yyyy`,
          })
        : text(field, `${labelText} ${end}`, 9, {
            inputMode: 'decimal',
            hideLabel: true,
            placeholder: end,
          });
    return (
      <fieldset className="structure-search__range">
        <legend className="cds--label">{labelText}</legend>
        <div className="structure-search__range-inputs">
          {box(from, 'From')}
          <span aria-hidden="true" className="structure-search__range-dash">
            –
          </span>
          {box(to, 'To')}
        </div>
      </fieldset>
    );
  };

  const toggle = (field: keyof Criteria, labelText: string) => (
    <Toggle
      id={`structure-search-${field}`}
      data-testid={`structure-search-${field}`}
      labelText={labelText}
      size="sm"
      toggled={Boolean(criteria[field])}
      onToggle={(checked) => onChange(field, checked as never)}
    />
  );

  return (
    <form onSubmit={submit} data-testid="structure-search-form" className="structure-search__form">
      <div className="structure-search__criteria">
        {/* The structure */}
        {text('structureName', 'Structure #', 20)}
        {codeSelect(
          'structureTypeClassCode',
          'Type/Class',
          'Any type/class',
          codeTables.structureTypeClassCodes,
        )}
        {codeSelect(
          'superstructureTypeCode',
          'Superstructure Type',
          'Any superstructure type',
          codeTables.superstructureTypeCodes,
        )}
        {codeSelect(
          'structureCurbTypeCode',
          'Curb Type',
          'Any curb type',
          codeTables.structureCurbTypeCodes,
        )}

        {codeSelect(
          'culvertTypeCode',
          'Culvert Type',
          'Any culvert type',
          codeTables.culvertTypeCodes,
        )}
        {range('yearBuiltStart', 'yearBuiltEnd', 'Year Superstructure Installed', 'year')}
        {codeSelect(
          'specialEquipmentCode',
          'Special Equipment Requirements',
          'Any requirement',
          codeTables.specialEquipmentCodes,
        )}
        <div className="structure-search__toggles">
          {toggle('downrated', 'Downrated Structure?')}
          {toggle('portableStructure', 'Portable Structure?')}
        </div>

        {/* The site it stands on */}
        {text('siteId', 'Site #', 14)}
        {codeSelect('siteStatusCode', 'Site Status', 'Any status', codeTables.siteStatusCodes)}
        <div className="structure-search__paired">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>
        {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}

        {text('forestServiceRoad', 'Forest Service Road', 20)}
        {range('kiloStart', 'kiloEnd', 'Kilometres', 'kilometres')}
        {text('crossingName', 'Crossing Name', 20)}
        {codeSelect(
          'specialAccessCode',
          'Special Access Requirements',
          'Any requirement',
          codeTables.specialAccessCodes,
        )}

        {orgUnitSelect('orgUnit', 'Forest District', 'Any district', codeTables.forestDistricts)}
        {orgUnitSelect(
          'managementOrgUnit',
          'Management Area',
          criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
          codeTables.managementAreas,
          codeTablesLoading || managementAreasLoading,
        )}
        <ClientCombo
          key={resetToken}
          id="structure-search-maintainer"
          titleText="Designated Maintainer"
          selectedLabel={criteria.maintainerLabel}
          onSelect={selectMaintainer}
          onTermChange={typeMaintainer}
        />
        <Select
          id="structure-search-clientLocationCode"
          data-testid="structure-search-clientLocationCode"
          labelText="Maintainer Location"
          disabled={criteria.clientNumber === '' || locations.isFetching}
          value={criteria.clientLocationCode}
          onChange={(event) => onChange('clientLocationCode', event.target.value)}
        >
          <SelectItem
            value=""
            text={criteria.clientNumber === '' ? 'Pick a maintainer first' : 'Any location'}
          />
          {(locations.data ?? []).map((location) => (
            <SelectItem
              key={location.clientLocnCode}
              value={location.clientLocnCode ?? ''}
              text={locationLabel(location)}
            />
          ))}
        </Select>

        {/* Legacy's "Replacement Date" fieldset */}
        {range(
          'loadRestrictionYearStart',
          'loadRestrictionYearEnd',
          'Estimated Load Restriction',
          'year',
        )}
        {range('replacementYearStart', 'replacementYearEnd', 'Estimated Replacement', 'year')}
        {range('closureYearStart', 'closureYearEnd', 'Estimated Closure', 'year')}
        <div className="structure-search__toggles">
          {toggle('incomplete', 'Incomplete Data?')}
          {/* Off by default, which leaves archived structures out — legacy's rule. */}
          {toggle('includeArchived', 'Include Archived Structure?')}
        </div>

        <div className="structure-search__actions">
          <Button kind="ghost" type="button" onClick={onReset} data-testid="structure-search-reset">
            Clear
          </Button>
          <Button type="submit" renderIcon={SearchIcon} data-testid="structure-search-submit">
            Search
          </Button>
        </div>
      </div>
    </form>
  );
};

export default StructureSearchCriteriaForm;
