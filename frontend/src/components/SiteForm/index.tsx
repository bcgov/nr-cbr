import { Checkbox, Select, SelectItem, TextArea, TextInput } from '@carbon/react';

import ClientCombo from '@/components/core/ClientCombo';
import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { SITE_TEXT_LIMITS, SITE_TYPE, type SiteFormValues } from './types';

import './siteForm.scss';

import type { SiteErrors } from './validation';
import type { ClientSuggestion } from '@/types/client';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { FC, SubmitEventHandler } from 'react';

import { clientLabel } from '@/utils/clientSearch';
import { byteLength } from '@/utils/textLimits';

export type SiteCodeTables = {
  siteStatusCodes: CodeOption[];
  siteTypeCodes: CodeOption[];
  structureInspectionStatusCodes: CodeOption[];
  specialAccessCodes: CodeOption[];
  forestDistricts: OrgUnitOption[];
  managementAreas: OrgUnitOption[];
  businessAreas: OrgUnitOption[];
};

type Props = {
  values: SiteFormValues;
  errors: SiteErrors;
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
};

/**
 * The form's own id, so the Save button beside the page heading can submit it from outside.
 *
 * <p>Exported rather than repeated: the two halves are in different files and a typo in either
 * leaves a button that silently does nothing.
 */
export const FORM_ID = 'site-form';

/**
 * `122° 30′ 15.5″`, for a coordinate the user may read but not change.
 *
 * <p>Prime and double-prime, not apostrophe and quote: they are the characters the notation
 * actually uses, and a screen reader is no worse off for it. Empty when no part is filled, so the
 * cell falls back to the em dash rather than printing three bare symbols.
 */
