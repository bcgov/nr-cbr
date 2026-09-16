import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, Select, SelectItem, TextInput, Toggle } from '@carbon/react';

import { criteriaErrors } from './validation';

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
/**
 * "DCK - Chilliwack Natural Resource District", the form nr-frep uses for both code lists and org
 * units, and the same string the results table shows for a district.
 *
 * <p>Joined rather than interpolated so a missing half degrades to the other one instead of
 * rendering "DCK - undefined" or a leading dash — reference data is not ours and a row with one
 * column empty is not worth breaking a dropdown over.
 */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

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
  const errors = criteriaErrors(criteria);
  const hasErrors = Object.keys(errors).length > 0;

  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    // Legacy runs the same check server-side and re-renders the form with a page-level message.
    // Stopping here keeps the messages beside the boxes they belong to; the backend rejects the
    // same values with a 400, because the browser is not the only caller.
    if (hasErrors) {
      return;
    }
    onSearch();
  };

  const text = (
    field: keyof Criteria,
    labelText: string,
    maxLength: number,
    extra?: { placeholder?: string; inputMode?: 'decimal'; hideLabel?: boolean },
  ) => (
    <TextInput
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={String(criteria[field])}
      invalid={field in errors}
      invalidText={errors[field]}
      onChange={(event) => onChange(field, event.target.value as never)}
      {...extra}
    />
  );

  /**
   * A code-table dropdown.
   *
   * <p>`anyText` is the unfiltered option, and it says so in words — "Any status" rather than a
   * blank line, which is how nr-frep writes it. A blank first option reads as a value that failed
   * to load, and gives a user who has set the filter no obvious way to unset it.
   */
  const codeSelect = (
    field: keyof Criteria,
    labelText: string,
    anyText: string,
    options: CodeOption[],
  ) => (
    <Select
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      disabled={codeTablesLoading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      {/* The legacy form's blank first option, given words. The value is still "" — no filter. */}
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.code}
          value={option.code}
          // "ACT - Active", as nr-frep renders a code list. The code is what the business says out
          // loud and what appears in reports and on paper; the description alone leaves a user
          // translating between the two.
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
      id={`site-search-${field}`}
      data-testid={`site-search-${field}`}
      labelText={labelText}
      disabled={loading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.orgUnitNo}
          // The number, not the code: it is what CROSSING_SITE.ORG_UNIT_NO holds, and the code is
          // not unique across the org hierarchy. Only the label changes here.
          value={option.orgUnitNo}
          text={label(option.orgUnitCode, option.orgUnitName)}
        />
      ))}
    </Select>
  );

  /**
   * A "from – to" pair of kilometre bounds. Legacy rendered these as two inputs either side of a
   * hyphen, `size="8" maxlength="8"`.
   *
   * <p>`maxLength` is 9 rather than legacy's 8: the column is `NUMBER(8,2)`, so `999999.99` is a
   * legal value and eight characters cannot hold it. Legacy's limit forbids a value its own
   * validator accepts.
   *
   * <p>`inputMode="decimal"` rather than `type="number"`: a number input brings spinners, which are
   * meaningless on a search bound, and browsers silently discard a non-numeric value instead of
   * showing it back — which would leave the field-level message describing something the user can no
   * longer see.
   *
   * <p>One grid column for the pair, not two. Two boxes this short inside a two-column cell filled
   * the first half and left the second half empty, so the "To" box ended just past the column
   * boundary and lined up with nothing on the rows above or below.
   *
   * <p>"From" and "To" are `hideLabel`, so they name the boxes for a screen reader without taking a
   * second row of label above them. Visible, they pushed these inputs a label-height below every
   * other field on the row — the legend occupies the row that a plain field's label does, and the
   * From/To labels then sat underneath it. Legacy has no such labels either: one `Kilometres:`, two
   * boxes, a hyphen. The placeholder carries the same hint back visually, inside the box, where it
   * costs no height.
   */
  const range = (from: keyof Criteria, to: keyof Criteria, labelText: string) => (
    <fieldset className="site-search__range">
      <legend className="cds--label">{labelText}</legend>
      <div className="site-search__range-inputs">
        {text(from, 'From', 9, { inputMode: 'decimal', hideLabel: true, placeholder: 'From' })}
        <span aria-hidden="true" className="site-search__range-dash">
          –
        </span>
        {text(to, 'To', 9, { inputMode: 'decimal', hideLabel: true, placeholder: 'To' })}
      </div>
    </fieldset>
  );

  return (
    <form onSubmit={submit} data-testid="site-search-form" className="site-search__form">
      <div className="site-search__criteria">
        {text('siteId', 'Site #', 14)}
        {codeSelect('siteStatusCode', 'Status', 'Any status', codeTables.siteStatusCodes)}

        <div className="site-search__paired site-search__span-2">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>

        {codeSelect(
          'structureInspectionStatusCode',
          'Inspection Status',
          'Any inspection status',
          codeTables.structureInspectionStatusCodes,
        )}
        {text('forestServiceRoad', 'Forest Service Road', 20)}
        {text('clientNumber', 'Designated Maintainer Client Number', 8)}
        {text('crossingName', 'Crossing Name', 20)}
        {text('clientLocationCode', 'Client Location Code', 2)}

        {orgUnitSelect('orgUnit', 'Forest District', 'Any district', codeTables.forestDistricts)}
        {orgUnitSelect(
          'managementOrgUnit',
          'Management Area',
          // Until a district is chosen this list is empty by design, so "Any management area" would
          // be offering a filter over nothing. Saying what is missing is the more useful blank.
          criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
          codeTables.managementAreas,
          codeTablesLoading || managementAreasLoading,
        )}
        {range('kiloStart', 'kiloEnd', 'Kilometres')}
        {range('userKmStart', 'userKmEnd', 'User Kilometres')}

        {codeSelect(
          'specialAccessCode',
          'Special Access Requirements',
          'Any requirement',
          codeTables.specialAccessCodes,
        )}
        {codeSelect('siteTypeCode', 'Site Type', 'Any site type', codeTables.siteTypeCodes)}
        {text('primaryUserName', 'Designated Maintainer', 35)}

        {/* Toggles rather than checkboxes. Both are filters that are either applied or not, and a
            toggle states its current position in words ("Off"/"On") instead of leaving the user to
            read a tick — which matters for "Incomplete Data?", where an unticked box is ambiguous
            between "not filtering on this" and "show only complete records".

            The two share one grid cell. A cell each put a column's width between them, which read as
            two unrelated controls rather than the pair of filters they are — and a toggle is much
            narrower than the field a column is sized for, so most of that space was empty. */}
        <div className="site-search__toggles">
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
        </div>

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
