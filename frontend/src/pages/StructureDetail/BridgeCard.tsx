import { Edit, Road, WarningAltFilled } from '@carbon/icons-react';
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
import { describe, isOther, number, yesNo } from './format';

import type { BridgeDetails, BridgeDetailsRequest, CodeValue } from './structureResponse';
import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useBridgeCodes, useStructureTypeClassCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateBridgeDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

/** Legacy warns when a bridge has more spans than this. */
const SPANS_ADVISED = 10;
const DURABLE = 'PB';
const OTHER = 'OTH';
/** The two bridge Type/Classes, in legacy's order. */
const BRIDGE_TYPES = ['PB', 'TB'];
/** Superstructures a portable structure may have — legacy's `errors.structure.portablestructure`. */
const PORTABLE_SUPERSTRUCTURES = new Set(['SBB', 'SIB', 'STBA', 'TU', 'TT']);
/** What a Durable Bridge may not be made of. */
const TEMPORARY_SUPERSTRUCTURES = new Set(['LGST', 'RAIL', 'TU']);
const TEMPORARY_ABUTMENTS = new Set(['CLT', 'PT', 'ST']);
/** The comment columns, in bytes. */
const TEXT_MAX = 2000;

/** Number of Spans, with legacy's warning past ten. Kept by the Spans & Piers tab. */
const SpanCount: FC<{ count: number }> = ({ count }) => (
  <ReadOnlyField
    label="Number of Spans"
    value={
      <>
        {count}
        {/* Legacy's warning under the count; it blocks nothing. */}
        {count > SPANS_ADVISED && (
          <span className="structure-detail__field-warning" data-testid="span-count-warning">
            <WarningAltFilled size={16} aria-hidden="true" />
            Should not be greater than {SPANS_ADVISED}.
          </span>
        )}
      </>
    }
  />
);

/** The bridge's own fields, as the card shows them. */
const BridgeView: FC<{ bridge: BridgeDetails; identity: ReactNode }> = ({ bridge, identity }) => (
  <div className="structure-detail__fields">
    {identity}
    <SpanCount count={bridge.spanCount} />
    <ReadOnlyField label="Needle Beams?" value={yesNo(bridge.needleBeams)} />
    <ReadOnlyField label="Bridge Length (metres)" value={number(bridge.lengthMetres)} />
    <CodeWithOther
      label="Superstructure"
      value={bridge.superstructure}
      comment={bridge.superstructureComment}
    />
    <CodeWithOther label="Deck Type" value={bridge.deckType} comment={bridge.deckTypeComment} />
    <ReadOnlyField label="Deck Width (metres)" value={number(bridge.deckWidthMetres)} />
    <ReadOnlyField label="Running Surface" value={describe(bridge.runningSurface)} />
    <CodeWithOther label="Curb Type" value={bridge.curbType} comment={bridge.curbTypeComment} />
    <ReadOnlyField
      label="Left Abutment (Looking Downstream)"
      value={describe(bridge.leftAbutment)}
    />
    <ReadOnlyField
      label="Right Abutment (Looking Downstream)"
      value={describe(bridge.rightAbutment)}
    />
    {(isOther(bridge.leftAbutment) || isOther(bridge.rightAbutment)) && (
      <ReadOnlyField
        label="If Other (Right or Left), please specify"
        value={bridge.abutmentComment}
      />
    )}
  </div>
);

/** The form's values as typed: the lengths are text until saved. */
type Values = {
  typeClassCode: string;
  portable: boolean;
  needleBeams: boolean;
  totalBridgeLength: string;
  superstructureTypeCode: string;
  superstructureComment: string;
  deckTypeCode: string;
  deckTypeComment: string;
  deckWidth: string;
  runningSurfaceCode: string;
  curbTypeCode: string;
  curbTypeComment: string;
  leftAbutmentCode: string;
  rightAbutmentCode: string;
  abutmentComment: string;
};
type Field = keyof Values;
type Messages = Partial<Record<Field, string>>;

/** A typed length as a number, null when blank, or NaN when it is not a number with ≤ `scale` decimals. */
const toMetres = (typed: string, scale: number): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  const pattern = new RegExp(String.raw`^\d+(\.\d{0,${scale}})?$`);
  return pattern.test(trimmed) ? Number(trimmed) : Number.NaN;
};

