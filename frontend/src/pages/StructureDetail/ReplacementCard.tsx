import { Edit, Renew } from '@carbon/icons-react';
import { Button, TextArea, TextInput } from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import Card from './Card';
import { money, number } from './format';

import type { ReplacementDetails, ReplacementDetailsRequest } from './structureResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useUpdateReplacementDetails } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

/** `ESTIMATED_REPLACEMENT_COST NUMBER(8)`, whole dollars. */
const COST_MAX = 99_999_999;
/** `ESTIMATED_REPLACEMENT_COST_CMT VARCHAR2(2000 BYTE)`. */
const TEXT_MAX = 2000;

type Values = {
  closureYear: string;
  replacementYear: string;
  loadRestrictionYear: string;
  cost: string;
  costComment: string;
};
type Field = keyof Values;
type Messages = Partial<Record<Field, string>>;

/** A typed year as a number, null when blank, or NaN when it is not four digits. */
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

const usable = (year: number | null): year is number => year !== null && !Number.isNaN(year);

/** Estimated Load Restriction not after Estimated Replacement, nor after Estimated Closure. */
const restrictionOrderError = (
  restriction: number | null,
  replacement: number | null,
  closure: number | null,
): string | undefined => {
  if (!usable(restriction)) return undefined;
  if (usable(replacement) && restriction > replacement) {
    return 'Estimated Load Restriction must not be after Estimated Replacement.';
  }
  return usable(closure) && restriction > closure
    ? 'Estimated Load Restriction must not be after Estimated Closure.'
    : undefined;
};

/** The form's blocking rules — legacy's `StructureForm.validate`, checked as the values change. */
const errorsOf = (values: Values, restrictionRequired: boolean): Messages => {
  const errors: Messages = {};
  const years: [Field, string][] = [
    ['closureYear', 'Estimated Closure'],
    ['replacementYear', 'Estimated Replacement'],
    ['loadRestrictionYear', 'Estimated Load Restriction'],
  ];
  for (const [field, label] of years) {
    if (Number.isNaN(toYear(values[field]))) {
      errors[field] = `${label} must be a 4-digit year, e.g. 2004.`;
    }
  }
  const closure = toYear(values.closureYear);
  const replacement = toYear(values.replacementYear);
  const restriction = toYear(values.loadRestrictionYear);
  if (restriction === null && restrictionRequired) {
    errors.loadRestrictionYear = 'Estimated Load Restriction is required.';
  }
  if (usable(replacement) && usable(closure) && replacement > closure) {
    errors.replacementYear = 'Estimated Replacement must not be after Estimated Closure.';
  }
  errors.loadRestrictionYear ??= restrictionOrderError(restriction, replacement, closure);
  if (Number.isNaN(toCost(values.cost))) {
    errors.cost = 'Estimated Replacement Cost must be whole dollars from $0 to $99,999,999.';
  }
  if (overLimitError(values.costComment, TEXT_MAX)) {
    errors.costComment = overLimitError(values.costComment, TEXT_MAX);
  }
  return errors;
};

/**
 * Legacy's warnings (`Structure.validate`, `StructureAction.validate`), shown as typed; they never
 * stopped its save. The year limits count from Year Superstructure Installed, when there is one.
 */
const warningsOf = (values: Values, yearBuilt: number | null, recreation: boolean): Messages => {
  const warnings: Messages = {};
  const outside = (year: number | null, offset: number) => {
    if (!usable(year)) return undefined;
    if (yearBuilt !== null && year > yearBuilt + offset) {
      return `Must not be greater than the year built + ${offset} years (${yearBuilt + offset}).`;
    }
    return year < 1950 ? 'Must be greater than or equal to 1950.' : undefined;
  };
  warnings.closureYear = outside(toYear(values.closureYear), 50);
  warnings.replacementYear = outside(toYear(values.replacementYear), 60);
  warnings.loadRestrictionYear = outside(toYear(values.loadRestrictionYear), 50);
  // Not on a recreation site, as legacy.
  if (!values.loadRestrictionYear.trim() && !recreation) {
    warnings.loadRestrictionYear = 'Required field.';
  }
  const cost = toCost(values.cost);
  if (cost !== null && !Number.isNaN(cost) && cost > 2_000_000) {
    warnings.cost = 'Estimated Replacement Cost is not in the range $0 through $2,000,000.';
  }
  return warnings;
};

const blankToEmpty = (value: number | null) => (value === null ? '' : String(value));

type FormProps = {
  structureId: string;
  replacement: ReplacementDetails;
  yearBuilt: number | null;
  recreation: boolean;
  onDone: () => void;
};

/**
 * The card's values as a form, in place of the view, every field where the view has it. Each
 * error shows once its field is left, then as it is corrected; legacy's warnings as typed.
 */
