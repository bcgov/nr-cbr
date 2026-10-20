import { Select, SelectItem, TextInput, Toggle } from '@carbon/react';

import type { CodeOption, OrgUnitOption } from '@/types/configuration';

/** "PB - Permanent Bridge"; a missing half degrades to the other rather than "PB - undefined". */
const label = (code: string | null, description: string | null): string =>
  [code, description].filter(Boolean).join(' - ');

type Context<C extends object> = {
  /** The prefix of every box's id and test id — the report's. */
  id: string;
  criteria: C;
  /** The errors to show now — `useFieldErrors().shown`. */
  shown: Partial<Record<keyof C, string | undefined>>;
  /** A box was left — `useFieldErrors().leave`. */
  leave: (field: keyof C & string) => void;
  onChange: <K extends keyof C>(field: K, value: C[K]) => void;
  /** Disables the selects while their contents are still unknown. */
  codeTablesLoading: boolean;
};

/**
 * The boxes a report's criteria form is built from — a text box, a code or org-unit select, a
 * "from – to" range, a toggle — each bound to one criterion by name, as Structure Search builds
 * its form. Shared by the reports so their criteria read and behave alike.
 */
export const criteriaFields = <C extends object>({
  id,
  criteria,
  shown,
  leave,
  onChange,
  codeTablesLoading,
}: Context<C>) => {
  type Field = keyof C & string;

  const text = (
    field: Field,
    labelText: string,
    maxLength: number,
    extra?: { placeholder?: string; inputMode?: 'decimal' | 'numeric'; hideLabel?: boolean },
  ) => (
    <TextInput
      id={`${id}-${field}`}
      data-testid={`${id}-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={String(criteria[field])}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event) => onChange(field, event.target.value as C[Field])}
      onBlur={() => leave(field)}
      {...extra}
    />
  );

  const codeSelect = (field: Field, labelText: string, anyText: string, options: CodeOption[]) => (
    <Select
      id={`${id}-${field}`}
      data-testid={`${id}-${field}`}
      labelText={labelText}
      disabled={codeTablesLoading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as C[Field])}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.code}
          value={option.code}
          text={label(option.code, option.description)}
        />
      ))}
    </Select>
  );

  const orgUnitSelect = (
    field: Field,
    labelText: string,
    anyText: string,
    options: OrgUnitOption[],
    loading = codeTablesLoading,
  ) => (
    <Select
      id={`${id}-${field}`}
      data-testid={`${id}-${field}`}
      labelText={labelText}
      disabled={loading}
      value={String(criteria[field])}
      onChange={(event) => onChange(field, event.target.value as C[Field])}
    >
      <SelectItem value="" text={anyText} />
      {options.map((option) => (
        <SelectItem
          key={option.orgUnitNo}
          value={option.orgUnitNo}
          text={label(option.orgUnitCode, option.orgUnitName)}
        />
      ))}
    </Select>
  );

  /**
   * A "from – to" pair, as on Structure Search: the legend names the pair and the boxes carry
   * hidden labels and a placeholder.
   */
  const range = (
    from: Field,
    to: Field,
    labelText: string,
    kind: 'kilometres' | 'year' | 'millimetres',
  ) => {
    const box = (field: Field, end: 'From' | 'To') => {
      const common = { hideLabel: true } as const;
      if (kind === 'year') {
        return text(field, `${labelText} ${end}`, 4, {
          ...common,
          inputMode: 'numeric',
          placeholder: `${end} yyyy`,
        });
      }
      if (kind === 'millimetres') {
        return text(field, `${labelText} ${end}`, 6, {
          ...common,
          inputMode: 'numeric',
          placeholder: end,
        });
      }
      return text(field, `${labelText} ${end}`, 9, {
        ...common,
        inputMode: 'decimal',
        placeholder: end,
      });
    };
    return (
      <fieldset className="report-criteria__range">
        <legend className="cds--label">{labelText}</legend>
        <div className="report-criteria__range-inputs">
          {box(from, 'From')}
          <span aria-hidden="true" className="report-criteria__range-dash">
            –
          </span>
          {box(to, 'To')}
        </div>
      </fieldset>
    );
  };

  const toggle = (field: Field, labelText: string) => (
    <Toggle
      id={`${id}-${field}`}
      data-testid={`${id}-${field}`}
      labelText={labelText}
      size="sm"
      toggled={Boolean(criteria[field])}
      onToggle={(checked) => onChange(field, checked as C[Field])}
    />
  );

  return { text, codeSelect, orgUnitSelect, range, toggle };
};
