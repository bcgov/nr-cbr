import { TextArea, TextInput } from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import { TEXT_MAX, type ReplacementValues } from './replacementRules';

import type { FieldsProps } from './formFields';
import type { ChangeEvent, FC } from 'react';

import { byteLength } from '@/utils/textLimits';

type Field = keyof ReplacementValues;

type Props = FieldsProps<ReplacementValues> & {
  /** Estimated Load Restriction is required — once the structure has one. */
  restrictionRequired: boolean;
};

/** Replacement Details' fields — the three years, the cost and its comments. */
const ReplacementFields: FC<Props> = ({
  values,
  shown,
  warnings = {},
  change,
  leave,
  restrictionRequired,
}) => {
  const box = (
    field: Exclude<Field, 'costComment'>,
    label: string,
    maxLength: number,
    required = false,
  ) => (
    <TextInput
      id={`replacement-${field}`}
      data-testid={`replacement-${field}`}
      labelText={requiredLabel(label, required)}
      inputMode="numeric"
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

  return (
    <>
      <div className="structure-detail__fields">
        {box('closureYear', 'Estimated Closure (year)', 4)}
        {box('replacementYear', 'Estimated Replacement (year)', 4)}
        {box('loadRestrictionYear', 'Estimated Load Restriction (year)', 4, restrictionRequired)}
        {box('cost', 'Estimated Replacement Cost ($)', 8)}
      </div>
      <FieldWithCounter used={byteLength(values.costComment)} limit={TEXT_MAX}>
        <TextArea
          id="replacement-costComment"
          data-testid="replacement-costComment"
          labelText="Replacement Cost Comments"
          rows={3}
          value={values.costComment}
          invalid={Boolean(shown.costComment)}
          invalidText={shown.costComment}
          onChange={(event) => change('costComment', event.target.value)}
          onBlur={() => leave('costComment')}
        />
      </FieldWithCounter>
    </>
  );
};

export default ReplacementFields;
