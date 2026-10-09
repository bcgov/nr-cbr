import { DatePicker, DatePickerInput } from '@carbon/react';
import { useRef } from 'react';

import { DATE_FORMAT, fromCalendar, toIso } from './dateBox';

import type { ChangeEvent, FC, ReactNode } from 'react';

type Props = {
  id: string;
  labelText: ReactNode;
  /** What the box holds, `yyyy/mm/dd` or part of one while it is typed. */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  invalidText?: string;
};

/**
 * A date box with its calendar, typed as legacy's `yyyy/mm/dd`.
 *
 * <p>Only a whole date (or none) goes back to the picker: flatpickr parses the value it is handed,
 * and handing it each half-typed keystroke would rewrite the box under the user's cursor.
 */
const DateField: FC<Props> = ({ id, labelText, value, onChange, disabled, invalidText }) => {
  const pickerDate = useRef('');
  if (!value || toIso(value) !== null) {
    pickerDate.current = value;
  }
  return (
    <DatePicker
      datePickerType="single"
      dateFormat={DATE_FORMAT}
      value={pickerDate.current}
      // The shared fix in `_overrides.scss`: Carbon hard-codes a single picker to 18rem.
      className="cbr-date-picker"
      onChange={(dates: Date[]) => onChange(dates[0] ? fromCalendar(dates[0]) : '')}
    >
      <DatePickerInput
        id={id}
        data-testid={id}
        // One span, because Carbon lays a date picker's label out as flex: an asterisk on its own
        // would be a flex item, lifted off the text's baseline.
        labelText={<span>{labelText}</span>}
        placeholder="yyyy/mm/dd"
        disabled={disabled}
        invalid={Boolean(invalidText)}
        invalidText={invalidText}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)}
      />
    </DatePicker>
  );
};

export default DateField;
