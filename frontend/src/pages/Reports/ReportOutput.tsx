import { Download } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup } from '@carbon/react';

import type { ReportFormat } from './types';
import type { FC } from 'react';

import { holdFocus } from '@/hooks/useFieldErrors';

/** A report's orders, each with its legacy label. */
export type SortOption<S extends string> = { value: S; label: string };

type SortByProps<S extends string> = {
  /** The prefix of the radios' ids — the report's. */
  id: string;
  options: readonly SortOption<S>[];
  sortBy: S;
  onChange: (sortBy: S) => void;
};

/** A report's "Sort By" — legacy's orders for it, one radio each. */
export const SortBy = <S extends string>({ id, options, sortBy, onChange }: SortByProps<S>) => (
  <div className="report-criteria__output">
    <RadioButtonGroup
      legendText="Sort By"
      name={`${id}-sortBy`}
      orientation="vertical"
      valueSelected={sortBy}
      onChange={(value) => onChange(value as S)}
    >
      {options.map((option) => (
        <RadioButton
          key={option.value}
          id={`${id}-sortBy-${option.value}`}
          data-testid={`${id}-sortBy-${option.value}`}
          labelText={option.label}
          value={option.value}
        />
      ))}
    </RadioButtonGroup>
  </div>
);

type ButtonsProps = {
  /** The prefix of the buttons' test ids — the report's. */
  id: string;
  /**
   * The formats offered, a Download button each, in this order. PDF is the form's submit — what
   * Enter in a box produces — and goes last, as the primary action.
   */
  formats: readonly ReportFormat[];
  /** The format being produced, while one is: every button waits, and that one says so. */
  running: ReportFormat | null;
  onRun: (format: ReportFormat) => void;
  onReset: () => void;
};

/** Clear, then a Download button per format. */
export const ReportButtons: FC<ButtonsProps> = ({ id, formats, running, onRun, onReset }) => (
  <div className="report-criteria__actions">
    <Button
      kind="ghost"
      type="button"
      onClick={onReset}
      onMouseDown={holdFocus}
      data-testid={`${id}-reset`}
    >
      Clear
    </Button>
    {formats.map((format) => (
      <Button
        key={format}
        kind="tertiary"
        type={format === 'PDF' ? 'submit' : 'button'}
        renderIcon={Download}
        onClick={format === 'PDF' ? undefined : () => onRun(format)}
        onMouseDown={holdFocus}
        disabled={running !== null}
        data-testid={`${id}-run-${format}`}
      >
        {running === format ? `Producing ${format}…` : `Download ${format}`}
      </Button>
    ))}
  </div>
);