const dms = (degrees: string, minutes: string, seconds: string): string =>
  [degrees, minutes, seconds].every((part) => part.trim() === '')
    ? ''
    : `${degrees || '0'}\u00b0 ${minutes || '0'}\u2032 ${seconds || '0'}\u2033`;

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
  codeTables,
  codeTablesLoading = false,
  managementAreasLoading = false,
  onChange,
  onSettle,
  onSave,
  isEditable = () => true,
}) => {
  const isRecreation = values.crossingSiteTypeCode === SITE_TYPE.RECREATION;
  // Neither is required of a storage site: it holds portable structures rather than spanning
  // anything, so it has no crossing to name and no point on a road to measure to. The marker
  // follows the rule rather than the other way round — an asterisk on a field nothing enforces
  // teaches the user to ignore asterisks.
  const isStorage = values.crossingSiteTypeCode === SITE_TYPE.STORAGE;
  /** All nine coordinate boxes move together, so one of them settles the layout for all of them. */
  const coordinatesAreValues = !isEditable('longitudeDegrees');

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
    },
  ) => {
    const { required, ...rest } = extra ?? {};
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
        onChange={(event) => onChange(field, event.target.value as never)}
        // Leaving a box is what earns its message. Until then only the rules no further typing can
        // satisfy have anything to say — see utils/validation.
        onBlur={() => onSettle(field)}
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
          survives for a screen reader, which reads "Longitude (west), Degrees". */}
        <div className="site-form__parts">
          {text(degrees, 'Degrees', 4, { placeholder: 'Deg', hideLabel: true })}
          {text(minutes, 'Minutes', 3, { placeholder: 'Min', hideLabel: true })}
          {text(seconds, 'Seconds', 5, { placeholder: 'Sec', hideLabel: true })}
        </div>
      </fieldset>
    );

  const selectMaintainer = (client: ClientSuggestion | null) => {
    onChange('clientNumber', client?.clientNumber ?? '');
    onChange('clientLocationCode', client?.clientLocnCode ?? '');
    onChange('maintainerLabel', client ? clientLabel(client) : '');
  };

  return (
    <form id={FORM_ID} onSubmit={submit} data-testid="site-form" className="site-form__form">
      <div className="site-form__fields">
        {text('siteId', 'Site #', 14, { required: true })}
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
          {text('roadSectionId', 'Br.', 30, { required: true })}
        </div>

        {/* Same column either way — a recreation site records its district in ORG_UNIT_NO like any
            other. Only the label changes, because for a recreation site the district is the one
            administering the site rather than the road. */}
        {orgUnitSelect(
          'orgUnitNo',
          isRecreation ? 'Recreation District' : 'Forest District',
          'Select a district',
          codeTables.forestDistricts,
          codeTablesLoading,
          // A recreation site is identified by its project, not by a district's road network.
          !isRecreation,
        )}

        {/* One lookup where legacy has two boxes, exactly as on Site Search: a pick fills the
            client number and the location code together, which is the pair CROSSING_SITE stores. */}
        {!isEditable('clientNumber') ? (
          <ReadOnlyField label="Designated Maintainer" value={values.maintainerLabel} />
        ) : (
          <ClientCombo
            id="site-form-maintainer"
            titleText="Designated Maintainer"
            selectedLabel={values.maintainerLabel}
            onSelect={selectMaintainer}
            // A typed term that resolves to nothing cannot be saved — unlike on Site Search, where it
            // is still a usable filter — so the field keeps only what was picked.
            onTermChange={() => {}}
          />
        )}

        {orgUnitSelect(
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
        {!isEditable('forestFileId') ? (
          <ReadOnlyField label={isRecreation ? 'Project Name' : 'Forest Service Road'} value="" />
        ) : (
          <TextInput
            id="site-form-derived-road"
            data-testid="site-form-derived-road"
            labelText={isRecreation ? 'Project Name' : 'Forest Service Road'}
            value=""
            disabled
            helperText="Looked up from the road record — not yet available"
            onChange={() => {}}
          />
        )}

        {/* A column each. They are two different measurements of the same thing — the distance
            measured from the point of commencement, and the distance posted on the sign — and
            reading one against the other is the point of having both. */}
        {text('pointOfCommencementDistance', 'Kilometres', 10, { required: !isStorage })}
        {text('userKm', 'User Kilometres', 10)}
        {text('crossingName', 'Crossing Name', 255, { required: !isStorage })}
        {orgUnitSelect(
          'businessAreaOrgUnitNo',
          'BCTS BA Responsible',
          'No business area',
          codeTables.businessAreas,
        )}

        {text('trimMapSheetNumber', 'Trim Map Sheet #', 10)}
        {text('ntsMapSheetNumber', '1:50,000 Map Sheet #', 10)}
        {codeSelect(
          'specialAccessRqmtCode',
          'Special Access Requirements',
          'No requirement',
          codeTables.specialAccessCodes,
        )}

        {/* Disabled, as it is in every branch of the legacy form — including for a Level 2 user.
            Capital Road is set by the road data, not on this screen. */}
        {/* Never editable on this screen, in legacy or here — it is set from the road record. In a
            read-only page it reads as a word rather than as a permanently greyed-out tick box. */}
        {!isEditable('capitalRoad') ? (
          <ReadOnlyField label="Capital Road" value={values.capitalRoad ? 'Yes' : 'No'} />
        ) : (
          <Checkbox
            id="site-form-capitalRoad"
            data-testid="site-form-capitalRoad"
            className="site-form__checkbox"
            labelText="Capital Road"
            checked={values.capitalRoad}
            disabled
            helperText="Set from the road record"
            onChange={(_event, { checked }) => onChange('capitalRoad', checked)}
          />
        )}

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
              label="Longitude (west)"
              value={dms(values.longitudeDegrees, values.longitudeMinutes, values.longitudeSeconds)}
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
                // Entered without a sign. Every site in the province is west of Greenwich, so the
                // value is negated when it is stored — legacy does the same rather than asking for
                // a minus sign on every record.
                'Longitude (west)',
                'longitudeDegrees',
                'longitudeMinutes',
                'longitudeSeconds',
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
