import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, Checkbox, Select, SelectItem, TextArea, TextInput } from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { SITE_TEXT_LIMITS, SITE_TYPE, type SiteFormValues } from './types';

import './siteForm.scss';

import type { SiteErrors } from './validation';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { FC, SubmitEventHandler } from 'react';

import { byteLength } from '@/utils/textLimits';

export type SiteCodeTables = {
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  structureInspectionStatusCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  /** The districts a recreation site may use, narrowed by its project file. */
  recreationDistricts: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
};

type Props = {
  values: SiteFormValues;
  errors: SiteErrors;
  /**
   * What the form says in amber: values it will store, and would like looked at first.
   *
   * <p>Legacy's second message tier, from `Site.validate` — a coordinate outside the province, a
   * UTM zone BC does not use, sixty minutes in a box that holds sixty seconds to the minute. None
   * of them refuses the save, and treating them as errors would make the form unusable for exactly
   * the crossings whose position is most worth checking.
   *
   * <p>An error on the same field wins. Carbon renders one state per input, and "this will not
   * save" is always the more urgent of the two.
   */
  warnings?: SiteErrors;
  codeTables: SiteCodeTables;
  codeTablesLoading?: boolean;
  managementAreasLoading?: boolean;
  onChange: <K extends keyof SiteFormValues>(field: K, value: SiteFormValues[K]) => void;
  /**
   * A field has been left. The page uses it to decide whose errors may be shown yet — see
   * `errorsForSettledFields`.
   */
  onSettle: (field: keyof SiteFormValues) => void;
  /**
   * Run when the form is submitted.
   *
   * <p>Save and Cancel are rendered by the page, beside the heading — but Save stays a real submit
   * button, tied back here by the `form` attribute and {@link FORM_ID}. That keeps the native path
   * intact: pressing Enter in any text box still saves, which a button wired only to `onClick`
   * would have quietly taken away.
   */
  onSave: () => void;
  /**
   * Whether a field may be changed right now.
   *
   * <p>Defaults to everything, which is what Add Site wants — its route is gated so that only a
   * user who may fill the whole form can reach it. Site Detail passes a predicate built from the
   * role matrix and whether the page is in edit mode.
   *
   * <p>A field that says no renders as a {@link ReadOnlyField}, not a disabled input. A greyed-out
   * box invites a click that does nothing and takes a tab stop for no reason; a label above a
   * value reads as information, which is what it is.
   */
  isEditable?: (field: keyof SiteFormValues) => boolean;
  /**
   * The name of the road section that Project File ID# and Br. name between them — <b>or, for a
   * recreation site, the name of the recreation project the file names</b>.
   *
   * <p>One prop for both because they are one cell: `site.jsp:803-824` is a single `<c:if>` that
   * shows "Forest Service Road" for every type but `REC` and "Project Name" for `REC`, never both.
   * They come from different tables — `CBR_ROAD_SECTION_VW` and `RECREATION_PROJECT` — so the page
   * decides which question to ask; this only decides what to call the answer.
   *
   * <p>Supplied by the page rather than fetched here: Site Detail already has it from the site it
   * read, and Add Site looks it up as the boxes change. Never typed — legacy disables the box for
   * every role, because the value belongs to the other record and not to the site.
   */
  forestServiceRoad?: string;
  /** True while that lookup is in flight, so the field can say so instead of looking empty. */
  forestServiceRoadLoading?: boolean;
  /**
   * Whether the Project File ID# and Br. on this form resolve to a real road section.
   *
   * <p>It decides who owns the Forest District. Legacy's rule, from `SiteAction` and
   * `setValidateResponse`: the road sets the district and locks it whenever there is one; a
   * recreation site keeps the choice, with the file narrowing the list; and a storage site keeps
   * it only while there is no road, because a storage site may have none.
   */
  roadResolved?: boolean;
  /**
   * Fields to leave off the form entirely.
   *
   * <p>For the three legacy locks on every branch of `site.jsp` — Designated Maintainer, User
   * Kilometres and BCTS BA Responsible. None of them can be set from this screen in legacy either,
   * so on a site that does not exist yet they can only ever be blank, and a read-only cell showing
   * nothing is a question the user cannot answer. Site Detail keeps them: there the site is stored
   * and the values are worth reading.
   */
  hiddenFields?: ReadonlySet<keyof SiteFormValues>;
  /**
   * Opens the road lookup. Omitted where there is nothing to look up — a read-only page — so the
   * control is absent rather than present and inert.
   */
  onFindRoad?: () => void;
};

