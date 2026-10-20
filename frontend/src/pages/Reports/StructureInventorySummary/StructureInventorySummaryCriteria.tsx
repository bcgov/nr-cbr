import { Download } from '@carbon/icons-react';
import {
  Button,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextInput,
  Toggle,
} from '@carbon/react';

import ClientCombo from '@/components/core/ClientCombo';

import {
  SORT_OPTIONS,
  type ReportFormat,
  type StructureInventorySort,
  type StructureInventorySummaryCriteria as Criteria,
} from './types';
import { criteriaErrors } from './validation';

import type { ClientSuggestion } from '@/types/client';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { FC, SubmitEventHandler } from 'react';

import { useClientLocations } from '@/hooks/useClientSearch';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { clientLabel, locationLabel } from '@/utils/clientSearch';

export type CodeTables = {
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  structureTypeClassCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  portableSuperstructureStatusCodes: CodeOption[];
  culvertTypeCodes: CodeOption[];
  superstructureTypeCodes: CodeOption[];
  culvertMaterialCodes: CodeOption[];
  deckTypeCodes: CodeOption[];
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
  onReset: () => void;
  /** Bumped on Clear, to remount the maintainer lookup and forget which boxes were visited. */
  resetToken: number;
};

/** "PB - Permanent Bridge"; a missing half degrades to the other rather than "PB - undefined". */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

const ID = 'structure-inventory-summary';

/**
 * The Structure Inventory – Summary criteria form — legacy's `structure_inventory_summary.jsp`.
 *
 * <p>Laid out as Structure Search is, in the same four-column grid: the structure's own criteria
 * first, then its site's, then who holds it, then legacy's "Replacement Date" pair — and below them
 * the order. Labels are legacy's. A button per format, rather than legacy's format radio and one
 * Run Report: the choice and the action are one click.
 */