/** What was stored, so a field that had a value is required (legacy's tier rule). */
type Stored = { [K in Field]?: boolean };

/** The form's blocking rules — the server's, checked as the values change. */
const errorsOf = (values: Values, stored: Stored): Messages => {
  const errors: Messages = {};
  const durable = values.typeClassCode === DURABLE;
  const metres = (field: Field, label: string, scale: number, max: number) => {
    const value = toMetres(values[field] as string, scale);
    if (value === null) {
      if (stored[field]) errors[field] = `${label} is required.`;
    } else if (Number.isNaN(value)) {
      errors[field] = `${label} must be a number of metres, with up to ${scale} decimal places.`;
    } else if (value > max) {
      errors[field] = `${label} can be at most ${max} metres.`;
    }
  };
  metres('totalBridgeLength', 'Bridge Length', 3, 99_999.999);
  metres('deckWidth', 'Deck Width', 2, 99_999.99);

  const code = (field: Field, label: string) => {
    if (!values[field] && stored[field]) errors[field] = `${label} is required.`;
  };
  code('superstructureTypeCode', 'Superstructure');
  code('deckTypeCode', 'Deck Type');
  code('runningSurfaceCode', 'Running Surface');
  code('leftAbutmentCode', 'Left Abutment');
  code('rightAbutmentCode', 'Right Abutment');
  if (!values.curbTypeCode && durable) {
    errors.curbTypeCode = 'Curb Type is required for a Durable Bridge.';
  }

  const other = (when: boolean, field: Field, label: string) => {
    if (!when) return;
    const text = values[field] as string;
    if (!text.trim()) errors[field] = `${label} is required.`;
    else if (overLimitError(text, TEXT_MAX)) errors[field] = overLimitError(text, TEXT_MAX);
  };
  other(values.superstructureTypeCode === OTHER, 'superstructureComment', 'Superstructure Details');
  other(values.deckTypeCode === OTHER, 'deckTypeComment', 'Deck Type Details');
  other(values.curbTypeCode === OTHER, 'curbTypeComment', 'Curb Type Description');
  other(
    values.leftAbutmentCode === OTHER || values.rightAbutmentCode === OTHER,
    'abutmentComment',
    'Abutment Details',
  );

  if (
    values.portable &&
    !errors.superstructureTypeCode &&
    !PORTABLE_SUPERSTRUCTURES.has(values.superstructureTypeCode)
  ) {
    errors.superstructureTypeCode =
      'Only Steel Box Beam, Steel I-beam, Steel Truss/Bailey/Acrow, Timber - Untreated Stringers and Timber - Treated Stringers are allowed when Portable Structure is checked.';
  }
  if (durable) {
    if (
      !errors.superstructureTypeCode &&
      TEMPORARY_SUPERSTRUCTURES.has(values.superstructureTypeCode)
    ) {
      errors.superstructureTypeCode =
        'Superstructure: temporary materials are not permitted in a Durable Bridge.';
    }
    if (!errors.leftAbutmentCode && TEMPORARY_ABUTMENTS.has(values.leftAbutmentCode)) {
      errors.leftAbutmentCode =
        'Left Abutment: temporary materials are not permitted in a Durable Bridge.';
    }
    if (!errors.rightAbutmentCode && TEMPORARY_ABUTMENTS.has(values.rightAbutmentCode)) {
      errors.rightAbutmentCode =
        'Right Abutment: temporary materials are not permitted in a Durable Bridge.';
    }
  }
  return errors;
};

/** Legacy's range warnings, shown beside a field; they never stopped its save. */
const warningsOf = (values: Values): Messages => {
  const warnings: Messages = {};
  const length = toMetres(values.totalBridgeLength, 3);
  if (length !== null && !Number.isNaN(length) && length > 400) {
    warnings.totalBridgeLength = 'Bridge Length is not in the range 0 through 400.';
  }
  const width = toMetres(values.deckWidth, 2);
  if (width !== null && !Number.isNaN(width) && width > 12) {
    warnings.deckWidth = 'Deck Width is not in the range 0 through 12.';
  }
  return warnings;
};

/** The current codes, with the bridge's own first when it has since expired. */
const withStored = (listed: CodeOption[] | undefined, stored: CodeValue): CodeOption[] => {
  const options = listed ?? [];
  return stored.code && !options.some((option) => option.code === stored.code)
    ? [{ code: stored.code, description: stored.description ?? stored.code }, ...options]
    : options;
};

