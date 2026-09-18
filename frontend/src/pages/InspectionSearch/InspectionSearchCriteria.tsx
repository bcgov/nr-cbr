import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DatePicker,
  DatePickerInput,
  InlineNotification,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextInput,
  Toggle,
} from '@carbon/react';
import { useRef, useState } from 'react';

import { PROJECT_BRANCH_KM_DATE_SORT, STRUCTURE_ID_DATE_SORT } from './types';
import { MONTH_PATTERN, criteriaErrors, hasCriteria, isMonth } from './validation';

import type {
  CodeOption,
  InspectionReviewerOption,
  InspectionSearchCriteria as Criteria,
  InspectionSortBy,
  OrgUnitOption,
} from './types';
import type { ChangeEvent, FC, SubmitEventHandler } from 'react';

export type CodeTables = {
  structureTypeClassCodes: CodeOption[];
  inspectionTypeCodes: CodeOption[];
  inspectionReportStatusCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
  inspectionReviewers: InspectionReviewerOption[];
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
 * The Inspection Search criteria form.
 *
 * <p>All 19 fields from the legacy `inspection_search.jsp`, in the legacy order and with the legacy
 * labels. The labels are the vocabulary the business already uses and appear in their
 * documentation, so they are kept verbatim rather than modernised — "Project File ID#", "Type/Class"
 * and the rest. The four question-shaped checkbox labels keep their question marks for the same
 * reason.
 *
 * <p>Legacy laid this out as a 4-column HTML table with hand-set percentage widths, which put each
 * checkbox on the same row as an unrelated dropdown. Here it is the same CSS grid Site Search uses —
 * four criteria per row on a wide screen, stepping to two and then one — with the four filter
 * toggles gathered into one block instead of scattered down the right-hand column.
 *
 * <p>One legacy defect fixed by construction: every `<label for="...">` on that form names an id
 * that does not exist (`for="siteId"` against an input Struts renders as `siteId` only by
 * coincidence of the property name, `for="structureId"` against `structureName`, and so on), so
 * clicking a label focuses nothing and a screen reader announces an unlabelled box. Carbon owns the
 * association here — each field passes `labelText` and the component wires it to its own input.
 */
/**
 * "DCK - Chilliwack Natural Resource District", the form nr-frep uses for both code lists and org
 * units, and the same string the results table shows for a district.
 *
 * <p>Joined rather than interpolated so a missing half degrades to the other one instead of
 * rendering "DCK - undefined" or a leading dash.
 */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

/**
 * The calendar's date format: a month, because the criterion is a month.
 *
 * <p>flatpickr formats `m` zero-padded, so anything the calendar writes comes back as `2026/01` —
 * already the canonical form `validation.ts` compares and the backend will parse.
 */
const MONTH_FORMAT = 'Y/m';

/**
 * `yyyy/mm` (or `yyyy/m`) to the first of that month, and anything else to `false`.
 *
 * <p>flatpickr's own parser fills the fields a format does not mention from *today*, so `Y/m`
 * reading "2026/02" on the 31st of a month lands on 3 March and the box redraws itself as
 * `2026/03`. Anchoring the day to the 1st is what makes a month-granular format safe.
 *
 * <p>`false` is flatpickr's signal for "not a date": it leaves the box empty rather than inventing
 * one. That is the behaviour wanted for a half-typed or misformatted month — see the blur handler
 * on {@link monthPicker}, which is what keeps this form's own state in step with it.
 */
const parseMonth = (value: string): Date | false => {
  if (!MONTH_PATTERN.test(value)) {
    return false;
  }
  const [year, month] = value.split('/');
  return new Date(Number(year), Number(month) - 1, 1);
};

const formatMonth = (date: Date): string =>
  `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}`;

const InspectionSearchCriteriaForm: FC<Props> = ({
  criteria,
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onSearch,
  onReset,
}) => {
  const errors = criteriaErrors(criteria);
  const hasErrors = Object.keys(errors).length > 0;

  /**
   * Whether the user has already tried to search an empty form.
   *
   * <p>The message only appears after a submit, not while the form is merely untouched — an empty
   * form is the normal starting state and telling the user off for it before they have done
   * anything is noise. It clears itself the moment a criterion is entered, because the condition it
   * describes is no longer true; that is why the flag records the attempt rather than the message.
   */
  const [attemptedEmpty, setAttemptedEmpty] = useState(false);
  const showEmptyError = attemptedEmpty && !hasCriteria(criteria);

  /**
   * What each calendar has been told, held still while a month is half-typed.
   *
   * <p>Carbon pushes `value` down into flatpickr on every change, and flatpickr answers a value it
   * cannot parse by blanking the box — so handing it the live criterion would erase "2026/0" the
   * instant the user typed the "0" on the way to "2026/01". Only a month flatpickr can read is ever
   * forwarded; anything mid-edit leaves the last readable one in place, and the box keeps what the
   * user is typing.
   */
  const parsable = useRef({ inspectionDateStart: '', inspectionDateEnd: '' });
  if (isMonth(criteria.inspectionDateStart)) {
    parsable.current.inspectionDateStart = criteria.inspectionDateStart;
  }
  if (isMonth(criteria.inspectionDateEnd)) {
    parsable.current.inspectionDateEnd = criteria.inspectionDateEnd;
  }

  // Typed as the handler rather than the event: React 19 deprecated `FormEvent`, and naming the
  // prop's own type means the element decides what the event is instead of this file guessing.
  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    // Legacy's `errors.search.select`: an empty form is refused rather than answered with every
    // inspection ever recorded.
    if (!hasCriteria(criteria)) {
      setAttemptedEmpty(true);
      return;
    }
    setAttemptedEmpty(false);
    // Legacy runs the same checks server-side and re-renders the form with page-level messages.
    // Stopping here keeps the messages beside the boxes they belong to; the backend will reject the
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
    extra?: { placeholder?: string; hideLabel?: boolean },
  ) => (
    <TextInput
      id={`inspection-search-${field}`}
      data-testid={`inspection-search-${field}`}
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
   * <p>`anyText` is the unfiltered option, and it says so in words — "Any status" rather than the
   * legacy blank line, which reads as a value that failed to load and gives a user who has set the
   * filter no obvious way to unset it.
   */
  const codeSelect = (
    field: keyof Criteria,
    labelText: string,
    anyText: string,
    options: CodeOption[],
  ) => (
    <Select
      id={`inspection-search-${field}`}
      data-testid={`inspection-search-${field}`}
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
          // "ROUT - Routine", as nr-frep renders a code list. The code is what the business says out
          // loud and what appears in reports and on paper.
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
      id={`inspection-search-${field}`}
      data-testid={`inspection-search-${field}`}
      labelText={labelText}
      disabled={loading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as never)}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.orgUnitNo}
          // The number, not the code: it is what the inspection's org unit holds, and the code is
          // not unique across the org hierarchy. Only the label changes here.
          value={option.orgUnitNo}
          text={label(option.orgUnitCode, option.orgUnitName)}
        />
      ))}
    </Select>
  );

  /**
   * One end of the inspection date range: a `yyyy/mm` month, picked from a calendar or typed.
   *
   * <p>`hideLabel`, with the fieldset's legend carrying the group name: a screen reader announces
   * "Inspection Date, From" rather than a bare "From", and the boxes stay level with the plain
   * fields beside them instead of sitting a label-height lower.
   *
   * <p>The two calendars are deliberately not chained to each other by `minDate`/`maxDate`. Doing
   * so reads well until someone types: an end month earlier than the start puts the *start* box out
   * of its own picker's range, flatpickr empties that box, and the criterion behind it is left
   * holding a month the form no longer shows. An impossible range is caught by `validation.ts`
   * instead, where it can be explained rather than silently swallowed.
   *
   * <p>Three handlers, each covering a different way the box can change:
   *
   * <ul>
   *   <li>`onChange` — a calendar pick, and every keystroke. The criterion tracks the typing, so
   *       the format message appears as the mistake is being made rather than at submit.
   *   <li>`onBlur` — leaving the box. flatpickr has already re-read the text by then, padding
   *       `2026/3` to `2026/03` or clearing it if it is not a month at all, and this copies its
   *       verdict back. Without it the criterion would keep a value the box no longer shows, and
   *       the search would run on something invisible.
   *   <li>`pattern` — not a handler, but the same class of problem. Carbon defaults the input's
   *       pattern to `m/d/Y`, which the browser enforces on submit; a month fails it, and the form
   *       would then refuse to submit with no message anywhere on the page.
   * </ul>
   */
  const monthPicker = (
    field: 'inspectionDateStart' | 'inspectionDateEnd',
    labelText: string,
    placeholder: string,
  ) => (
    <DatePicker
      datePickerType="single"
      dateFormat={MONTH_FORMAT}
      parseDate={parseMonth}
      value={parsable.current[field]}
      // The shared opt-in fix in `_overrides.scss`: a single-mode picker is hard-coded to 18rem by
      // Carbon, which overflows half of a two-column cell.
      className="cbr-date-picker"
      onChange={(dates: Date[]) => onChange(field, dates[0] ? formatMonth(dates[0]) : '')}
    >
      <DatePickerInput
        id={`inspection-search-${field}`}
        data-testid={`inspection-search-${field}`}
        labelText={labelText}
        hideLabel
        placeholder={placeholder}
        pattern="\d{4}/\d{1,2}"
        invalid={field in errors}
        invalidText={errors[field]}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(field, event.target.value)}
        onBlur={(event) => onChange(field, event.target.value)}
      />
    </DatePicker>
  );

  /**
   * The inspection date range: two `yyyy/mm` months.
   *
   * <p>Month-granular, because that is what the criterion is — legacy parses both bounds with
   * `SimpleDateFormat("yyyy/MM")`, and the label has said `(yyyy/mm)` for as long as the screen has
   * existed. The calendar underneath still offers days, because Carbon builds flatpickr without its
   * month-select plugin and does not expose the plugin list; picking any day in January gives
   * `2026/01`, which is the month the search uses. Legacy had the same mismatch — a calendar popup
   * formatted `yyyy/MM`, so the day it collected was discarded too.
   *
   * <p>Two single pickers rather than one `range` picker, which is what this looks like it should
   * be. A range picker treats one month with the other blank as an unfinished gesture and clears it
   * when the user clicks away — but "everything from 2026/01 onwards" is a whole criterion here,
   * not half of one, and a search form has to let it stand.
   */
  const dateRange = () => (
    <fieldset className="inspection-search__range inspection-search__span-2">
      <legend className="cds--label">Inspection Date (yyyy/mm)</legend>
      <div className="inspection-search__range-inputs">
        {monthPicker('inspectionDateStart', 'From', 'From: 2026/01')}
        <span aria-hidden="true" className="inspection-search__range-dash">
          –
        </span>
        {monthPicker('inspectionDateEnd', 'To', 'To: 2026/12')}
      </div>
    </fieldset>
  );

  /**
   * One filter switch: the question above, the switch and its state below — Carbon's default
   * arrangement, and the one Site Search uses for the same kind of control, so the two screens read
   * the same way.
   */
  const toggle = (
    field:
      'closeProximity' | 'mostRecentInspections' | 'findChangedReviewed' | 'findMovedStructures',
    labelText: string,
  ) => (
    <Toggle
      id={`inspection-search-${field}`}
      data-testid={`inspection-search-${field}`}
      className="inspection-search__toggle"
      labelText={labelText}
      size="sm"
      toggled={criteria[field]}
      onToggle={(checked) => onChange(field, checked)}
    />
  );

  return (
    <form
      onSubmit={submit}
      data-testid="inspection-search-form"
      className="inspection-search__form"
    >
      <div className="inspection-search__criteria">
        {text('siteId', 'Site #', 14)}
        {/* Legacy's label reads "Structure #" while the property is `structureName` and the column
            behind it holds a name. The legacy label is kept: it is what the business calls the
            field, and it is what the user is looking for. */}
        {text('structureName', 'Structure #', 14)}
        {codeSelect(
          'structureTypeClassCode',
          'Type/Class',
          'Any type or class',
          codeTables.structureTypeClassCodes,
        )}
        {codeSelect(
          'inspectionTypeCode',
          'Inspection Type',
          'Any inspection type',
          codeTables.inspectionTypeCodes,
        )}

        {codeSelect(
          'inspectionReportStatusCode',
          'Inspection Report Status',
          'Any status',
          codeTables.inspectionReportStatusCodes,
        )}

        <div className="inspection-search__paired">
          {text('forestFileId', 'Project File ID#', 10)}
          {text('roadSectionId', 'Br.', 30)}
        </div>

        {text('forestServiceRoad', 'Forest Service Road', 20)}

        {/* Out of legacy's order, which has it below Management Area with the other two org units.
            It is moved because the date range below needs two columns and cannot fit in the one
            left at the end of this row, so the row was ending in a hole — and because this is the
            org unit that loses least by being separated: BCTS Business Area is a third, independent
            filter, not a child of the district the way Management Area is, so the two that have to
            stay adjacent still are. */}
        {orgUnitSelect(
          'businessAreaOrgUnitNo',
          'BCTS Business Area',
          'Any business area',
          codeTables.businessAreas,
        )}

        {dateRange()}

        {orgUnitSelect('orgUnitNo', 'Forest District', 'Any district', codeTables.forestDistricts)}
        {orgUnitSelect(
          'managementOrgUnitNo',
          'Management Area',
          // Until a district is chosen this list is empty by design, so "Any management area" would
          // be offering a filter over nothing. Saying what is missing is the more useful blank.
          criteria.orgUnitNo === '' ? 'Select a forest district first' : 'Any management area',
          codeTables.managementAreas,
          codeTablesLoading || managementAreasLoading,
        )}
        {text('inspectorName', 'Inspector Name', 255)}

        <Select
          id="inspection-search-inspectionReviewerId"
          data-testid="inspection-search-inspectionReviewerId"
          labelText="Reviewed By"
          disabled={codeTablesLoading}
          value={criteria.inspectionReviewerId}
          onChange={(event) => onChange('inspectionReviewerId', event.target.value)}
        >
          <SelectItem value="" text="Anyone" />
          {codeTables.inspectionReviewers.map((reviewer) => (
            // The name alone, not "id - name": the value is a sequence number, which means nothing
            // to a user and appears nowhere they would recognise it.
            <SelectItem
              key={reviewer.inspectionReviewerId}
              value={reviewer.inspectionReviewerId}
              text={reviewer.displayName}
            />
          ))}
        </Select>

        {/* The four filters legacy renders as checkboxes down the right-hand column, each stranded
            beside an unrelated dropdown. Gathered into one block because they are one kind of thing:
            switches that narrow the result set. Toggles rather than checkboxes for the reason Site
            Search gives — a toggle states its position in words, where an unticked box is ambiguous
            between "not filtering on this" and "show me the ones that are not".

            Two columns of two rather than four across: these labels are whole questions, not Site
            Search's two words, and four of them in a row leaves the longest one wrapping while the
            other three sit on one line — a ragged row of four different heights. Two columns give
            each question the room to be one line, and leave the other half of the form's width to
            Sort By, which then sits beside this block instead of below it. */}
        <fieldset className="inspection-search__toggles">
          {/* Visually hidden rather than deleted. Each question names itself, so a heading over them
              was only repeating what the four labels already say; but the fieldset still needs an
              accessible name, or a screen reader announces four unrelated switches at the end of the
              form with nothing tying them together. */}
          <legend className="cds--visually-hidden">Limit results to</legend>
          <div className="inspection-search__toggles-grid">
            {toggle('closeProximity', 'Close Proximity Inspection Required?')}
            {toggle('mostRecentInspections', 'Most Recent Inspections Only?')}
            {toggle('findChangedReviewed', 'Previously Reviewed Inspections Only?')}
            {toggle('findMovedStructures', 'Include Inspections for Structures at Previous Sites?')}
          </div>
        </fieldset>

        <fieldset className="inspection-search__sort inspection-search__span-2">
          {/* Stacked, not side by side. Both labels are long enough to wrap in half a two-column
              cell, and a wrapped horizontal radio puts its second line under the *other* option's
              button — so the two read as four fragments. Legacy stacks them too, with a <br>. */}
          <RadioButtonGroup
            legendText="Sort By"
            name="inspection-search-sortBy"
            orientation="vertical"
            valueSelected={criteria.sortBy}
            onChange={(value) => onChange('sortBy', value as InspectionSortBy)}
          >
            <RadioButton
              // Legacy's `reset()` selects this one, so it is what an untouched form sorts by.
              labelText="Structure Id (Asc), Date (Desc)"
              value={STRUCTURE_ID_DATE_SORT}
              id="inspection-search-sortBy-structure"
              data-testid="inspection-search-sortBy-structure"
            />
            <RadioButton
              labelText="Project Number, Branch, KM (Asc), Date (Desc)"
              value={PROJECT_BRANCH_KM_DATE_SORT}
              id="inspection-search-sortBy-project"
              data-testid="inspection-search-sortBy-project"
            />
          </RadioButtonGroup>
        </fieldset>

        {showEmptyError && (
          <div className="inspection-search__notice">
            <InlineNotification
              kind="warning"
              lowContrast
              hideCloseButton
              title="Enter at least one criterion"
              subtitle={
                'An inspection search needs something to narrow it — a site, a structure, a date ' +
                'range, a district or any other filter above.'
              }
              data-testid="inspection-search-empty-error"
            />
          </div>
        )}

        <div className="inspection-search__actions">
          <Button
            kind="ghost"
            type="button"
            onClick={onReset}
            data-testid="inspection-search-reset"
          >
            Clear
          </Button>
          <Button type="submit" renderIcon={SearchIcon} data-testid="inspection-search-submit">
            Search
          </Button>
        </div>
      </div>
    </form>
  );
};

export default InspectionSearchCriteriaForm;
