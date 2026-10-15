import { Edit, Pipelines } from '@carbon/icons-react';
import {
  Button,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import Card from './Card';
import CodeWithOther from './CodeWithOther';
import { describe, number } from './format';

import type { CodeValue, CulvertDetails, CulvertDetailsRequest } from './structureResponse';
import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useCulvertCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateCulvertDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

const OPEN_BOTTOM = 'OPN';
const OTHER = 'OTH';
/** `CULVERT_MATERIAL_COMMENT VARCHAR2(2000)`. */
const TEXT_MAX = 2000;

/** The culvert's own fields, as the card shows them. Wood log culverts have no type or open bottom. */
const CulvertView: FC<{ culvert: CulvertDetails; woodLog: boolean; identity: ReactNode }> = ({
  culvert,
  woodLog,
  identity,
}) => (
  <div className="structure-detail__fields">
    {identity}
    <ReadOnlyField
      label="Culvert Number"
      value={
        culvert.culvertNumber === null
          ? null
          : `${culvert.culvertNumber} of ${culvert.culvertsOnSite}`
      }
    />
    <ReadOnlyField label="Culvert Length (metres)" value={number(culvert.lengthMetres)} />
    <ReadOnlyField label="Gradient" value={number(culvert.gradient)} />
    {!woodLog && <ReadOnlyField label="Culvert Type" value={describe(culvert.culvertType)} />}
    <CodeWithOther
      label="Culvert Material"
      value={culvert.material}
      comment={culvert.materialComment}
    />
    <ReadOnlyField label="Inlet Cover Depth (mm)" value={number(culvert.inletCoverDepthMm)} />
    <ReadOnlyField label="Outlet Cover Depth (mm)" value={number(culvert.outletCoverDepthMm)} />
    <ReadOnlyField label="Opening Height (mm)" value={number(culvert.openingHeightMm)} />
    <ReadOnlyField label="Opening Width (mm)" value={number(culvert.openingWidthMm)} />
    <ReadOnlyField label="Headwall Location" value={describe(culvert.headwallLocation)} />
    {!woodLog && (
      <ReadOnlyField
        label="Open Bottom Substructure"
        value={describe(culvert.openBottomSubstructure)}
      />
    )}
  </div>
);

/** The form's values as typed: the numbers are text until saved. */
type Values = {
  portable: boolean;
  culvertNumber: string;
  lengthMetres: string;
  gradient: string;
  culvertTypeCode: string;
  materialCode: string;
  materialComment: string;
  inletCoverDepthMm: string;
  outletCoverDepthMm: string;
  openingHeightMm: string;
  openingWidthMm: string;
  headwallLocationCode: string;
  openBottomCode: string;
};
type Field = keyof Values;
type Messages = Partial<Record<Field, string>>;
type NumberField =
  | 'culvertNumber'
  | 'lengthMetres'
  | 'gradient'
  | 'inletCoverDepthMm'
  | 'outletCoverDepthMm'
  | 'openingHeightMm'
  | 'openingWidthMm';

/** How each number is typed and what its column holds. */
const NUMBERS: Record<
  NumberField,
  { label: string; pattern: RegExp; max: number; format: string }
> = {
  culvertNumber: {
    label: 'Culvert Number',
    pattern: /^\d+$/,
    max: 99_999,
    format: 'a whole number',
  },
  lengthMetres: {
    label: 'Culvert Length',
    pattern: /^\d+(\.\d?)?$/,
    max: 99_999.9,
    format: 'metres, with up to 1 decimal place',
  },
  gradient: {
    label: 'Gradient',
    pattern: /^-?\d+(\.\d?)?$/,
    max: 999.9,
    format: 'a number with up to 1 decimal place',
  },
  inletCoverDepthMm: {
    label: 'Inlet Cover Depth',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  outletCoverDepthMm: {
    label: 'Outlet Cover Depth',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  openingHeightMm: {
    label: 'Opening Height',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  openingWidthMm: {
    label: 'Opening Width',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
};

/** A typed number, null when blank, or NaN when it is not as the field takes it. */
const toNumber = (field: NumberField, typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  const { pattern, max } = NUMBERS[field];
  return pattern.test(trimmed) && Math.abs(Number(trimmed)) <= max ? Number(trimmed) : Number.NaN;
};

/** What was stored, so a field that had a value is required (legacy's tier rule). */
type Stored = Partial<Record<Field, boolean>>;

/** The numbers: required once stored, and as their columns hold them. */
const numberErrors = (values: Values, stored: Stored): Messages => {
  const errors: Messages = {};
  for (const field of Object.keys(NUMBERS) as NumberField[]) {
    const value = toNumber(field, values[field]);
    const { label, format } = NUMBERS[field];
    if (value === null && stored[field]) errors[field] = `${label} is required.`;
    else if (Number.isNaN(value))
      errors[field] = `${label} must be ${format}, as the column holds.`;
  }
  return errors;
};

/** Culvert Type, once stored, and Open Bottom Substructure for an open-bottom culvert. */
const typeErrors = (values: Values, stored: Stored): Messages => {
  const errors: Messages = {};
  if (!values.culvertTypeCode && stored.culvertTypeCode) {
    errors.culvertTypeCode = 'Culvert Type is required.';
  }
  if (values.culvertTypeCode === OPEN_BOTTOM && !values.openBottomCode) {
    errors.openBottomCode = 'Open Bottom Substructure is required for an open-bottom culvert.';
  }
  return errors;
};

/** Culvert Material, once stored, and an Other's details. */
const materialErrors = (values: Values, stored: Stored): Messages => {
  const errors: Messages = {};
  if (!values.materialCode && stored.materialCode) {
    errors.materialCode = 'Culvert Material is required.';
  }
  if (values.materialCode === OTHER) {
    errors.materialComment = values.materialComment.trim()
      ? overLimitError(values.materialComment, TEXT_MAX) || undefined
      : 'Culvert Details is required.';
  }
  return errors;
};

/** The form's blocking rules — the server's, checked as the values change. */
const errorsOf = (values: Values, stored: Stored, woodLog: boolean): Messages => ({
  ...numberErrors(values, stored),
  // A Wood Log Culvert has no Culvert Type or Open Bottom Substructure.
  ...(woodLog ? {} : typeErrors(values, stored)),
  ...materialErrors(values, stored),
});

/** Legacy's range warnings (`Culvert.validate`), shown as typed; they never stopped its save. */
const RANGES: Partial<Record<NumberField, [number, number]>> = {
  culvertNumber: [1, 10],
  lengthMetres: [5, 100],
  gradient: [0, 20],
  inletCoverDepthMm: [300, 5_000],
  outletCoverDepthMm: [300, 5_000],
  openingHeightMm: [300, 3_000],
  openingWidthMm: [300, 6_000],
};

const warningsOf = (values: Values): Messages => {
  const warnings: Messages = {};
  for (const [field, [low, high]] of Object.entries(RANGES) as [NumberField, [number, number]][]) {
    const value = toNumber(field, values[field]);
    if (value !== null && !Number.isNaN(value) && (value < low || value > high)) {
      warnings[field] =
        `${NUMBERS[field].label} is not in the range ${low.toLocaleString()} through ${high.toLocaleString()}.`;
    }
  }
  return warnings;
};

/** The current codes, with the culvert's own first when it has since expired. */
const withStored = (listed: CodeOption[] | undefined, stored: CodeValue): CodeOption[] => {
  const options = listed ?? [];
  return stored.code && !options.some((option) => option.code === stored.code)
    ? [{ code: stored.code, description: stored.description ?? stored.code }, ...options]
    : options;
};

const blankToEmpty = (value: number | null) => (value === null ? '' : String(value));

type FormProps = {
  structureId: string;
  culvert: CulvertDetails;
  portable: boolean;
  woodLog: boolean;
  /** Type/Class, read-only: a culvert's cannot change. */
  typeClass: ReactNode;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it. A Wood
 * Log Culvert has no Culvert Type or Open Bottom Substructure, as legacy's page hides them; it is
 * saved as BOX. Each error shows once its field is left, then as it is corrected.
 */
const CulvertForm: FC<FormProps> = ({
  structureId,
  culvert,
  portable,
  woodLog,
  typeClass,
  onDone,
}) => {
  const codes = useCulvertCodes();
  const update = useUpdateCulvertDetails(structureId);
  const { display } = useNotification();
  const [values, setValues] = useState<Values>({
    portable,
    culvertNumber: blankToEmpty(culvert.culvertNumber),
    lengthMetres: blankToEmpty(culvert.lengthMetres),
    gradient: blankToEmpty(culvert.gradient),
    culvertTypeCode: culvert.culvertType.code ?? '',
    materialCode: culvert.material.code ?? '',
    materialComment: culvert.materialComment ?? '',
    inletCoverDepthMm: blankToEmpty(culvert.inletCoverDepthMm),
    outletCoverDepthMm: blankToEmpty(culvert.outletCoverDepthMm),
    openingHeightMm: blankToEmpty(culvert.openingHeightMm),
    openingWidthMm: blankToEmpty(culvert.openingWidthMm),
    headwallLocationCode: culvert.headwallLocation.code ?? '',
    openBottomCode: culvert.openBottomSubstructure.code ?? '',
  });
  // Legacy's tier rule: required once the culvert has one.
  const stored: Stored = {
    culvertNumber: culvert.culvertNumber !== null,
    lengthMetres: culvert.lengthMetres !== null,
    openingHeightMm: culvert.openingHeightMm !== null,
    openingWidthMm: culvert.openingWidthMm !== null,
    culvertTypeCode: Boolean(culvert.culvertType.code),
    materialCode: Boolean(culvert.material.code),
  };
  const form = useFieldErrors<Field>(errorsOf(values, stored, woodLog), update.fieldErrors);
  const shown = form.shown;
  const warnings = warningsOf(values);

  const change = <K extends Field>(field: K, value: Values[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const request: CulvertDetailsRequest = {
      ...values,
      culvertNumber: toNumber('culvertNumber', values.culvertNumber),
      lengthMetres: toNumber('lengthMetres', values.lengthMetres),
      gradient: toNumber('gradient', values.gradient),
      inletCoverDepthMm: toNumber('inletCoverDepthMm', values.inletCoverDepthMm),
      outletCoverDepthMm: toNumber('outletCoverDepthMm', values.outletCoverDepthMm),
      openingHeightMm: toNumber('openingHeightMm', values.openingHeightMm),
      openingWidthMm: toNumber('openingWidthMm', values.openingWidthMm),
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Culvert details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The culvert details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const numberBox = (field: NumberField, label: string, maxLength: number) => (
    <TextInput
      id={`culvert-${field}`}
      data-testid={`culvert-${field}`}
      labelText={requiredLabel(label, Boolean(stored[field]))}
      inputMode={field === 'gradient' || field === 'lengthMetres' ? 'decimal' : 'numeric'}
      maxLength={maxLength}
      value={values[field]}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      warn={!shown[field] && Boolean(warnings[field])}
      warnText={warnings[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    />
  );

  const select = (
    field: 'culvertTypeCode' | 'materialCode' | 'headwallLocationCode' | 'openBottomCode',
    label: ReactNode,
    options: CodeOption[],
  ) => (
    <Select
      id={`culvert-${field}`}
      data-testid={`culvert-${field}`}
      labelText={label}
      value={values[field]}
      disabled={codes.isLoading}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    >
      <SelectItem value="" text="None" />
      {options.map((option) => (
        <SelectItem key={option.code} value={option.code} text={option.description} />
      ))}
    </Select>
  );

  return (
    <div className="structure-detail__edit" data-testid="culvert-details-form">
      <div className="structure-detail__fields">
        {typeClass}
        <RadioButtonGroup
          name="culvert-portable"
          legendText={requiredLabel('Portable Structure?', true)}
          valueSelected={values.portable ? 'Y' : 'N'}
          onChange={(selected) => change('portable', selected === 'Y')}
        >
          <RadioButton id="culvert-portable-yes" labelText="Yes" value="Y" />
          <RadioButton id="culvert-portable-no" labelText="No" value="N" />
        </RadioButtonGroup>
        {numberBox('culvertNumber', 'Culvert Number', 5)}
        {numberBox('lengthMetres', 'Culvert Length (metres)', 7)}
        {numberBox('gradient', 'Gradient', 6)}
        {!woodLog &&
          select(
            'culvertTypeCode',
            requiredLabel('Culvert Type', Boolean(stored.culvertTypeCode)),
            withStored(codes.data?.culvertTypes, culvert.culvertType),
          )}
        {select(
          'materialCode',
          requiredLabel('Culvert Material', Boolean(stored.materialCode)),
          withStored(codes.data?.materials, culvert.material),
        )}
        {values.materialCode === OTHER && (
          <FieldWithCounter used={byteLength(values.materialComment)} limit={TEXT_MAX}>
            <TextArea
              id="culvert-materialComment"
              data-testid="culvert-materialComment"
              labelText={requiredLabel('If Other, please specify', true)}
              rows={2}
              value={values.materialComment}
              invalid={Boolean(shown.materialComment)}
              invalidText={shown.materialComment}
              onChange={(event) => change('materialComment', event.target.value)}
              onBlur={() => form.leave('materialComment')}
            />
          </FieldWithCounter>
        )}
        {numberBox('inletCoverDepthMm', 'Inlet Cover Depth (mm)', 10)}
        {numberBox('outletCoverDepthMm', 'Outlet Cover Depth (mm)', 10)}
        {numberBox('openingHeightMm', 'Opening Height (mm)', 10)}
        {numberBox('openingWidthMm', 'Opening Width (mm)', 10)}
        {select(
          'headwallLocationCode',
          'Headwall Location',
          withStored(codes.data?.headwallLocations, culvert.headwallLocation),
        )}
        {!woodLog &&
          select(
            'openBottomCode',
            requiredLabel('Open Bottom Substructure', values.culvertTypeCode === OPEN_BOTTOM),
            withStored(codes.data?.openBottomSubstructures, culvert.openBottomSubstructure),
          )}
      </div>

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          kind="tertiary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          kind="primary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={save}
          disabled={update.isPending}
          data-testid="culvert-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  culvert: CulvertDetails;
  portable: boolean;
  woodLog: boolean;
  /** Type/Class and Portable as read-only fields, first in the view. */
  identity: ReactNode;
  /** Type/Class alone, read-only, first in the form. */
  typeClass: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Culvert card — legacy's culvert rows with the structure's Portable Structure —
 * with its own "Edit culvert" for Level 2 and up, legacy's `/level2Access` on every one of them.
 * The edit replaces the values in place, as the Bridge card does.
 */
const CulvertCard: FC<Props> = ({
  structureId,
  culvert,
  portable,
  woodLog,
  identity,
  typeClass,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Culvert"
      icon={Pipelines}
      testId="structure-section-culvert"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="culvert-details-edit"
            onClick={onEdit}
          >
            Edit culvert
          </Button>
        )
      }
    >
      {editing ? (
        <CulvertForm
          structureId={structureId}
          culvert={culvert}
          portable={portable}
          woodLog={woodLog}
          typeClass={typeClass}
          onDone={onDone}
        />
      ) : (
        <CulvertView culvert={culvert} woodLog={woodLog} identity={identity} />
      )}
    </Card>
  );
};

export default CulvertCard;
