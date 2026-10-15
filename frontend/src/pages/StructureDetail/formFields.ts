import type { CodeValue } from './structureResponse';
import type { CodeOption } from '@/types/configuration';

/** A form's messages, one per field at most. */
export type Messages<V> = Partial<Record<keyof V & string, string>>;

/**
 * What a section's fields need from the form that holds them — the structure page's card that
 * edits them, or Add Structure, which lays several sections out together.
 */
export type FieldsProps<V> = {
  values: V;
  /** The errors to show now — `useFieldErrors`' `shown`. */
  shown: Messages<V>;
  /** Legacy's non-blocking warnings, shown as typed. */
  warnings?: Messages<V>;
  change: <K extends keyof V & string>(field: K, value: V[K]) => void;
  leave: (field: keyof V & string) => void;
};

/** The current codes, with a record's own first when it has since expired. */
export const withStored = (
  listed: CodeOption[] | undefined,
  stored: CodeValue | undefined,
): CodeOption[] => {
  const options = listed ?? [];
  return stored?.code && !options.some((option) => option.code === stored.code)
    ? [{ code: stored.code, description: stored.description ?? stored.code }, ...options]
    : options;
};

/** A stored number as a form holds it. */
export const blankToEmpty = (value: number | null) => (value === null ? '' : String(value));

/** A typed year as a number, null when blank, or NaN when it is not four digits (1000–9999). */
export const toYear = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^[1-9]\d{3}$/.test(trimmed) ? Number(trimmed) : Number.NaN;
};

/**
 * The messages of one section of a larger form whose fields are keyed `section.field` — Add
 * Structure's, as the server keys them.
 */
export const sectionOf = <V>(messages: Record<string, string | undefined>, section: string) =>
  Object.fromEntries(
    Object.entries(messages)
      .filter(([key]) => key.startsWith(`${section}.`))
      .map(([key, message]) => [key.slice(section.length + 1), message]),
  ) as Messages<V>;

/** One section's messages keyed `section.field`, to merge into a larger form's. */
export const prefixed = <V>(messages: Messages<V>, section: string) =>
  Object.fromEntries(
    Object.entries(messages).map(([key, message]) => [`${section}.${key}`, message]),
  ) as Record<string, string | undefined>;
