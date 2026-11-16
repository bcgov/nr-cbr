import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, Select, SelectItem, TextInput, Toggle } from '@carbon/react';

import ClientCombo from '@/components/core/ClientCombo';
import MultiCodeSelect from '@/components/core/MultiCodeSelect';

import { criteriaErrors } from './validation';

import type { CodeOption, OrgUnitOption, StructureSearchCriteria as Criteria } from './types';
import type { ClientSuggestion } from '@/types/client';
import type { FC, SubmitEventHandler } from 'react';

import {
  codeOptions,
  orgUnitOptions,
  type MultiCodeOption,
  type MultiFields,
  withIsNull,
} from '@/components/core/MultiCodeSelect/options';
import { useClientLocations } from '@/hooks/useClientSearch';
import { MIN_CLIENT_TERM_LENGTH, clientLabel, locationLabel } from '@/utils/clientSearch';

export type CodeTables = {
  structureTypeClassCodes: CodeOption[];
  superstructureTypeCodes: CodeOption[];
  structureCurbTypeCodes: CodeOption[];
  culvertTypeCodes: CodeOption[];
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  specialEquipmentCodes: CodeOption[];
  builtByCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
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
 * legacy's form has, in its order — and last its yes/no filters, side by side on a row of their own.
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

  /**
   * A filter that takes several values — the dropdowns highlighted in the UAT "CBR Pick list".
   * Nothing ticked is any. The long org-unit lists get a type-to-filter box.
   */
  const multiSelect = (
    field: MultiFields<Criteria>,
    labelText: string,
    anyText: string,
    options: readonly MultiCodeOption[],
    { filterable = false, disabled = codeTablesLoading } = {},
  ) => (
    <MultiCodeSelect
      id={`structure-search-${field}`}
      titleText={labelText}
      placeholder={anyText}
      options={options}
      selected={criteria[field]}
      onChange={(values) => onChange(field, values)}
      filterable={filterable}
      disabled={disabled}
    />
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
        {multiSelect(
          'structureTypeClassCode',
          'Type/Class',
          'Any type/class',
          codeOptions(codeTables.structureTypeClassCodes),
        )}
        {multiSelect(
          'superstructureTypeCode',
          'Superstructure Type',
          'Any superstructure type',
          codeOptions(codeTables.superstructureTypeCodes),
        )}
        {codeSelect(
          'structureCurbTypeCode',
          'Curb Type',
          'Any curb type',
          codeTables.structureCurbTypeCodes,
        )}

        {multiSelect(
          'culvertTypeCode',
          'Culvert Type',
          'Any culvert type',
          codeOptions(codeTables.culvertTypeCodes),
        )}
        {range('yearBuiltStart', 'yearBuiltEnd', 'Year Superstructure Installed', 'year')}
        {codeSelect(
          'specialEquipmentCode',
          'Special Equipment Requirements',
          'Any requirement',
          codeTables.specialEquipmentCodes,
        )}
        {codeSelect('builtByCode', 'Built For', 'Any', codeTables.builtByCodes)}

        {/* The site it stands on */}
        {text('siteId', 'Site #', 14)}
        {multiSelect(
          'siteStatusCode',
          'Site Status',
          'Any status',
          codeOptions(codeTables.siteStatusCodes),
        )}
        <div className="structure-search__paired">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>
        {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}

        {text('forestServiceRoad', 'Forest Service Road', 20)}
        {range('kiloStart', 'kiloEnd', 'Kilometres', 'kilometres')}
        {text('crossingName', 'Crossing Name', 20)}
        {multiSelect(
          'specialAccessCode',
          'Special Access Requirements',
          'Any requirement',
          codeOptions(codeTables.specialAccessCodes),
        )}

        {multiSelect(
          'orgUnit',
          'Forest District',
          'Any district',
          orgUnitOptions(codeTables.forestDistricts),
          { filterable: true },
        )}
        {multiSelect(
          'managementOrgUnit',
          'Management Area',
          criteria.orgUnit.length === 0 ? 'Select a forest district first' : 'Any management area',
          orgUnitOptions(codeTables.managementAreas),
          {
            filterable: true,
            disabled: codeTablesLoading || managementAreasLoading || criteria.orgUnit.length === 0,
          },
        )}
        {multiSelect(
          'businessAreaOrgUnit',
          'BCTS Business Area',
          'Any business area',
          // "Is null" first: structures held by no business area (UAT pick list).
          withIsNull(orgUnitOptions(codeTables.businessAreas)),
          { filterable: true },
        )}
        <ClientCombo
          key={resetToken}
          id="structure-search-maintainer"
          titleText="Designated Maintainer"
          // One line, so this row is no taller than the others.
          helperText={`Name, city or client # (min. ${MIN_CLIENT_TERM_LENGTH})`}
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

        {/* The yes/no filters, together on the last row, side by side. */}
        <div className="structure-search__toggles structure-search__toggles--row">
          {toggle('downrated', 'Downrated Structure?')}
          {toggle('portableStructure', 'Portable Structure?')}
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