const ReplacementForm: FC<FormProps> = ({
  structureId,
  replacement,
  yearBuilt,
  recreation,
  onDone,
}) => {
  const update = useUpdateReplacementDetails(structureId);
  const { display } = useNotification();
  const [values, setValues] = useState<Values>({
    closureYear: blankToEmpty(replacement.estimatedClosureYear),
    replacementYear: blankToEmpty(replacement.estimatedReplacementYear),
    loadRestrictionYear: blankToEmpty(replacement.estimatedLoadRestrictionYear),
    cost: blankToEmpty(replacement.estimatedReplacementCost),
    costComment: replacement.replacementCostComment ?? '',
  });
  // Legacy's tier rule: required once the structure has one.
  const restrictionRequired = replacement.estimatedLoadRestrictionYear !== null;
  const form = useFieldErrors<Field>(errorsOf(values, restrictionRequired), update.fieldErrors);
  const shown = form.shown;
  const warnings = warningsOf(values, yearBuilt, recreation);

  const change = (field: Field, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const request: ReplacementDetailsRequest = {
      closureYear: toYear(values.closureYear),
      replacementYear: toYear(values.replacementYear),
      loadRestrictionYear: toYear(values.loadRestrictionYear),
      cost: toCost(values.cost),
      costComment: values.costComment,
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Replacement details saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The replacement details were not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const box = (
    field: Exclude<Field, 'costComment'>,
    label: string,
    maxLength: number,
    required = false,
  ) => (
    <TextInput
      id={`replacement-${field}`}
      data-testid={`replacement-${field}`}
      labelText={requiredLabel(label, required)}
      inputMode="numeric"
      maxLength={maxLength}
      value={values[field]}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      warn={!shown[field] && Boolean(warnings[field])}
      warnText={warnings[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
      onBlur={() => form.leave(field)}
    />
  );

  return (
    <div className="structure-detail__edit" data-testid="replacement-details-form">
      <div className="structure-detail__fields">
        {box('closureYear', 'Estimated Closure (year)', 4)}
        {box('replacementYear', 'Estimated Replacement (year)', 4)}
        {box('loadRestrictionYear', 'Estimated Load Restriction (year)', 4, restrictionRequired)}
        {box('cost', 'Estimated Replacement Cost ($)', 8)}
      </div>
      <FieldWithCounter used={byteLength(values.costComment)} limit={TEXT_MAX}>
        <TextArea
          id="replacement-costComment"
          data-testid="replacement-costComment"
          labelText="Replacement Cost Comments"
          rows={3}
          value={values.costComment}
          invalid={Boolean(shown.costComment)}
          invalidText={shown.costComment}
          onChange={(event) => change('costComment', event.target.value)}
          onBlur={() => form.leave('costComment')}
        />
      </FieldWithCounter>

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          kind="tertiary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          kind="primary"
          size="sm"
          onMouseDown={holdFocus}
          onClick={save}
          disabled={update.isPending}
          data-testid="replacement-details-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

type Props = {
  structureId: string;
  replacement: ReplacementDetails;
  /** Year Superstructure Installed, which legacy's year warnings count from. */
  yearBuilt: number | null;
  /** Whether the structure stands on a recreation site, where Load Restriction is not expected. */
  recreation: boolean;
  editing: boolean;
  /** True while another card on the tab is being edited, when this one's Edit waits. */
  otherEditing: boolean;
  onEdit: () => void;
  onDone: () => void;
};

/**
 * The Details tab's Replacement Details — legacy's estimates of closure, replacement and load
 * restriction, and the cost — with its own "Edit replacement details" for a P.Eng, legacy's
 * `/pEngAccess` on every one of its fields. The edit replaces the values in place, as the other
 * cards do. The replacement history is a card of its own, `ReplacementHistoryCard`.
 */
const ReplacementCard: FC<Props> = ({
  structureId,
  replacement,
  yearBuilt,
  recreation,
  editing,
  otherEditing,
  onEdit,
  onDone,
}) => {
  const { isPeng } = useAuthorization();
  return (
    <Card
      title="Replacement Details"
      icon={Renew}
      testId="structure-section-replacement"
      action={
        isPeng &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            disabled={otherEditing}
            data-testid="replacement-details-edit"
            onClick={onEdit}
          >
            Edit replacement details
          </Button>
        )
      }
    >
      {editing ? (
        <ReplacementForm
          structureId={structureId}
          replacement={replacement}
          yearBuilt={yearBuilt}
          recreation={recreation}
          onDone={onDone}
        />
      ) : (
        <>
          <div className="structure-detail__fields">
            <ReadOnlyField
              label="Estimated Closure (year)"
              value={number(replacement.estimatedClosureYear)}
            />
            <ReadOnlyField
              label="Estimated Replacement (year)"
              value={number(replacement.estimatedReplacementYear)}
            />
            <ReadOnlyField
              label="Estimated Load Restriction (year)"
              value={number(replacement.estimatedLoadRestrictionYear)}
            />
            <ReadOnlyField
              label="Estimated Replacement Cost ($)"
              value={money(replacement.estimatedReplacementCost)}
            />
          </div>
          <div className="structure-detail__wide">
            <ReadOnlyField
              label="Replacement Cost Comments"
              value={replacement.replacementCostComment}
            />
          </div>
        </>
      )}
    </Card>
  );
};

export default ReplacementCard;
