/**
 * A date as its box holds it — legacy's `yyyy/MM/dd` — and as the server takes it, `yyyy-MM-dd`.
 * The structure page's date fields type and show the first and send the second.
 */

/** Legacy's date format, `yyyy/MM/dd`, in flatpickr's tokens. */
export const DATE_FORMAT = 'Y/m/d';
const DATE_PATTERN = /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/;

const pad = (value: number) => String(value).padStart(2, '0');

/** `2026-09-01` as the box shows it, `2026/09/01`; empty for none. */
export const toBox = (iso: string | null | undefined): string =>
  iso ? iso.replaceAll('-', '/') : '';

/** A typed `yyyy/mm/dd` as the server takes it, or null when it is not a real day. */
export const toIso = (typed: string): string | null => {
  const match = DATE_PATTERN.exec(typed.trim());
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return `${year}-${pad(month)}-${pad(day)}`;
};

/** A day picked on the calendar, as the box shows it. */
export const fromCalendar = (date: Date): string =>
  `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`;

/** `2024-06-15` plus some years, as `yyyy/mm/dd` — 29 February lands on 28 February. */
export const addYears = (iso: string, years: number): string => {
  const [year, month, day] = iso.split('-').map(Number);
  const target = year + years;
  const lastDay = new Date(target, month, 0).getDate();
  return `${target}/${pad(month)}/${pad(Math.min(day, lastDay))}`;
};