/**
 * The form's own id, so the Save button beside the page heading can submit it from outside.
 *
 * <p>Exported rather than repeated: the two halves are in different files and a typo in either
 * leaves a button that silently does nothing.
 */
export const FORM_ID = 'site-form';

/** Shared so the default prop is one object rather than a new Set on every render. */
const EMPTY_HIDDEN: ReadonlySet<keyof SiteFormValues> = new Set();

/**
 * `122° 30′ 15.5″`, for a coordinate the user may read but not change.
 *
 * <p>Prime and double-prime, not apostrophe and quote: they are the characters the notation
 * actually uses, and a screen reader is no worse off for it. Empty when no part is filled, so the
 * cell falls back to the em dash rather than printing three bare symbols.
 */
const dms = (degrees: string, minutes: string, seconds: string, negative = false): string =>
  [degrees, minutes, seconds].every((part) => part.trim() === '')
    ? ''
    : `${negative ? '\u2212' : ''}${degrees || '0'}\u00b0 ${minutes || '0'}\u2032 ${seconds || '0'}\u2033`;

/**
 * `10 · 382875E · 7710784N`, for a UTM reference the user may read but not change.
 *
 * <p>The letters are what distinguish an easting from a northing on a map sheet, and without them
 * three numbers in a row say nothing about which is which. Empty when no part is filled, so the
 * cell falls back to the em dash.
 */
const utm = (values: SiteFormValues): string => {
  const parts = [
    values.utmZone,
    values.utmEasting ? `${values.utmEasting}E` : '',
    values.utmNorthing ? `${values.utmNorthing}N` : '',
  ].filter((part) => String(part).trim() !== '');
  return parts.join(' \u00b7 ');
};

/** "DCK - Chilliwack Natural Resource District", as everywhere else in the app. */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

/**
 * The Add Site form, ported from the legacy `site.jsp`.
 *
 * <p>Same fields, same labels, same order. Legacy lays them out as a four-column HTML table of
 * label/value pairs with hand-set percentage widths; this is the four-column CSS grid the search
 * screens use, so the two forms in the application look like each other.
 *
 * <p><b>Three fields change with the site type.</b> A recreation site has a Recreation District
 * and a Project Name where a crossing has a Forest District and a Forest Service Road, and a
 * storage site needs neither a Crossing Name nor a kilometre mark. Legacy re-renders the whole page
 * through `redisplay()` on every change of the type dropdown to achieve this; here the branch is in
 * the markup and nothing round-trips.
 */
