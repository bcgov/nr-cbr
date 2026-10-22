import { DATE_FORMAT, fromCalendar, toIso } from '@/pages/StructureDetail/dateBox';
import { MONTH_FORMAT, formatMonth, isMonth, parseMonth } from '@/utils/month';

/** What a range's boxes hold: a month or a day — its format, and how to read and write it. */
export type Calendar = {
  /** flatpickr's tokens for it. */
  format: string;
  /** The format, as the placeholder of both boxes. */
  placeholder: string;
  /** The box's pattern — Carbon's default is m/d/Y, which the browser would enforce on submit. */
  pattern: string;
  /** Whether the value is one the calendar can read, so it is safe to hand it. */
  readable: (value: string) => boolean;
  /** flatpickr's parser, where its own would misread the value; its own otherwise. */
  parse?: (value: string) => Date | false;
  /** A date picked on the calendar, as the box shows it. */
  write: (date: Date) => string;
};

/** A month, `yyyy/mm` — the inspection reports' Inspection Date, as on Inspection Search. */
export const MONTH: Calendar = {
  format: MONTH_FORMAT,
  placeholder: 'yyyy/mm',
  pattern: String.raw`\d{4}/\d{1,2}`,
  readable: (value) => value !== '' && isMonth(value),
  parse: parseMonth,
  write: formatMonth,
};

/** A day, legacy's `yyyy/mm/dd` — the Inspections Due reports' Next Planned Inspection Date. */
export const DAY: Calendar = {
  format: DATE_FORMAT,
  placeholder: 'yyyy/mm/dd',
  pattern: String.raw`\d{4}/\d{1,2}/\d{1,2}`,
  readable: (value) => toIso(value) !== null,
  write: fromCalendar,
};
