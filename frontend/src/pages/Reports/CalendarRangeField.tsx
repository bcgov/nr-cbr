import { DatePicker, DatePickerInput } from '@carbon/react';
import { useRef } from 'react';

import type { Calendar } from './calendar';
import type { ChangeEvent, FC } from 'react';

type End = 'start' | 'end';

type Props = {
  calendar: Calendar;
  /** The ids of the two boxes, `<id>-<field>`; also their test ids. */
  startId: string;
  endId: string;
  legend: string;
  start: string;
  end: string;
  startError?: string;
  endError?: string;
  disabled?: boolean;
  onChange: (which: End, value: string) => void;
  onLeave: (which: End) => void;
};

/**
 * A "from – to" pair of months or days — a calendar each, or typed. The placeholder is short,
 * because the pair shares one grid cell; the dash between the boxes reads "from – to", and their
 * hidden labels say which is which.
 *
 * <p>Each calendar is handed only a value it can read: Carbon pushes `value` into flatpickr on
 * every change, and flatpickr blanks the box when it cannot parse it, which would erase "2026/0"
 * as the user types on the way to "2026/01". Anything mid-edit leaves the last readable value in
 * place. Two single pickers rather than a range picker, which would clear a range with one end
 * blank — "from 2026/01 onwards" is a whole criterion here.
 */
const CalendarRangeField: FC<Props> = ({
  calendar,
  startId,
  endId,
  legend,
  start,
  end,
  startError,
  endError,
  disabled,
  onChange,
  onLeave,
}) => {
  const parsable = useRef({ start: '', end: '' });
  for (const [which, value] of [
    ['start', start],
    ['end', end],
  ] as const) {
    if (value === '' || calendar.readable(value)) {
      parsable.current[which] = value;
    }
  }

  const picker = (which: End, id: string, label: string, error?: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat={calendar.format}
      parseDate={calendar.parse}
      value={parsable.current[which]}
      // The shared opt-in fix in `_overrides.scss`: Carbon pins a single picker to 18rem.
      className="cbr-date-picker"
      onChange={(dates: Date[]) => onChange(which, dates[0] ? calendar.write(dates[0]) : '')}
    >
      <DatePickerInput
        id={id}
        data-testid={id}
        labelText={label}
        hideLabel
        placeholder={calendar.placeholder}
        pattern={calendar.pattern}
        disabled={disabled}
        invalid={Boolean(error)}
        invalidText={error}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(which, event.target.value)}
        onBlur={(event) => {
          onChange(which, event.target.value);
          onLeave(which);
        }}
      />
    </DatePicker>
  );

  return (
    <fieldset className="report-criteria__range report-criteria__calendar-range">
      <legend className="cds--label">{legend}</legend>
      <div className="report-criteria__range-inputs">
        {picker('start', startId, `${legend} From`, startError)}
        <span aria-hidden="true" className="report-criteria__range-dash">
          –
        </span>
        {picker('end', endId, `${legend} To`, endError)}
      </div>
    </fieldset>
  );
};

export default CalendarRangeField;
