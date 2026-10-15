import {
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import { DURABLE, OTHER, TEXT_MAX, type BridgeStored, type BridgeValues } from './bridgeRules';
import { withStored, type FieldsProps } from './formFields';

import type { CodeValue } from './structureResponse';
import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useBridgeCodes } from '@/hooks/useConfiguration';
import { byteLength } from '@/utils/textLimits';

type Field = keyof BridgeValues;

type Props = FieldsProps<BridgeValues> & {
  /** Which fields are required: those the bridge has stored, or all of them on a new bridge. */
  stored: BridgeStored;
  /** The bridge's own codes, kept selectable though since expired. */
  storedCodes?: Partial<
    Record<
      | 'superstructure'
      | 'deckType'
      | 'runningSurface'
      | 'curbType'
      | 'leftAbutment'
      | 'rightAbutment',
      CodeValue
    >
  >;
  /** Fields shown first in the grid — the card's Type/Class. */
  leading?: ReactNode;
  /** Shown after Portable Structure — the card's Number of Spans. */
  afterPortable?: ReactNode;
};

/**
 * The Bridge card's fields, from Portable Structure to the abutments, for the card's edit and for
 * Add Structure. A Durable Bridge needs a Curb Type and no temporary superstructure or abutment; a
 * portable one a portable superstructure; an Other its details.
 */
const BridgeFields: FC<Props> = ({
  values,
  shown,
  warnings = {},
  change,
  leave,
  stored,
  storedCodes = {},
  leading,
  afterPortable,
}) => {
  const codes = useBridgeCodes();
  const durable = values.typeClassCode === DURABLE;

  const select = (field: Field, label: ReactNode, options: CodeOption[]) => (
    <Select
      id={`bridge-${field}`}
      data-testid={`bridge-${field}`}
      labelText={label}
      value={values[field] as string}
      disabled={codes.isLoading}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event) => change(field, event.target.value)}
      onBlur={() => leave(field)}
    >
      <SelectItem value="" text="None" />
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
      onBlur={() => leave(field)}
    />
  );

  const details = (field: Field, label: string) => (
    <FieldWithCounter used={byteLength(values[field] as string)} limit={TEXT_MAX}>
      <TextArea
        id={`bridge-${field}`}
        data-testid={`bridge-${field}`}
        labelText={requiredLabel(label, true)}
        rows={4}
        value={values[field] as string}
        invalid={Boolean(shown[field])}
        invalidText={shown[field]}
        onChange={(event) => change(field, event.target.value)}
        onBlur={() => leave(field)}
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
    <div className="structure-detail__fields">
      {leading}
      {yesNoField('portable', 'Portable Structure?')}
      {afterPortable}
      {yesNoField('needleBeams', 'Needle Beams?')}
      {metres(
        'totalBridgeLength',
        requiredLabel('Bridge Length (metres)', Boolean(stored.totalBridgeLength)),
        9,
      )}
      {select(
        'superstructureTypeCode',
        requiredLabel('Superstructure', Boolean(stored.superstructureTypeCode) || values.portable),
        withStored(codes.data?.superstructureTypes, storedCodes.superstructure),
      )}
      {values.superstructureTypeCode === OTHER &&
        details('superstructureComment', 'Superstructure Details')}
      {select(
        'deckTypeCode',
        requiredLabel('Deck Type', Boolean(stored.deckTypeCode)),
        withStored(codes.data?.deckTypes, storedCodes.deckType),
      )}
      {values.deckTypeCode === OTHER && details('deckTypeComment', 'Deck Type Details')}
      {metres('deckWidth', requiredLabel('Deck Width (metres)', Boolean(stored.deckWidth)), 8)}
      {select(
        'runningSurfaceCode',
        requiredLabel('Running Surface', Boolean(stored.runningSurfaceCode)),
        withStored(codes.data?.runningSurfaces, storedCodes.runningSurface),
      )}
      {select(
        'curbTypeCode',
        requiredLabel('Curb Type', durable),
        withStored(codes.data?.curbTypes, storedCodes.curbType),
      )}
      {values.curbTypeCode === OTHER && details('curbTypeComment', 'Curb Type Description')}
      {select(
        'leftAbutmentCode',
        requiredLabel('Left Abutment (Looking Downstream)', Boolean(stored.leftAbutmentCode)),
        withStored(codes.data?.abutments, storedCodes.leftAbutment),
      )}
      {select(
        'rightAbutmentCode',
        requiredLabel('Right Abutment (Looking Downstream)', Boolean(stored.rightAbutmentCode)),
        withStored(codes.data?.abutments, storedCodes.rightAbutment),
      )}
      {(values.leftAbutmentCode === OTHER || values.rightAbutmentCode === OTHER) &&
        details('abutmentComment', 'Abutment Details (Right or Left)')}
    </div>
  );
};

export default BridgeFields;
