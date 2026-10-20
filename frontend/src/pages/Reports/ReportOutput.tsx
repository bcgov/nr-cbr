import { Download } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup } from '@carbon/react';

import { SORT_OPTIONS, type ReportFormat, type StructureInventorySort } from './types';

import type { FC } from 'react';

import { holdFocus } from '@/hooks/useFieldErrors';

type SortByProps = {
  /** The prefix of the radios' ids — the report's. */
  id: string;
  sortBy: StructureInventorySort;
  onChange: (sortBy: StructureInventorySort) => void;
};

/** The Structure Inventory reports' "Sort By" — legacy's four orders. */
export const SortBy: FC<SortByProps> = ({ id, sortBy, onChange }) => (
  <div className="report-criteria__output">
    <RadioButtonGroup
      legendText="Sort By"
      name={`${id}-sortBy`}
      orientation="vertical"
      valueSelected={sortBy}
      onChange={(value) => onChange(value as StructureInventorySort)}
    >
      {SORT_OPTIONS.map((option) => (
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
