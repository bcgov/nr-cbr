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

import {
  OPEN_BOTTOM,
  OTHER,
  TEXT_MAX,
  type CulvertStored,
  type CulvertValues,
  type NumberField,
} from './culvertRules';
import { withStored, type FieldsProps } from './formFields';

import type { CodeValue } from './structureResponse';
import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useCulvertCodes } from '@/hooks/useConfiguration';
import { byteLength } from '@/utils/textLimits';

type Props = FieldsProps<CulvertValues> & {
  /** Which fields are required: those the culvert has stored, or all of them on a new culvert. */
  stored: CulvertStored;
  /** A Wood Log Culvert has no Culvert Type or Open Bottom Substructure, as legacy's page. */
  woodLog: boolean;
  /** The culvert's own codes, kept selectable though since expired. */
  storedCodes?: Partial<
    Record<'culvertType' | 'material' | 'headwallLocation' | 'openBottomSubstructure', CodeValue>
  >;
  /** Fields shown first in the grid — the card's Type/Class. */
  leading?: ReactNode;
};

/** The Culvert card's fields, for the card's edit and for Add Structure. */
const CulvertFields: FC<Props> = ({
  values,
  shown,
  warnings = {},
  change,
  leave,
  stored,
  woodLog,
  storedCodes = {},
  leading,
}) => {
  const codes = useCulvertCodes();

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
      onBlur={() => leave(field)}
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
      onBlur={() => leave(field)}
    >
      <SelectItem value="" text="None" />
      {options.map((option) => (
        <SelectItem key={option.code} value={option.code} text={option.description} />
      ))}
    </Select>
  );

  return (
    <div className="structure-detail__fields">
      {leading}
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
          withStored(codes.data?.culvertTypes, storedCodes.culvertType),
        )}
      {select(
        'materialCode',
        requiredLabel('Culvert Material', Boolean(stored.materialCode)),
        withStored(codes.data?.materials, storedCodes.material),
      )}
      {values.materialCode === OTHER && (
        <FieldWithCounter used={byteLength(values.materialComment)} limit={TEXT_MAX}>
          <TextArea
            id="culvert-materialComment"
            data-testid="culvert-materialComment"
            labelText={requiredLabel('If Other, please specify', true)}
            rows={4}
            value={values.materialComment}
            invalid={Boolean(shown.materialComment)}
            invalidText={shown.materialComment}
            onChange={(event) => change('materialComment', event.target.value)}
            onBlur={() => leave('materialComment')}
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
        withStored(codes.data?.headwallLocations, storedCodes.headwallLocation),
      )}
      {!woodLog &&
        select(
          'openBottomCode',
          requiredLabel('Open Bottom Substructure', values.culvertTypeCode === OPEN_BOTTOM),
          withStored(codes.data?.openBottomSubstructures, storedCodes.openBottomSubstructure),
        )}
    </div>
  );
};

export default CulvertFields;