const blankToEmpty = (value: number | null) => (value === null ? '' : String(value));

type FormProps = {
  structureId: string;
  bridge: BridgeDetails;
  typeClass: CodeValue;
  portable: boolean;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it.
 *
 * <p>Type/Class and Portable Structure are edited here with the fields their rules check: a
 * Durable Bridge needs a Curb Type and no temporary superstructure or abutment; a portable one a
 * portable superstructure. Each error shows once its field is left, then as it is corrected.
 */
const BridgeForm: FC<FormProps> = ({ structureId, bridge, typeClass, portable, onDone }) => {
  const codes = useBridgeCodes();
  const typeClasses = useStructureTypeClassCodes();
  const update = useUpdateBridgeDetails(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<Values>({
    typeClassCode: typeClass.code ?? '',
    portable,
    needleBeams: bridge.needleBeams,
    totalBridgeLength: blankToEmpty(bridge.lengthMetres),
    superstructureTypeCode: bridge.superstructure.code ?? '',
    superstructureComment: bridge.superstructureComment ?? '',
    deckTypeCode: bridge.deckType.code ?? '',
    deckTypeComment: bridge.deckTypeComment ?? '',
    deckWidth: blankToEmpty(bridge.deckWidthMetres),
    runningSurfaceCode: bridge.runningSurface.code ?? '',
    curbTypeCode: bridge.curbType.code ?? '',
    curbTypeComment: bridge.curbTypeComment ?? '',
    leftAbutmentCode: bridge.leftAbutment.code ?? '',
    rightAbutmentCode: bridge.rightAbutment.code ?? '',
    abutmentComment: bridge.abutmentComment ?? '',
  });
  // Legacy's tier rule: required once the bridge has one.
  const stored: Stored = {
    totalBridgeLength: bridge.lengthMetres !== null,
    deckWidth: bridge.deckWidthMetres !== null,
    superstructureTypeCode: Boolean(bridge.superstructure.code),
    deckTypeCode: Boolean(bridge.deckType.code),
    runningSurfaceCode: Boolean(bridge.runningSurface.code),
    leftAbutmentCode: Boolean(bridge.leftAbutment.code),
    rightAbutmentCode: Boolean(bridge.rightAbutment.code),
  };
  const form = useFieldErrors<Field>(errorsOf(values, stored), update.fieldErrors);
  const shown = form.shown;
  const warnings = warningsOf(values);

  const change = <K extends Field>(field: K, value: Values[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const request: BridgeDetailsRequest = {
      ...values,
      totalBridgeLength: toMetres(values.totalBridgeLength, 3),
      deckWidth: toMetres(values.deckWidth, 2),
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Bridge details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The bridge details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const durable = values.typeClassCode === DURABLE;
  const typeOptions = BRIDGE_TYPES.map((code) => ({
    code,
    description:
      typeClasses.data?.find((option) => option.code === code)?.description ??
      (code === DURABLE ? 'Durable Bridge' : 'Non-Durable Bridge'),
  }));

  const select = (
    field: Field,
    label: ReactNode,
    options: CodeOption[],
    blank: string,
    disabled = codes.isLoading,
  ) => (
    <Select
      id={`bridge-${field}`}
      data-testid={`bridge-${field}`}
      labelText={label}
      value={values[field] as string}
      disabled={disabled}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    >
      <SelectItem value="" text={blank} />
      {options.map((option) => (
        <SelectItem key={option.code} value={option.code} text={option.description} />
      ))}
    </Select>
  );

  const metres = (field: Field, label: ReactNode, maxLength: number) => (
    <TextInput
      id={`bridge-${field}`}
      data-testid={`bridge-${field}`}
      labelText={label}
      inputMode="decimal"
      maxLength={maxLength}
      value={values[field] as string}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      warn={!shown[field] && Boolean(warnings[field])}
      warnText={warnings[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    />
  );

  const details = (field: Field, label: string) => (
    <FieldWithCounter used={byteLength(values[field] as string)} limit={TEXT_MAX}>
      <TextArea
        id={`bridge-${field}`}
        data-testid={`bridge-${field}`}
        labelText={requiredLabel(label, true)}
        rows={2}
        value={values[field] as string}
        invalid={Boolean(shown[field])}
        invalidText={shown[field]}
        onChange={(event) => change(field, event.target.value)}
        onBlur={() => form.leave(field)}
      />
    </FieldWithCounter>
  );

  const yesNoField = (field: 'portable' | 'needleBeams', label: string) => (
    <RadioButtonGroup
      name={`bridge-${field}`}
      legendText={requiredLabel(label, true)}
      valueSelected={values[field] ? 'Y' : 'N'}
      onChange={(selected) => change(field, selected === 'Y')}
    >
      <RadioButton id={`bridge-${field}-yes`} labelText="Yes" value="Y" />
      <RadioButton id={`bridge-${field}-no`} labelText="No" value="N" />
    </RadioButtonGroup>
  );

  return (
    <div className="structure-detail__edit" data-testid="bridge-details-form">
      <div className="structure-detail__fields">
        {select(
          'typeClassCode',
          requiredLabel('Type/Class', true),
          typeOptions,
          'Choose a type',
          typeClasses.isLoading,
        )}
        {yesNoField('portable', 'Portable Structure?')}
        <SpanCount count={bridge.spanCount} />
        {yesNoField('needleBeams', 'Needle Beams?')}
        {metres(
          'totalBridgeLength',
          requiredLabel('Bridge Length (metres)', Boolean(stored.totalBridgeLength)),
          9,
        )}
        {select(
          'superstructureTypeCode',
          requiredLabel(
            'Superstructure',
            Boolean(stored.superstructureTypeCode) || values.portable,
          ),
          withStored(codes.data?.superstructureTypes, bridge.superstructure),
          'None',
        )}
        {values.superstructureTypeCode === OTHER &&
          details('superstructureComment', 'Superstructure Details')}
        {select(
          'deckTypeCode',
          requiredLabel('Deck Type', Boolean(stored.deckTypeCode)),
          withStored(codes.data?.deckTypes, bridge.deckType),
          'None',
        )}
        {values.deckTypeCode === OTHER && details('deckTypeComment', 'Deck Type Details')}
        {metres('deckWidth', requiredLabel('Deck Width (metres)', Boolean(stored.deckWidth)), 8)}
        {select(
          'runningSurfaceCode',
          requiredLabel('Running Surface', Boolean(stored.runningSurfaceCode)),
          withStored(codes.data?.runningSurfaces, bridge.runningSurface),
          'None',
        )}
        {select(
          'curbTypeCode',
          requiredLabel('Curb Type', durable),
          withStored(codes.data?.curbTypes, bridge.curbType),
          'None',
        )}
        {values.curbTypeCode === OTHER && details('curbTypeComment', 'Curb Type Description')}
        {select(
          'leftAbutmentCode',
          requiredLabel('Left Abutment (Looking Downstream)', Boolean(stored.leftAbutmentCode)),
          withStored(codes.data?.abutments, bridge.leftAbutment),
          'None',
        )}
        {select(
          'rightAbutmentCode',
          requiredLabel('Right Abutment (Looking Downstream)', Boolean(stored.rightAbutmentCode)),
          withStored(codes.data?.abutments, bridge.rightAbutment),
          'None',
        )}
        {(values.leftAbutmentCode === OTHER || values.rightAbutmentCode === OTHER) &&
          details('abutmentComment', 'Abutment Details (Right or Left)')}
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
          data-testid="bridge-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  bridge: BridgeDetails;
  typeClass: CodeValue;
  portable: boolean;
  /** Type/Class and Portable as read-only fields, first in the view. */
  identity: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Bridge card — legacy's bridge rows with the structure's Type/Class and Portable
 * Structure — with its own "Edit bridge" for Level 2 and up, legacy's `/level2Access` on every one
 * of them. The edit replaces the values in place, as the Structure card does.
 */
const BridgeCard: FC<Props> = ({
  structureId,
  bridge,
  typeClass,
  portable,
  identity,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Bridge"
      icon={Road}
      testId="structure-section-bridge"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="bridge-details-edit"
            onClick={onEdit}
          >
            Edit bridge
          </Button>
        )
      }
    >
      {editing ? (
        <BridgeForm
          structureId={structureId}
          bridge={bridge}
          typeClass={typeClass}
          portable={portable}
          onDone={onDone}
        />
      ) : (
        <BridgeView bridge={bridge} identity={identity} />
      )}
    </Card>
  );
};

export default BridgeCard;
