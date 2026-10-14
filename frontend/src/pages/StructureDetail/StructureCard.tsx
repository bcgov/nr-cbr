import { Building, Edit } from '@carbon/icons-react';
import {
  Button,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react';
import { useMemo, useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import Card from './Card';
import { describe, money, number, yesNo } from './format';

import type {
  CodeValue,
  StructureCommonDetails,
  StructureDetailsRequest,
} from './structureResponse';
import type { CodeOption } from '@/types/configuration';
import type { ChangeEvent, FC, ReactNode } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useBuiltByCodes, useStructureSourceCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateStructureDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

/** Sources that need Year Added to Inventory — legacy's asterisk follows them. */
const SOURCES_NEEDING_YEAR_ADDED = new Set(['IR', 'INSR', 'ITM', 'IFS', 'IIO']);
/** The costs are `NUMBER(6)`, whole dollars. */
const COST_MAX = 999_999;
/** The boxes' lengths, as legacy's `maxlength`: a year's four digits, a cost's six. */
const YEAR_LENGTH = 4;
const COST_LENGTH = 6;

type Props = {
  structureId: string;
  details: StructureCommonDetails;
  /** Shown here only when the structure has no bridge or culvert card to carry them. */
  extra?: ReactNode;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/** The form's values as typed: the numbers are text until saved. */
type Values = {
  builtByCode: string;
  yearFabricated: string;
  yearBuilt: string;
  inventoryAddedYear: string;
  sourceCode: string;
  endOfDesignLifeYear: string;
  asBuiltInfoOnFile: boolean;
  installationCost: string;
  materialCost: string;
};
type Field = keyof Values;
type Messages = Partial<Record<Field, string>>;

/** A typed year as a number, null when blank, or NaN when it is not four digits (1000–9999). */
const toYear = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^[1-9]\d{3}$/.test(trimmed) ? Number(trimmed) : Number.NaN;
};

/** A typed cost as a number, null when blank, or NaN when it is not whole dollars in range. */
const toCost = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= COST_MAX ? Number(trimmed) : Number.NaN;
};

/** The form's blocking rules — the server's, checked before the round trip. */
const errorsOf = (values: Values, sourceRequired: boolean): Messages => {
  const errors: Messages = {};
  const thisYear = new Date().getFullYear();
  const years: [Field, string][] = [
    ['yearFabricated', 'Year Superstructure Fabricated'],
    ['yearBuilt', 'Year Superstructure Installed'],
    ['inventoryAddedYear', 'Year Added to Inventory'],
    ['endOfDesignLifeYear', 'End of Design Life'],
  ];
  for (const [field, label] of years) {
    if (Number.isNaN(toYear(values[field] as string))) {
      errors[field] = `${label} must be a 4-digit year, e.g. 2004.`;
    }
  }
  const fabricated = toYear(values.yearFabricated);
  const built = toYear(values.yearBuilt);
  if (!errors.yearFabricated && fabricated !== null) {
    if (fabricated > thisYear) {
      errors.yearFabricated = `Year Superstructure Fabricated must not be after this year (${thisYear}).`;
    } else if (built !== null && !Number.isNaN(built) && fabricated > built) {
      errors.yearFabricated =
        'Year Superstructure Fabricated must not be after Year Superstructure Installed.';
    }
  }
  if (sourceRequired && !values.sourceCode) errors.sourceCode = 'Source is required.';
  if (
    SOURCES_NEEDING_YEAR_ADDED.has(values.sourceCode) &&
    !values.inventoryAddedYear.trim() &&
    !errors.inventoryAddedYear
  ) {
    errors.inventoryAddedYear = 'Year Added to Inventory is required for this Source.';
  }
  if (Number.isNaN(toCost(values.installationCost))) {
    errors.installationCost = 'Installation Cost must be whole dollars from $0 to $999,999.';
  }
  if (Number.isNaN(toCost(values.materialCost))) {
    errors.materialCost = 'Material Cost must be whole dollars from $0 to $999,999.';
  }
  return errors;
};

/**
 * Legacy's range warnings (`Structure.validate`), shown beside a field as it is typed. They never
 * stopped legacy's save and do not stop this one.
 */
const warningsOf = (values: Values): Messages => {
  const warnings: Messages = {};
  const thisYear = new Date().getFullYear();
  const built = toYear(values.yearBuilt);
  const outside = (year: number | null, low: number, high: number, highText: string) =>
    year === null || Number.isNaN(year)
      ? undefined
      : year > high
        ? `Must not be greater than ${highText}.`
        : year < low
          ? `Must be greater than or equal to ${low}.`
          : undefined;
  warnings.yearBuilt = outside(built, 1900, thisYear, 'the current year');
  warnings.yearFabricated = outside(toYear(values.yearFabricated), 1900, thisYear + 1, 'next year');
  warnings.inventoryAddedYear = outside(
    toYear(values.inventoryAddedYear),
    1900,
    thisYear + 1,
    'next year',
  );
  // As legacy: only against a year installed.
  if (built !== null && !Number.isNaN(built)) {
    warnings.endOfDesignLifeYear = outside(
      toYear(values.endOfDesignLifeYear),
      1950,
      built + 50,
      `the year built + 50 years (${built + 50})`,
    );
  }
  const installation = toCost(values.installationCost);
  if (installation !== null && installation > 500_000) {
    warnings.installationCost = 'Installation Cost is not in the range $0 through $500,000.';
  }
  return warnings;
};

/** The current codes, with the structure's own first when it has since expired. */
const withStored = (listed: CodeOption[] | undefined, stored: CodeValue): CodeOption[] => {
  const options = listed ?? [];
  return stored.code && !options.some((option) => option.code === stored.code)
    ? [{ code: stored.code, description: stored.description ?? stored.code }, ...options]
    : options;
};

const blankToEmpty = (value: number | null) => (value === null ? '' : String(value));

/** What the card shows when it is not being edited. */
const StructureView: FC<{ details: StructureCommonDetails; extra?: ReactNode }> = ({
  details,
  extra,
}) => (
  <div className="structure-detail__fields">
    {extra}
    <ReadOnlyField label="Built For" value={describe(details.builtBy)} />
    <ReadOnlyField label="Year Superstructure Fabricated" value={number(details.yearFabricated)} />
    <ReadOnlyField label="Year Superstructure Installed" value={number(details.yearBuilt)} />
    <ReadOnlyField label="Year Added to Inventory" value={number(details.inventoryAddedYear)} />
    <ReadOnlyField label="Source" value={describe(details.source)} />
    <ReadOnlyField label="End of Design Life (year)" value={number(details.endOfDesignLifeYear)} />
    <ReadOnlyField label="As Built Information on File?" value={yesNo(details.asBuiltInfoOnFile)} />
    <ReadOnlyField label="Installation Cost ($)" value={money(details.installationCost)} />
    <ReadOnlyField label="Material Cost ($)" value={money(details.materialCost)} />
  </div>
);

/**
 * The card's values as a form, in place of the view, every field where the view has it.
 *
 * <p>Legacy's rules beside their fields — each once its field is left, then as it is corrected, and
 * all of them on Save: years of up to four digits, the fabrication year
 * not after this year or the year installed, Source once the structure has one, Year Added to
 * Inventory for the sources IR, INSR, ITM, IFS and IIO, costs in whole dollars the column holds.
 * Legacy's range warnings show as the fields are typed and do not stop the save.
 */
const StructureForm: FC<{
  structureId: string;
  details: StructureCommonDetails;
  extra?: ReactNode;
  onDone: () => void;
}> = ({ structureId, details, extra, onDone }) => {
  const builtBy = useBuiltByCodes();
  const sources = useStructureSourceCodes();
  const update = useUpdateStructureDetails(structureId);
  const { display } = useNotification();
  // As legacy: required once the structure has one.
  const sourceRequired = Boolean(details.source.code);

  const [values, setValues] = useState<Values>({
    builtByCode: details.builtBy.code ?? '',
    yearFabricated: blankToEmpty(details.yearFabricated),
    yearBuilt: blankToEmpty(details.yearBuilt),
    inventoryAddedYear: blankToEmpty(details.inventoryAddedYear),
    sourceCode: details.source.code ?? '',
    endOfDesignLifeYear: blankToEmpty(details.endOfDesignLifeYear),
    asBuiltInfoOnFile: details.asBuiltInfoOnFile,
    installationCost: blankToEmpty(details.installationCost),
    materialCost: blankToEmpty(details.materialCost),
  });
  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<Field>(errorsOf(values, sourceRequired), update.fieldErrors);
  const shown = form.shown;
  const warnings = warningsOf(values);
  const builtByOptions = useMemo(
    () => withStored(builtBy.data, details.builtBy),
    [builtBy.data, details.builtBy],
  );
  const sourceOptions = useMemo(
    () => withStored(sources.data, details.source),
    [sources.data, details.source],
  );
  const yearAddedRequired = SOURCES_NEEDING_YEAR_ADDED.has(values.sourceCode);

  const change = <K extends Field>(field: K, value: Values[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const request: StructureDetailsRequest = {
      builtByCode: values.builtByCode,
      yearFabricated: toYear(values.yearFabricated),
      yearBuilt: toYear(values.yearBuilt),
      inventoryAddedYear: toYear(values.inventoryAddedYear),
      sourceCode: values.sourceCode,
      endOfDesignLifeYear: toYear(values.endOfDesignLifeYear),
      asBuiltInfoOnFile: values.asBuiltInfoOnFile,
      installationCost: toCost(values.installationCost),
      materialCost: toCost(values.materialCost),
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Structure details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The structure details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  /** A number box, as long as legacy's: 4 for a year, 6 for a cost in whole dollars. */
  const textField = (field: Field, label: ReactNode, maxLength: number) => (
    <TextInput
      id={`structure-${field}`}
      data-testid={`structure-${field}`}
      labelText={label}
      inputMode="numeric"
      maxLength={maxLength}
      value={values[field] as string}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      warn={!shown[field] && Boolean(warnings[field])}
      warnText={warnings[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    />
  );

  return (
    <div className="structure-detail__edit" data-testid="structure-details-form">
      <div className="structure-detail__fields">
        {extra}
        <Select
          id="structure-builtByCode"
          data-testid="structure-builtByCode"
          labelText="Built For"
          value={values.builtByCode}
          disabled={builtBy.isLoading}
          invalid={Boolean(shown.builtByCode)}
          invalidText={shown.builtByCode}
          onChange={(event) => change('builtByCode', event.target.value)}
          onBlur={() => form.leave('builtByCode')}
        >
          <SelectItem value="" text="None" />
          {builtByOptions.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
        {textField('yearFabricated', 'Year Superstructure Fabricated', YEAR_LENGTH)}
        {textField('yearBuilt', 'Year Superstructure Installed', YEAR_LENGTH)}
        {textField(
          'inventoryAddedYear',
          requiredLabel('Year Added to Inventory', yearAddedRequired),
          YEAR_LENGTH,
        )}
        <Select
          id="structure-sourceCode"
          data-testid="structure-sourceCode"
          labelText={requiredLabel('Source', sourceRequired)}
          value={values.sourceCode}
          disabled={sources.isLoading}
          invalid={Boolean(shown.sourceCode)}
          invalidText={shown.sourceCode}
          onChange={(event) => change('sourceCode', event.target.value)}
          onBlur={() => form.leave('sourceCode')}
        >
          {/* A blank only while one may be left blank, or while nothing is chosen yet. */}
          {(!sourceRequired || !values.sourceCode) && (
            <SelectItem value="" text={sourceRequired ? 'Choose a source' : 'None'} />
          )}
          {sourceOptions.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
        {textField('endOfDesignLifeYear', 'End of Design Life (year)', YEAR_LENGTH)}
        <RadioButtonGroup
          name="structure-as-built"
          legendText={requiredLabel('As Built Information on File?', true)}
          valueSelected={values.asBuiltInfoOnFile ? 'Y' : 'N'}
          onChange={(selected) => change('asBuiltInfoOnFile', selected === 'Y')}
        >
          <RadioButton id="structure-as-built-yes" labelText="Yes" value="Y" />
          <RadioButton id="structure-as-built-no" labelText="No" value="N" />
        </RadioButtonGroup>
        {textField('installationCost', 'Installation Cost ($)', COST_LENGTH)}
        {textField('materialCost', 'Material Cost ($)', COST_LENGTH)}
      </div>

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="sm"
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="sm"
          onClick={save}
          disabled={update.isPending}
          data-testid="structure-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

/**
 * The Details tab's Structure card — legacy's fields above the bridge and culvert ones — with its
 * own "Edit structure" for Level 2 and up, legacy's `/level2Access` on every one of them. The edit
 * replaces the values in place, as the Inspection Schedule card does.
 */
const StructureCard: FC<Props> = ({
  structureId,
  details,
  extra,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { canDelete: canChange } = useAuthorization();
  return (
    <Card
      title="Structure"
      icon={Building}
      testId="structure-section-common"
      action={
        canChange &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="structure-details-edit"
            onClick={onEdit}
          >
            Edit structure
          </Button>
        )
      }
    >
      {editing ? (
        <StructureForm structureId={structureId} details={details} extra={extra} onDone={onDone} />
      ) : (
        <StructureView details={details} extra={extra} />
      )}
    </Card>
  );
};

export default StructureCard;
