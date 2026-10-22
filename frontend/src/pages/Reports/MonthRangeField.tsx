import { DatePicker, DatePickerInput } from '@carbon/react';
import { useRef } from 'react';

import type { ChangeEvent, FC } from 'react';

import { MONTH_FORMAT, formatMonth, isMonth, parseMonth } from '@/utils/month';

type End = 'start' | 'end';

/**
 * The format, as the placeholder of both boxes. Short, because the pair shares one grid cell; the
 * dash between them reads "from – to", and their hidden labels say which is which.
 */
const MONTH_PLACEHOLDER = 'yyyy/mm';

type Props = {
  /** The ids of the two boxes, `<id>-<field>`; also their test ids. */
  startId: string;
  endId: string;
  legend: string;
  start: string;
  end: string;
  startError?: string;
  endError?: string;
  onChange: (which: End, value: string) => void;
  onLeave: (which: End) => void;
};

/**
 * A "from – to" pair of months (`yyyy/mm`), as Inspection Search's Inspection Date — a calendar
 * each, or typed.
 *
 * <p>Each calendar is handed only a month it can read: Carbon pushes `value` into flatpickr on every
 * change, and flatpickr blanks the box when it cannot parse it, which would erase "2026/0" as the
 * user types on the way to "2026/01". Anything mid-edit leaves the last readable month in place.
 * Two single pickers rather than a range picker, which would clear a range with one end blank —
 * "from 2026/01 onwards" is a whole criterion here.
 */
const MonthRangeField: FC<Props> = ({
  startId,
  endId,
  legend,
  start,
  end,
  startError,
  endError,
  onChange,
  onLeave,
}) => {
  const parsable = useRef({ start: '', end: '' });
  if (isMonth(start)) {
    parsable.current.start = start;
  }
  if (isMonth(end)) {
    parsable.current.end = end;
  }

  const picker = (which: End, id: string, label: string, placeholder: string, error?: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat={MONTH_FORMAT}
      parseDate={parseMonth}
      value={parsable.current[which]}
      // The shared opt-in fix in `_overrides.scss`: Carbon pins a single picker to 18rem.
      className="cbr-date-picker"
      onChange={(dates: Date[]) => onChange(which, dates[0] ? formatMonth(dates[0]) : '')}
    >
      <DatePickerInput
        id={id}
        data-testid={id}
        labelText={label}
        hideLabel
        placeholder={placeholder}
        // Carbon's default pattern is m/d/Y, which the browser would enforce on submit.
        pattern="\d{4}/\d{1,2}"
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
    <fieldset className="report-criteria__range report-criteria__month-range">
      <legend className="cds--label">{legend}</legend>
      <div className="report-criteria__range-inputs">
        {picker('start', startId, `${legend} From`, MONTH_PLACEHOLDER, startError)}
        <span aria-hidden="true" className="report-criteria__range-dash">
          –
        </span>
        {picker('end', endId, `${legend} To`, MONTH_PLACEHOLDER, endError)}
      </div>
    </fieldset>
  );
};

export default MonthRangeField;
