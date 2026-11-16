import {
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import {
  DURABLE,
  OTHER,
  STANDARD_DECK_WIDTHS,
  TEXT_MAX,
  standardDeckWidth,
  type BridgeStored,
  type BridgeValues,
} from './bridgeRules';
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

const OTHER_WIDTH = 'OTHER';

type DeckWidthProps = {
  /** The width as typed, in metres — what is saved, whichever way it was chosen. */
  value: string;
  error?: string;
  warning?: string;
  required: boolean;
  change: (value: string) => void;
  leave: () => void;
};

/**
 * Deck Width: a choice of the standard widths, or Other with the width typed (UAT, "CBR Test
 * Changes", 2026-11). The stored column stays a number, so the choice is only how it is entered.
 *
 * <p>A stored width that is neither standard one shows as Other with its value. Once Other is
 * picked it stays picked while the width is typed — typing 4.27 there does not snap the choice
 * back and take the box away mid-word.
 */
const DeckWidthField: FC<DeckWidthProps> = ({ value, error, warning, required, change, leave }) => {
  const standard = standardDeckWidth(value);
  const [otherPicked, setOtherPicked] = useState(() => value.trim() !== '' && standard === null);
  let choice = '';
  if (otherPicked) choice = OTHER_WIDTH;
  else if (standard) choice = standard;
  else if (value.trim()) choice = OTHER_WIDTH;
  const typing = choice === OTHER_WIDTH;

  const choose = (picked: string) => {
    setOtherPicked(picked === OTHER_WIDTH);
    // Other starts blank, so a standard width chosen before is not saved by mistake.
    change(picked === OTHER_WIDTH ? '' : picked);
  };

  return (
    <>
      <Select
        id="bridge-deckWidth-choice"
        data-testid="bridge-deckWidth-choice"
        labelText={requiredLabel('Deck Width (metres)', required)}
        value={choice}
        invalid={!typing && Boolean(error)}
        invalidText={error}
        onChange={(event) => choose(event.target.value)}
        onBlur={leave}
      >
        <SelectItem value="" text="None" />
        {STANDARD_DECK_WIDTHS.map((width) => (
          <SelectItem key={width} value={width} text={width} />
        ))}
        <SelectItem value={OTHER_WIDTH} text="Other" />
      </Select>
      {typing && (
        <TextInput
          id="bridge-deckWidth"
          data-testid="bridge-deckWidth"
          labelText={requiredLabel('If Other, Deck Width (metres)', true)}
          inputMode="decimal"
          maxLength={8}
          value={value}
          invalid={Boolean(error)}
          invalidText={error}
          warn={!error && Boolean(warning)}
          warnText={warning}
          onChange={(event: ChangeEvent<HTMLInputElement>) => change(event.target.value)}
          onBlur={leave}
        />
      )}
    </>
  );
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
      <DeckWidthField
        value={values.deckWidth}
        error={shown.deckWidth}
        warning={warnings.deckWidth}
        required={Boolean(stored.deckWidth)}
        change={(value) => change('deckWidth', value)}
        leave={() => leave('deckWidth')}
      />
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
