import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, Select, SelectItem, TextInput, Toggle } from '@carbon/react';

import type { CodeOption, OrgUnitOption, SiteSearchCriteria as Criteria } from './types';
import type { FC, SubmitEventHandler } from 'react';

export type CodeTables = {
  siteStatusCodes: CodeOption[];
  structureInspectionStatusCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
};

type Props = {
  criteria: Criteria;
  codeTables: CodeTables;
  /** Disables the selects while their contents are still unknown. */
  codeTablesLoading?: boolean;
  /**
   * Management areas alone, because they are the one list that reloads mid-form — they depend on
   * the chosen Forest District. Folding this into {@link codeTablesLoading} would grey out the
   * whole form every time the user changed district.
   */
  managementAreasLoading?: boolean;
  onChange: <K extends keyof Criteria>(field: K, value: Criteria[K]) => void;
  onSearch: () => void;
  onReset: () => void;
};

/**
 * The Site Search criteria form.
 *
 * <p>All 17 fields from the legacy `site_search.jsp`, in the legacy order and with the legacy
 * labels. The labels are the vocabulary the business already uses and appear in their
 * documentation, so they are kept verbatim rather than modernised — "Project File ID#",
 * "Designated Maintainer Client Number", "Incomplete Data?" and the rest.
 *
 * <p>Legacy laid this out as a 4-column HTML table with hand-set percentage widths. Here it is a
 * CSS grid of four, matching nr-frep's search filters — four criteria per row on a wide screen,
 * stepping to two and then one as the window narrows, which the legacy table did not do at all.
 * Fields that hold a pair of inputs span two columns; see the stylesheet.
 */
const SiteSearchCriteriaForm: FC<Props> = ({
  criteria,
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onSearch,
  onReset,
}) => {
  // Typed as the handler rather than the event: React 19 deprecated `FormEvent` ("FormEvent
  // doesn't actually exist"), and naming the prop's own type — `onSubmit?: SubmitEventHandler<T>` —
  // means the element decides what the event is instead of this file guessing.
  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    onSearch();
  };

  const text = (
    field: keyof Criteria,
    labelText: string,
    maxLength: number,
    extra?: { placeholder?: string },
  ) => (
    <TextInput
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
      {...extra}
    />
  );

  const codeSelect = (field: keyof Criteria, labelText: string, options: CodeOption[]) => (
    <Select
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      disabled={codeTablesLoading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      {/* The legacy form's blank first option: no filter on this criterion. */}
      <SelectItem value="" text="" />
      {options.map((option) => (
        <SelectItem key={option.code} value={option.code} text={option.description} />
      ))}
    </Select>
  );

  const orgUnitSelect = (
    field: keyof Criteria,
    labelText: string,
    options: OrgUnitOption[],
    loading = codeTablesLoading,
  ) => (
    <Select
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      disabled={loading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      <SelectItem value="" text="" />
      {options.map((option) => (
        <SelectItem key={option.orgUnitNo} value={option.orgUnitNo} text={option.orgUnitName} />
      ))}
    </Select>
  );

  /** A "from – to" pair. Legacy rendered these as two inputs either side of a hyphen. */
  const range = (from: keyof Criteria, to: keyof Criteria, labelText: string) => (
    <fieldset className="site-search__range site-search__span-2">
      <legend className="cds--label">{labelText}</legend>
      <div className="site-search__range-inputs">
        {text(from, 'From', 8)}
        <span aria-hidden="true" className="site-search__range-dash">
          –
        </span>
        {text(to, 'To', 8)}
      </div>
    </fieldset>
  );

  return (
    <form onSubmit={submit} data-testid="site-search-form" className="site-search__form">
      <div className="site-search__criteria">
        {text('siteId', 'Site #', 14)}
        {codeSelect('siteStatusCode', 'Status', codeTables.siteStatusCodes)}

        <div className="site-search__paired site-search__span-2">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>

        {codeSelect(
          'structureInspectionStatusCode',
          'Inspection Status',
          codeTables.structureInspectionStatusCodes,
        )}
        {text('forestServiceRoad', 'Forest Service Road', 20)}
        {text('clientNumber', 'Designated Maintainer Client Number', 8)}
        {text('crossingName', 'Crossing Name', 20)}
        {text('clientLocationCode', 'Client Location Code', 2)}

        {orgUnitSelect('orgUnit', 'Forest District', codeTables.forestDistricts)}
        {orgUnitSelect(
          'managementOrgUnit',
          'Management Area',
          codeTables.managementAreas,
          codeTablesLoading || managementAreasLoading,
        )}
        {range('kiloStart', 'kiloEnd', 'Kilometres')}
        {range('userKmStart', 'userKmEnd', 'User Kilometres')}

        {codeSelect(
          'specialAccessCode',
          'Special Access Requirements',
          codeTables.specialAccessCodes,
        )}
        {codeSelect('siteTypeCode', 'Site Type', codeTables.siteTypeCodes)}
        {text('primaryUserName', 'Designated Maintainer', 35)}

        {/* Toggles rather than checkboxes. Both are filters that are either applied or not, and a
            toggle states its current position in words ("Off"/"On") instead of leaving the user to
            read a tick — which matters for "Incomplete Data?", where an unticked box is ambiguous
            between "not filtering on this" and "show only complete records". */}
        <Toggle
          id="site-search-incomplete"
          data-testid="site-search-incomplete"
          className="site-search__toggle"
          labelText="Incomplete Data?"
          size="sm"
          toggled={criteria.incomplete}
          onToggle={(checked) => onChange('incomplete', checked)}
        />
        <Toggle
          id="site-search-capitalRoad"
          data-testid="site-search-capitalRoad"
          className="site-search__toggle"
          labelText="Capital Road"
          size="sm"
          toggled={criteria.capitalRoad}
          onToggle={(checked) => onChange('capitalRoad', checked)}
        />

        <div className="site-search__actions">
          <Button kind="ghost" type="button" onClick={onReset} data-testid="site-search-reset">
            Clear
          </Button>
          <Button type="submit" renderIcon={SearchIcon} data-testid="site-search-submit">
            Search
          </Button>
        </div>
      </div>
    </form>
  );
};

export default SiteSearchCriteriaForm;