const SiteForm: FC<Props> = ({
  values,
  errors,
  warnings = {},
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onSettle,
  onSave,
  isEditable = () => true,
  forestServiceRoad = '',
  forestServiceRoadLoading = false,
  roadResolved = false,
  hiddenFields = EMPTY_HIDDEN,
  onFindRoad,
}) => {
  const isRecreation = values.crossingSiteTypeCode === SITE_TYPE.RECREATION;
  const recreationDistricts = codeTables.recreationDistricts;
  // Neither is required of a storage site: it holds portable structures rather than spanning
  // anything, so it has no crossing to name and no point on a road to measure to. The marker
  // follows the rule rather than the other way round — an asterisk on a field nothing enforces
  // teaches the user to ignore asterisks.
  const isStorage = values.crossingSiteTypeCode === SITE_TYPE.STORAGE;
  /** All nine coordinate boxes move together, so one of them settles the layout for all of them. */
  const coordinatesAreValues = !isEditable('longitudeDegrees');

  /**
   * True when the road has settled the district and the user may not change it.
   *
   * <p>Never for a recreation site, which chooses from a list the file narrows. Otherwise whenever
   * the road resolves — and for everything except a storage site, even when it does not: legacy
   * leaves a crossing's district disabled and blank until a road is given, because a crossing
   * without a road has no district to record.
   */
  const districtIsDerived =
    !isRecreation && (roadResolved || values.crossingSiteTypeCode !== SITE_TYPE.STORAGE);

  /**
   * The district as it should read.
   *
   * <p>Falls back to the bare org unit number when the list does not contain it, which is not
   * hypothetical: legacy fills this field from the road's {@code FOREST_REGION}, and a region is
   * not one of the districts the list holds. Showing the number is worse than showing a name and
   * far better than showing nothing at all.
   */
  const derivedDistrict = (() => {
    const unit = codeTables.forestDistricts.find(
      (option) => String(option.orgUnitNo) === values.orgUnitNo,
    );
    if (unit) return label(unit.orgUnitCode, unit.orgUnitName);
    return values.orgUnitNo === '' ? '' : `Org unit ${values.orgUnitNo}`;
  })();

  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    onSave();
  };

  const text = (
    field: keyof SiteFormValues,
    labelText: string,
    maxLength: number,
    extra?: {
      placeholder?: string;
      hideLabel?: boolean;
      disabled?: boolean;
      helperText?: string;
      required?: boolean;
      /** Upper-case the value when the user leaves the box — see the Site # field. */
      uppercase?: boolean;
    },
  ) => {
    const { required, uppercase, ...rest } = extra ?? {};
    if (!isEditable(field)) {
      return <ReadOnlyField key={field} label={labelText} value={String(values[field] ?? '')} />;
    }
    return (
      <TextInput
        id={`site-form-${field}`}
        data-testid={`site-form-${field}`}
        labelText={requiredLabel(labelText, required)}
        maxLength={maxLength}
        value={String(values[field])}
        invalid={field in errors}
        invalidText={errors[field]}
        // Only when the field is not already in error: the rule that refuses the save outranks
        // the one that merely asks. Carbon prefers `invalid` over `warn` by itself, so this is
        // belt-and-braces — stated here because it is a decision rather than something to
        // rediscover from a component's internals, and it is not covered by a test for the same
        // reason: nothing it could be changed to would alter what appears.
        warn={!(field in errors) && field in warnings}
        warnText={warnings[field]}
        onChange={(event) => onChange(field, event.target.value as never)}
        // Leaving a box is what earns its message. Until then only the rules no further typing can
        // satisfy have anything to say — see utils/validation.
        onBlur={(event) => {
          // On exit rather than on every keystroke, which is where legacy puts it too
          // (`onchange="setSiteNumberToUpperCase()"`). Rewriting the box as the user types fights
          // the caret and makes a held shift key look broken.
          if (uppercase && event.target.value !== event.target.value.toUpperCase()) {
            onChange(field, event.target.value.toUpperCase() as never);
          }
          onSettle(field);
        }}
        {...rest}
      />
    );
  };

  const codeSelect = (
    field: keyof SiteFormValues,
    labelText: string,
    blankText: string,
    options: CodeOption[],
    required = false,
  ) =>
    !isEditable(field) ? (
      // The description, not the stored code: "Active", not "ACT". Falls back to the raw value if
      // the code table has not arrived or no longer carries it, which is better than an em dash on
      // a field that does have an answer.
      <ReadOnlyField
        label={labelText}
        value={
          options.find((option) => option.code === values[field])?.description ??
          String(values[field] ?? '')
        }
      />
    ) : (
      <Select
        id={`site-form-${field}`}
        data-testid={`site-form-${field}`}
        labelText={requiredLabel(labelText, required)}
        disabled={codeTablesLoading}
        invalid={field in errors}
        invalidText={errors[field]}
        warn={!(field in errors) && field in warnings}
        warnText={warnings[field]}
        value={String(values[field])}
        onChange={(event) => onChange(field, event.target.value as never)}
      >
        <SelectItem value="" text={blankText} />
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
    field: keyof SiteFormValues,
    labelText: string,
    blankText: string,
    options: OrgUnitOption[],
    disabled = codeTablesLoading,
    required = false,
  ) =>
    !isEditable(field) ? (
      <ReadOnlyField
        label={labelText}
        value={(() => {
          const unit = options.find((option) => String(option.orgUnitNo) === values[field]);
          return unit ? label(unit.orgUnitCode, unit.orgUnitName) : String(values[field] ?? '');
        })()}
      />
    ) : (
      <Select
        id={`site-form-${field}`}
        data-testid={`site-form-${field}`}
        labelText={requiredLabel(labelText, required)}
        disabled={disabled}
        invalid={field in errors}
        invalidText={errors[field]}
        warn={!(field in errors) && field in warnings}
        warnText={warnings[field]}
        value={String(values[field])}
        onChange={(event) => onChange(field, event.target.value as never)}
      >
        <SelectItem value="" text={blankText} />
        {options.map((unit) => (
          <SelectItem
            key={unit.orgUnitNo}
            value={String(unit.orgUnitNo)}
            text={label(unit.orgUnitCode, unit.orgUnitName)}
          />
        ))}
      </Select>
    );

  /**
   * A degrees / minutes / seconds triple.
   *
   * <p>The fieldset carries the group label, so a screen reader announces "Longitude, Degrees"
   * rather than a bare "Degrees" that could belong to either coordinate.
   */
  const coordinate = (
    labelText: string,
    degrees: keyof SiteFormValues,
    minutes: keyof SiteFormValues,
    seconds: keyof SiteFormValues,
    /** Whether the value is stored negative — see the sign rendered beside the boxes. */
    negative = false,
  ) =>
    !isEditable(degrees) ? (
      // One cell, not three. Degrees, minutes and seconds are one value — split across three
      // read-only cells they read as three separate facts, and two of them would be bare numbers
      // with no unit.
      <ReadOnlyField
        label={labelText}
        value={dms(
          String(values[degrees] ?? ''),
          String(values[minutes] ?? ''),
          String(values[seconds] ?? ''),
          negative,
        )}
      />
    ) : (
      <fieldset className="site-form__group">
        {/* Marked required, because legacy refuses the save without all three parts of each — its
          own comment above the check reads "// Making longitude mandatory". The marker sits on the
          group rather than on each box: the coordinate is one value, and three asterisks would
          read as three separate obligations. */}
        <legend className="cds--label">{requiredLabel(labelText, true)}</legend>
        {/* Labels kept but hidden, as the Kilometres range on Site Search does. The legend already
          names the value; a second row of visible labels under it gave every triple two label
          lines where the rest of the form has one, so nothing lined up down the page. The <label>
          survives for a screen reader, which reads "Longitude, Degrees". */}
        <div className="site-form__parts">
          {/* The minus legacy prints in front of the longitude boxes (`site.jsp:1036`), and the
              only thing on the screen that says the stored value is negative. The boxes take the
              magnitude — nobody types a sign on a British Columbian longitude — so without this
              the form shows 122 where the column holds -122. Not `aria-hidden`: it is read as part
              of the group, which is the one place it can be heard. */}
          {negative && (
            <span className="site-form__sign" data-testid="site-form-longitude-sign">
              &minus;
            </span>
          )}
          {text(degrees, 'Degrees', 4, { placeholder: 'Deg', hideLabel: true })}
          {text(minutes, 'Minutes', 3, { placeholder: 'Min', hideLabel: true })}
          {text(seconds, 'Seconds', 5, { placeholder: 'Sec', hideLabel: true })}
        </div>
      </fieldset>
    );

  return (
    <form id={FORM_ID} onSubmit={submit} data-testid="site-form" className="site-form__form">
      <div className="site-form__fields">
        {text('siteId', 'Site #', 14, {
          required: true,
          // `CROSSING_SITE_ID` is a natural key people read off a sign and type in, and it is
          // stored and matched exactly — so a lower-case entry is a different site from the one
          // the user meant. Legacy folds it for the same reason.
          uppercase: true,
        })}
        {codeSelect(
          'crossingSiteStatusCode',
          'Status',
          'Select a status',
          codeTables.siteStatusCodes,
          true,
        )}
        {codeSelect(
          'crossingSiteTypeCode',
          'Site Type',
          'Select a site type',
          codeTables.siteTypeCodes,
          true,
        )}
        {codeSelect(
          'structureInspectionStatusCode',
          'Inspection Status',
          'Select an inspection status',
          codeTables.structureInspectionStatusCodes,
          true,
        )}

        {/* Two halves of one identifier, on one line — as on the legacy form, and as on Site
            Search. */}
        <div className="site-form__paired">
          {text('forestFileId', 'Project File ID#', 10, { required: true })}
          {/* Not for a recreation site: no rule in legacy enforces it and `site.jsp:661` hides
              its error div there, so a "Br." — a branch of a road — is asked for only where
              there is a road. */}
          {text('roadSectionId', 'Br.', 30, { required: !isRecreation })}
          {/* The magnifying glass legacy puts beside these two boxes, opening its road search.
              Offered only while they can be changed — on a read-only page there is nothing for a
              chosen road to be written into. */}
          {onFindRoad && isEditable('forestFileId') && (
            <div className="site-form__find-road">
              <Button
                kind="ghost"
                size="md"
                hasIconOnly
                renderIcon={SearchIcon}
                iconDescription="Find a road"
                data-testid="site-form-find-road"
                onClick={onFindRoad}
              />
            </div>
          )}
        </div>

        {/* Same column either way — a recreation site records its district in ORG_UNIT_NO like any
            other. Only the label changes, because for a recreation site the district is the one
            administering the site rather than the road. */}
        {/* Who owns the Forest District, straight from legacy.
            
            A crossing is on a road and the road knows its district, so choosing a road chooses the
            district — `SiteAction` overwrites `orgUnitNo` from the road and `setValidateResponse`
            disables the field. A recreation site keeps the choice, but the project file narrows
            the list to the districts it is cross-referenced to. A storage site keeps the choice
            only while it has no road, because it may never have one.

            Read-only rather than a disabled select where the road decides it: the value is
            information, and a greyed-out dropdown invites a click that can do nothing. It also
            shows the value when the district list does not contain it — see `districtLabel`. */}
        {districtIsDerived ? (
          <ReadOnlyField label="Forest District" value={derivedDistrict} />
        ) : (
          orgUnitSelect(
            'orgUnitNo',
            isRecreation ? 'Recreation District' : 'Forest District',
            isRecreation && values.forestFileId.trim() === ''
              ? 'Enter a Project File ID# first'
              : 'Select a district',
            isRecreation ? recreationDistricts : codeTables.forestDistricts,
            codeTablesLoading,
            // A recreation site is identified by its project, not by a district's road network.
            !isRecreation,
          )
        )}

        {/* Read, never chosen. Legacy disables the client number and location code for every role
            and never calls the lookup that would fill them (`site.jsp:734-740`, `778-784`;
            `showClientSearch()` at 529 is dead), so no screen sets a maintainer. Add Site leaves
            the field out altogether — a site being created has none — and Site Detail shows what is
            stored. There is no third state, which is why there is no editable branch here. */}
        {hiddenFields.has('clientNumber') ? null : (
          <ReadOnlyField label="Designated Maintainer" value={values.maintainerLabel} />
        )}

        {/* Absent on a recreation site, as it is on legacy's form — the whole row sits inside
            `<c:if test="${SiteForm.crossingSiteTypeCode != 'REC'}">`. A management area is a former
            *forest* district, so there is nothing for a recreation site to pick: the list is built
            from the district in `orgUnitNo`, and on a recreation site that holds a recreation
            district, which no management area rolls up to. The field would be permanently empty.

            Enabled whenever it is shown, which is also legacy: the road that disables Forest
            District leaves this one alone, and the page's disable script never names it. */}
        {!isRecreation &&
          orgUnitSelect(
            'managementOrgUnitNo',
            'Management Area',
            values.orgUnitNo === '' ? 'Select a district first' : 'No management area',
            codeTables.managementAreas,
            codeTablesLoading || managementAreasLoading,
          )}

        {/* Derived, not entered — and disabled on the legacy form too, for every role. It is read
            back from the road record that Project File ID# and Br. identify, so it is bound to
            nothing here: it holds no value the form would submit, and giving it one of the real
            field names would put a second control on that field's id. The road lookup is not
            ported yet, so it says so rather than sitting blank with no explanation. */}
        {/* Read from the road record, never typed — legacy disables this box for every role and
            fills it from its own `getRoadDetails()` call. A value rather than a disabled input in
            both modes: there is nothing here to interact with, and a greyed-out box invites a
            click that does nothing.

            The em dash covers three cases that look alike and are not: the pair is incomplete, the
            pair names no section, or the section has no name upstream. The form cannot tell them
            apart and neither could legacy, which blanked the field for all three. */}
        <ReadOnlyField
          label={isRecreation ? 'Project Name' : 'Forest Service Road'}
          value={forestServiceRoad}
          emptyText={forestServiceRoadLoading ? 'Looking up\u2026' : '\u2014'}
        />

        {hiddenFields.has('businessAreaOrgUnitNo')
          ? null
          : orgUnitSelect(
              'businessAreaOrgUnitNo',
              'BCTS BA Responsible',
              'No business area',
              codeTables.businessAreas,
            )}

        {codeSelect(
          'specialAccessRqmtCode',
          'Special Access Requirements',
          'No requirement',
          codeTables.specialAccessCodes,
        )}
        {text('crossingName', 'Crossing Name', 255, { required: !isStorage })}
        {/* The three narrow cells, in one column between them rather than one each.

            Kilometres and User Kilometres are two measurements of the same thing — the distance
            from the point of commencement, and the distance posted on the sign — and reading one
            against the other is the point of having both. Capital Road is a single tick box. None
            of them needs a quarter of the form, and a column each left most of three columns empty
            and pushed them apart.

            `maxLength` is legacy's, but the column is the real limit: `NUMBER(8,2)` holds six
            digits ahead of the point and two behind, so "999999.99" — nine characters — is the
            longest value that can be stored. The tenth character legacy allows is one the database
            would refuse, which is why `KILOMETRE` in validation.ts is the narrower rule. */}
        <div className="site-form__narrow-group">
          {text('pointOfCommencementDistance', 'Kilometres', 10, { required: !isStorage })}
          {hiddenFields.has('userKm') ? null : text('userKm', 'User Kilometres', 10)}
          {/* Disabled, as it is in every branch of the legacy form — including for a Level 2 user.
              Capital Road is set by the road data, not on this screen. */}
          {/* Never editable on this screen, in legacy or here — it is set from the road record. In a
              read-only page it reads as a word rather than as a permanently greyed-out tick box. */}
          {!isEditable('capitalRoad') ? (
            <ReadOnlyField label="Capital Road" value={values.capitalRoad ? 'Yes' : 'No'} />
          ) : (
            <div className="site-form__checkbox">
              {/* An empty label, not a margin — the same device the road lookup button uses. A
                  checkbox carries its text beside the box rather than above it, so without this it
                  starts where every other field's *label* starts and sits a line above the controls
                  it shares a row with. Reserving whatever a label currently reserves keeps the two
                  in step if that ever changes; a hard offset would silently stop matching. */}
              <span className="cds--label" aria-hidden="true" />
              <Checkbox
                id="site-form-capitalRoad"
                data-testid="site-form-capitalRoad"
                labelText="Capital Road"
                checked={values.capitalRoad}
                disabled
                helperText="Set from the road record"
                onChange={(_event, { checked }) => onChange('capitalRoad', checked)}
              />
            </div>
          )}
        </div>

        {/* No heading above these. "Coordinates" named a group whose three members already say
            what they are, so it only added a line between the user and the boxes. */}
        {/* Side by side, because they are one point: a reader checks a pair of coordinates
            against each other, and splitting them across two rows puts the second where the eye
            has to go looking for it. UTM keeps its own row below — it says the same thing a
            different way rather than completing this. */}
        {/* Read as three ordinary cells, entered as three groups of boxes.
            A value needs no more room than the text of it, so read-only they take one grid column
            each like every other field and sit on one row. Editable they need nine inputs between
            them, which no single column holds — so longitude and latitude share a full-width row
            and UTM takes another. */}
        {coordinatesAreValues ? (
          <>
            <ReadOnlyField
              label="Longitude"
              // Signed, because the stored value is. Legacy's minus sits outside its `<c:choose>`,
              // so it shows whether the boxes are editable or not.
              value={dms(
                values.longitudeDegrees,
                values.longitudeMinutes,
                values.longitudeSeconds,
                true,
              )}
            />
            <ReadOnlyField
              label="Latitude"
              value={dms(values.latitudeDegrees, values.latitudeMinutes, values.latitudeSeconds)}
            />
            <ReadOnlyField label="UTM" value={utm(values)} />
          </>
        ) : (
          <>
            <div className="site-form__coordinate-pair">
              {coordinate(
                // Legacy's label, with legacy's minus sign beside the boxes rather than a word in
                // the label standing in for it.
                'Longitude',
                'longitudeDegrees',
                'longitudeMinutes',
                'longitudeSeconds',
                true,
              )}
              {coordinate('Latitude', 'latitudeDegrees', 'latitudeMinutes', 'latitudeSeconds')}
            </div>
            <fieldset className="site-form__group">
              <legend className="cds--label">UTM</legend>
              <div className="site-form__parts">
                {text('utmZone', 'Zone', 2, { placeholder: 'Zone', hideLabel: true })}
                {text('utmEasting', 'Easting', 10, { placeholder: 'Easting', hideLabel: true })}
                {text('utmNorthing', 'Northing', 10, {
                  placeholder: 'Northing',
                  hideLabel: true,
                })}
              </div>
            </fieldset>
          </>
        )}

        <div className="site-form__wide">
          {/* Counted, not capped. A `maxLength` would silently swallow the tail of anything pasted
              in — which is how someone loses the end of a paragraph without noticing — so the
              count turns red, the field goes invalid, and Save refuses instead. */}
          {!isEditable('pointOfAccessDescription') ? (
            <ReadOnlyField label="Site Details" value={values.pointOfAccessDescription} />
          ) : (
            <FieldWithCounter
              used={byteLength(values.pointOfAccessDescription)}
              limit={SITE_TEXT_LIMITS.pointOfAccessDescription}
            >
              <TextArea
                id="site-form-pointOfAccessDescription"
                data-testid="site-form-pointOfAccessDescription"
                labelText="Site Details"
                helperText="How to reach the site, and anything else worth knowing on arrival."
                // Six, where legacy sets five and this form used to set four. The field spans the
                // width of the form, so 255 bytes rarely fills even three lines — the extra height
                // is for the writing rather than the result: this is the one box on the screen that
                // takes a sentence, and a short one invites a short answer.
                rows={6}
                value={values.pointOfAccessDescription}
                invalid={'pointOfAccessDescription' in errors}
                invalidText={errors.pointOfAccessDescription}
                onChange={(event) => onChange('pointOfAccessDescription', event.target.value)}
                onBlur={() => onSettle('pointOfAccessDescription')}
              />
            </FieldWithCounter>
          )}
        </div>
      </div>
    </form>
  );
};

export default SiteForm;