const StructureInventorySummaryCriteriaForm: FC<Props> = ({
  criteria,
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onRun,
  onReset,
  resetToken,
}) => {
  const { shown, leave, submit: check } = useFieldErrors(criteriaErrors(criteria));

  const run = (format: ReportFormat) => {
    if (check()) {
      onRun(format);
    }
  };

  /** Enter in a box runs the PDF, the first button. */
  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    run('PDF');
  };

  const locations = useClientLocations(criteria.clientNumber);

  /** A maintainer was picked, or cleared: either way the old location no longer applies. */
  const selectMaintainer = (client: ClientSuggestion | null) => {
    onChange('clientNumber', client?.clientNumber ?? '');
    onChange('maintainerLabel', client ? clientLabel(client) : '');
    onChange('clientLocationCode', '');
  };

  /**
   * Typed text that is not a pick filters nothing — legacy's report takes a client number, not a
   * name — so it clears a pick it no longer matches.
   */
  const typeMaintainer = (term: string) => {
    if (criteria.clientNumber !== '' && term !== criteria.maintainerLabel) {
      selectMaintainer(null);
    }
  };

  const text = (
    field: keyof Criteria,
    labelText: string,
    maxLength: number,
    extra?: { placeholder?: string; inputMode?: 'decimal' | 'numeric'; hideLabel?: boolean },
  ) => (
    <TextInput
      id={`${ID}-${field}`}
      data-testid={`${ID}-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={String(criteria[field])}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event) => onChange(field, event.target.value as never)}
      onBlur={() => leave(field)}
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
      id={`${ID}-${field}`}
      data-testid={`${ID}-${field}`}
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
      id={`${ID}-${field}`}
      data-testid={`${ID}-${field}`}
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
   * A "from – to" pair, as on Structure Search: the legend names the pair and the boxes carry
   * hidden labels and a placeholder.
   */
  const range = (
    from: keyof Criteria,
    to: keyof Criteria,
    labelText: string,
    kind: 'kilometres' | 'year' | 'millimetres',
  ) => {
    const box = (field: keyof Criteria, end: 'From' | 'To') => {
      const common = { hideLabel: true } as const;
      if (kind === 'year') {
        return text(field, `${labelText} ${end}`, 4, {
          ...common,
          inputMode: 'numeric',
          placeholder: `${end} yyyy`,
        });
      }
      if (kind === 'millimetres') {
        return text(field, `${labelText} ${end}`, 6, {
          ...common,
          inputMode: 'numeric',
          placeholder: end,
        });
      }
      return text(field, `${labelText} ${end}`, 9, {
        ...common,
        inputMode: 'decimal',
        placeholder: end,
      });
    };
    return (
      <fieldset className="report-criteria__range">
        <legend className="cds--label">{labelText}</legend>
        <div className="report-criteria__range-inputs">
          {box(from, 'From')}
          <span aria-hidden="true" className="report-criteria__range-dash">
            –
          </span>
          {box(to, 'To')}
        </div>
      </fieldset>
    );
  };

  const toggle = (field: keyof Criteria, labelText: string) => (
    <Toggle
      id={`${ID}-${field}`}
      data-testid={`${ID}-${field}`}
      labelText={labelText}
      size="sm"
      toggled={Boolean(criteria[field])}
      onToggle={(checked) => onChange(field, checked as never)}
    />
  );

  return (
    <form onSubmit={submit} data-testid={`${ID}-form`} className="report-criteria__form">
      <div className="report-criteria__grid">
        {/* The structure */}
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
        {codeSelect('deckTypeCode', 'Deck Type', 'Any deck type', codeTables.deckTypeCodes)}
        {codeSelect(
          'portableSuperstructureStatusCode',
          'Portable Superstructure Status',
          'Any status',
          codeTables.portableSuperstructureStatusCodes,
        )}

        {codeSelect(
          'culvertTypeCode',
          'Culvert Type',
          'Any culvert type',
          codeTables.culvertTypeCodes,
        )}
        {codeSelect(
          'culvertMaterialCode',
          'Culvert Material',
          'Any culvert material',
          codeTables.culvertMaterialCodes,
        )}
        {range('openingWidthMin', 'openingWidthMax', 'Opening Width (mm)', 'millimetres')}
        {range('yearBuiltStart', 'yearBuiltEnd', 'Year Superstructure Installed', 'year')}

        {/* The site it stands on */}
        {codeSelect('siteStatusCode', 'Site Status', 'Any status', codeTables.siteStatusCodes)}
        {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}
        <div className="report-criteria__paired">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>
        {range('kiloStart', 'kiloEnd', 'Kilometres', 'kilometres')}

        {codeSelect(
          'specialAccessCode',
          'Special Access Requirements',
          'Any requirement',
          codeTables.specialAccessCodes,
        )}
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
        <ClientCombo
          key={resetToken}
          id={`${ID}-maintainer`}
          titleText="Designated Maintainer"
          selectedLabel={criteria.maintainerLabel}
          onSelect={selectMaintainer}
          onTermChange={typeMaintainer}
        />
        <Select
          id={`${ID}-clientLocationCode`}
          data-testid={`${ID}-clientLocationCode`}
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
      </div>

      <div className="report-criteria__output">
        <RadioButtonGroup
          legendText="Sort By"
          name={`${ID}-sortBy`}
          orientation="vertical"
          valueSelected={criteria.sortBy}
          onChange={(value) => onChange('sortBy', value as StructureInventorySort)}
        >
          {SORT_OPTIONS.map((option) => (
            <RadioButton
              key={option.value}
              id={`${ID}-sortBy-${option.value}`}
              data-testid={`${ID}-sortBy-${option.value}`}
              labelText={option.label}
              value={option.value}
            />
          ))}
        </RadioButtonGroup>
      </div>

      <div className="report-criteria__actions">
        <Button
          kind="ghost"
          type="button"
          onClick={onReset}
          onMouseDown={holdFocus}
          data-testid={`${ID}-reset`}
        >
          Clear
        </Button>
        <Button
          kind="tertiary"
          type="button"
          renderIcon={Download}
          onClick={() => run('CSV')}
          onMouseDown={holdFocus}
          data-testid={`${ID}-run-CSV`}
        >
          Download CSV
        </Button>
        <Button
          kind="tertiary"
          type="submit"
          renderIcon={Download}
          onMouseDown={holdFocus}
          data-testid={`${ID}-run-PDF`}
        >
          Download PDF
        </Button>
      </div>
    </form>
  );
};

export default StructureInventorySummaryCriteriaForm;
