import { RadioButton, RadioButtonGroup, Select, SelectItem } from '@carbon/react';

import { requiredLabel } from '@/utils/requiredLabel';

import DateField from './DateField';
import { FREQUENCIES, type ScheduleValues } from './scheduleRules';

import type { FieldsProps, Messages } from './formFields';
import type { FC } from 'react';

import { useSpecialEquipmentCodes } from '@/hooks/useConfiguration';

/** Routine Inspection Frequency, the one schedule field Level 1 may change too. */
export const FrequencySelect: FC<{
  value: string;
  shown: Messages<ScheduleValues>;
  onChange: (frequency: string) => void;
  onBlur: () => void;
}> = ({ value, shown, onChange, onBlur }) => (
  <Select
    id="schedule-frequency"
    data-testid="schedule-frequency"
    labelText={requiredLabel('Routine Inspection Frequency (years)', true)}
    value={value}
    invalid={Boolean(shown.routineFrequencyYears)}
    invalidText={shown.routineFrequencyYears}
    onChange={(event) => onChange(event.target.value)}
    onBlur={onBlur}
  >
    {!value && <SelectItem value="" text="Choose a frequency" />}
    {FREQUENCIES.map((years) => (
      <SelectItem key={years} value={years} text={years} />
    ))}
  </Select>
);

type Props = FieldsProps<ScheduleValues> & {
  /** Next Planned Routine Inspection is required — once the structure has one, or on a new one. */
  routineRequired: boolean;
  /** A new frequency, which on the card may also move the next routine inspection. */
  onFrequency?: (frequency: string) => void;
};

/**
 * The inspection schedule's fields at Level 2 and up — close proximity, its equipment and date, the
 * next routine inspection and the frequency — for the Inspection Schedule card and Add Structure.
 * Unticking close proximity hides its equipment and date and keeps them, as legacy's hidden fields
 * did.
 */
const ScheduleFields: FC<Props> = ({
  values,
  shown,
  change,
  leave,
  routineRequired,
  onFrequency,
}) => {
  const equipment = useSpecialEquipmentCodes();
  return (
    <>
      <RadioButtonGroup
        name="schedule-close-proximity"
        legendText={requiredLabel('Is a close proximity inspection required?', true)}
        valueSelected={values.closeProximityRequired ? 'Y' : 'N'}
        onChange={(selected) => change('closeProximityRequired', selected === 'Y')}
      >
        <RadioButton id="schedule-close-proximity-yes" labelText="Yes" value="Y" />
        <RadioButton id="schedule-close-proximity-no" labelText="No" value="N" />
      </RadioButtonGroup>
      {values.closeProximityRequired && (
        <div className="structure-detail__edit-row">
          <div className="structure-detail__edit-cell">
            <Select
              id="schedule-equipment"
              data-testid="schedule-equipment"
              labelText="Close Proximity Special Equipment Requirements"
              value={values.closeProximityEquipmentCode}
              disabled={equipment.isLoading}
              invalid={Boolean(shown.closeProximityEquipmentCode)}
              invalidText={shown.closeProximityEquipmentCode}
              onChange={(event) => change('closeProximityEquipmentCode', event.target.value)}
            >
              <SelectItem value="" text="None" />
              {(equipment.data ?? []).map((option) => (
                <SelectItem key={option.code} value={option.code} text={option.description} />
              ))}
            </Select>
          </div>
          <div className="structure-detail__edit-cell">
            <DateField
              id="schedule-next-close-proximity"
              labelText="Next Planned Close Proximity Inspection"
              value={values.nextCloseProximityDate}
              invalidText={shown.nextCloseProximityDate}
              onChange={(value) => change('nextCloseProximityDate', value)}
              onBlur={() => leave('nextCloseProximityDate')}
            />
          </div>
        </div>
      )}
      <div className="structure-detail__edit-row">
        <div className="structure-detail__edit-cell">
          <DateField
            id="schedule-next-routine"
            labelText={requiredLabel('Next Planned Routine Inspection', routineRequired)}
            value={values.nextRoutineDate}
            invalidText={shown.nextRoutineDate}
            onChange={(value) => change('nextRoutineDate', value)}
            onBlur={() => leave('nextRoutineDate')}
          />
        </div>
        <div className="structure-detail__edit-cell">
          <FrequencySelect
            value={values.routineFrequencyYears}
            shown={shown}
            onChange={(frequency) =>
              onFrequency ? onFrequency(frequency) : change('routineFrequencyYears', frequency)
            }
            onBlur={() => leave('routineFrequencyYears')}
          />
        </div>
      </div>
    </>
  );
};

export default ScheduleFields;
