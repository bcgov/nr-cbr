import { RadioButton, RadioButtonGroup, Select, SelectItem, TextInput } from '@carbon/react';
import { useMemo } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import { withStored, type FieldsProps } from './formFields';
import { SOURCES_NEEDING_YEAR_ADDED, type StructureValues } from './structureRules';

import type { CodeValue } from './structureResponse';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useBuiltByCodes, useStructureSourceCodes } from '@/hooks/useConfiguration';

/** The boxes' lengths, as legacy's `maxlength`: a year's four digits, a cost's six. */
const YEAR_LENGTH = 4;
const COST_LENGTH = 6;

type Field = keyof StructureValues;

type Props = FieldsProps<StructureValues> & {
  /** Source is required — once the structure has one, or always on a new structure. */
  sourceRequired: boolean;
  /** The structure's own codes, kept selectable though since expired. */
  storedCodes?: { builtBy?: CodeValue; source?: CodeValue };
  /** Fields shown before these in the same grid. */
  leading?: ReactNode;
};

/**
 * The Structure card's fields — Built For, the years, Source, End of Design Life, As Built
 * Information on File and the two costs — for the card's edit and for Add Structure.
 */
const StructureFields: FC<Props> = ({
  values,
  shown,
  warnings = {},
  change,
  leave,
  sourceRequired,
  storedCodes = {},
  leading,
}) => {
  const builtBy = useBuiltByCodes();
  const sources = useStructureSourceCodes();
  const builtByOptions = useMemo(
    () => withStored(builtBy.data, storedCodes.builtBy),
    [builtBy.data, storedCodes.builtBy],
  );
  const sourceOptions = useMemo(
    () => withStored(sources.data, storedCodes.source),
    [sources.data, storedCodes.source],
  );
  const yearAddedRequired = SOURCES_NEEDING_YEAR_ADDED.has(values.sourceCode);

  /** A number box, as long as legacy's: 4 for a year, 6 for a cost in whole dollars. */
  const textField = (field: Field, label: ReactNode, maxLength: number) => (
    <TextInput
      id={`structure-${field}`}
      data-testid={`structure-${field}`}
      labelText={label}
      inputMode="numeric"
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

  return (
    <div className="structure-detail__fields">
      {leading}
      <Select
        id="structure-builtByCode"
        data-testid="structure-builtByCode"
        labelText="Built For"
        value={values.builtByCode}
        disabled={builtBy.isLoading}
        invalid={Boolean(shown.builtByCode)}
        invalidText={shown.builtByCode}
        onChange={(event) => change('builtByCode', event.target.value)}
        onBlur={() => leave('builtByCode')}
      >
        <SelectItem value="" text="None" />
        {builtByOptions.map((option) => (
          <SelectItem key={option.code} value={option.code} text={option.description} />
        ))}
      </Select>
      {textField('yearFabricated', 'Year Superstructure Fabricated', YEAR_LENGTH)}
      {textField('yearBuilt', 'Year Superstructure Installed', YEAR_LENGTH)}
      {textField(
        'inventoryAddedYear',
        requiredLabel('Year Added to Inventory', yearAddedRequired),
        YEAR_LENGTH,
      )}
      <Select
        id="structure-sourceCode"
        data-testid="structure-sourceCode"
        labelText={requiredLabel('Source', sourceRequired)}
        value={values.sourceCode}
        disabled={sources.isLoading}
        invalid={Boolean(shown.sourceCode)}
        invalidText={shown.sourceCode}
        onChange={(event) => change('sourceCode', event.target.value)}
        onBlur={() => leave('sourceCode')}
      >
        {/* A blank only while one may be left blank, or while nothing is chosen yet. */}
        {(!sourceRequired || !values.sourceCode) && (
          <SelectItem value="" text={sourceRequired ? 'Choose a source' : 'None'} />
        )}
        {sourceOptions.map((option) => (
          <SelectItem key={option.code} value={option.code} text={option.description} />
        ))}
      </Select>
      {textField('endOfDesignLifeYear', 'End of Design Life (year)', YEAR_LENGTH)}
      <RadioButtonGroup
        name="structure-as-built"
        legendText={requiredLabel('As Built Information on File?', true)}
        valueSelected={values.asBuiltInfoOnFile ? 'Y' : 'N'}
        onChange={(selected) => change('asBuiltInfoOnFile', selected === 'Y')}
      >
        <RadioButton id="structure-as-built-yes" labelText="Yes" value="Y" />
        <RadioButton id="structure-as-built-no" labelText="No" value="N" />
      </RadioButtonGroup>
      {textField('installationCost', 'Installation Cost ($)', COST_LENGTH)}
      {textField('materialCost', 'Material Cost ($)', COST_LENGTH)}
    </div>
  );
};

export default StructureFields;
