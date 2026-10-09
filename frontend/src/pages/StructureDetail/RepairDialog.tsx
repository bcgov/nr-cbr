import {
  Button,
  Checkbox,
  FormGroup,
  Modal,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMemo, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { toBox, toIso } from './dateBox';
import DateField from './DateField';
import { number } from './format';

import type { RepairTypeOption, RepairUpdateRequest, StructureRepair } from './repairsResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useRepairGroupCodes,
  useRepairPriorityCodes,
  useRepairStatusCodes,
} from '@/hooks/useConfiguration';
import {
  useCreateStructureRepair,
  useStructureRepairTypes,
  useUpdateStructureRepair,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /**
   * The repair being edited, or null to add one. The dialog is mounted only while it is open, so it
   * opens fresh each time.
   */
  repair: StructureRepair | null;
  onClose: () => void;
};

/** The status every new repair starts in — legacy locks it on Add. */
const SUGGESTED = 'SUG';
/** The status that has a completed date and an actual cost. */
const COMPLETED = 'COM';
/** Required and Not Required: legacy offers them to a P.Eng only, and the server agrees. */
const ENGINEER_STATUSES: ReadonlySet<string> = new Set(['REQ', 'NRQ']);
/** The type whose repair must be described — legacy's `Repair.validate`. */
const DESCRIBED_TYPE = '800A';
/** The description column, in bytes. */
const TEXT_MAX = 2000;
/** The amount and quantity boxes: whole numbers, six digits at most, as legacy's boxes. */
const AMOUNT_MAX = 999_999;

/** The form's values as typed: the numbers and the date are text until saved. */
type Values = {
  statusCode: string;
  priorityCode: string;
  completedDate: string;
  estimate: string;
  actualCost: string;
  typeCode: string;
  quantity: string;
  description: string;
};

type Field = keyof Values;
type Errors = Partial<Record<Field, string>>;

/** A typed amount as a number, null when blank, or NaN when it is not a whole number in range. */
const toAmount = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  if (!/^\d+$/.test(trimmed)) return Number.NaN;
  const value = Number(trimmed);
  return value > AMOUNT_MAX ? Number.NaN : value;
};

const amountError = (label: string, typed: string): string | undefined =>
  Number.isNaN(toAmount(typed)) ? `${label} must be a whole number from 0 to 999,999.` : undefined;

/** The form's own rules — the server's, checked before the round trip. */
const validate = (values: Values): Errors => {
  const errors: Errors = {};
  if (!values.statusCode) errors.statusCode = 'Repair Status is required.';
  if (!values.priorityCode) errors.priorityCode = 'Repair Priority is required.';
  if (values.statusCode === COMPLETED) {
    if (!values.completedDate.trim()) {
      errors.completedDate = 'Repair Completed Date is required when the status is Completed.';
    } else if (toIso(values.completedDate) === null) {
      errors.completedDate = 'Repair Completed Date must be a date, as yyyy/mm/dd.';
    }
  }
  errors.estimate = amountError('Repair Estimate Cost', values.estimate);
  errors.actualCost = amountError('Repair Actual Cost', values.actualCost);
  if (!values.typeCode) errors.typeCode = 'Repair Type is required.';
  errors.quantity = amountError('Qty', values.quantity);
  if (values.typeCode === DESCRIBED_TYPE && !values.description.trim()) {
    errors.description = 'Repair Description is required for this Repair Type.';
  } else if (overLimitError(values.description, TEXT_MAX)) {
    errors.description = overLimitError(values.description, TEXT_MAX);
  }
  return errors;
};

/**
 * The types the ticked groups allow, each once, in the server's order (by group, then the type's
 * order) — or every type when no group is ticked, as legacy sends all five groups then.
 */
const typesFor = (options: RepairTypeOption[], groups: ReadonlySet<string>) => {
  const seen = new Set<string>();
  return options.filter((option) => {
    if (groups.size > 0 && !groups.has(option.groupCode)) return false;
    if (seen.has(option.code)) return false;
    seen.add(option.code);
    return true;
  });
};

/**
 * Legacy's "Repair Item" dialog, for adding a repair or editing one.
 *
 * <p>Adding, as legacy: the status is Suggested and cannot be changed, so the completed date and
 * actual cost are not offered; the number is the structure's next, given when saved and not shown
 * before.
 *
 * <p>Editing, as legacy: the number shown and not changed; Required and Not Required offered to a P.Eng only
 * (a repair already in one opens with no status chosen for anyone else, who must pick another);
 * the completed date and actual cost open only for Completed, and are cleared when the status moves
 * off it; the group checkboxes narrow the Repair Type list, every type showing while none is ticked.
 *
 * <p>Errors sit beside their fields — the form's own rules on Save, then whatever the server refused.
 * A save that fails for no field's sake is a toast, as the app's other actions report one.
 */
const RepairDialog: FC<Props> = ({ structureId, repair, onClose }) => {
  const { isPeng } = useAuthorization();
  const statuses = useRepairStatusCodes();
  const priorities = useRepairPriorityCodes();
  const groups = useRepairGroupCodes();
  const types = useStructureRepairTypes(structureId);
  const adding = repair === null;
  const create = useCreateStructureRepair(structureId);
  const update = useUpdateStructureRepair(structureId);
  const saving = create.isPending || update.isPending;
  const { display } = useNotification();

  const offeredStatuses = (statuses.data ?? []).filter(
    (option) => isPeng || !ENGINEER_STATUSES.has(option.code),
  );
  const storedStatus = repair?.status.code ?? SUGGESTED;

  const [values, setValues] = useState<Values>({
    // A status this user may not set opens blank, as legacy's list opens without it.
    statusCode: !isPeng && ENGINEER_STATUSES.has(storedStatus) ? '' : storedStatus,
    priorityCode: repair?.priority.code ?? '',
    completedDate: toBox(repair?.completedDate ?? null),
    estimate: number(repair?.estimate),
    actualCost: number(repair?.actualCost),
    typeCode: repair?.type.code ?? '',
    quantity: number(repair?.quantity),
    description: repair?.description ?? '',
  });
  const [ticked, setTicked] = useState<ReadonlySet<string>>(new Set());
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...(adding ? create.fieldErrors : update.fieldErrors), ...errors };
  const completed = values.statusCode === COMPLETED;

  const typeOptions = useMemo(() => {
    const listed = typesFor(types.data ?? [], ticked);
    // The stored type stays choosable while nothing narrows the list, even when the structure's
    // types no longer include it — legacy showed it blank, and a save then asked for it again.
    const stored = repair?.type.code;
    if (stored && ticked.size === 0 && !listed.some((option) => option.code === stored)) {
      return [
        { code: stored, description: repair.type.description, unit: repair.unit, groupCode: '' },
        ...listed,
      ];
    }
    return listed;
  }, [types.data, ticked, repair]);
  const unit = typeOptions.find((option) => option.code === values.typeCode)?.unit ?? '';

  // A stored priority no longer current is still the repair's, so it stays in the list.
  const priorityOptions = useMemo(() => {
    const listed = priorities.data ?? [];
    const stored = repair?.priority;
    return stored?.code && !listed.some((option) => option.code === stored.code)
      ? [{ code: stored.code, description: stored.description ?? stored.code }, ...listed]
      : listed;
  }, [priorities.data, repair]);

  const change = (field: Field, value: string) => {
    setValues((current) => {
      const next = { ...current, [field]: value };
      // As legacy's checkStatusCode: off Completed, the completed date and actual cost are cleared.
      if (field === 'statusCode' && value !== COMPLETED) {
        next.completedDate = '';
        next.actualCost = '';
      }
      return next;
    });
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const toggleGroup = (code: string, checked: boolean) => {
    const next = new Set(ticked);
    if (checked) next.add(code);
    else next.delete(code);
    setTicked(next);
    // A type the narrowed list no longer holds is cleared, as legacy's list replaced it.
    const kept = typesFor(types.data ?? [], next).some((option) => option.code === values.typeCode);
    if (next.size > 0 && !kept) change('typeCode', '');
  };

  const save = () => {
    const found = validate(values);
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    const request: RepairUpdateRequest = {
      statusCode: values.statusCode,
      priorityCode: values.priorityCode,
      completedDate: completed ? toIso(values.completedDate) : null,
      estimate: toAmount(values.estimate),
      actualCost: completed ? toAmount(values.actualCost) : null,
      typeCode: values.typeCode,
      quantity: toAmount(values.quantity),
      description: values.description,
    };
    const failed = (error: unknown) => {
      // Field refusals show beside their fields; anything else is a toast.
      if (Object.keys(apiFieldErrors(error)).length > 0) return;
      display({
        kind: 'error',
        title: adding ? 'The repair was not added' : 'The repair was not saved',
        subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
        timeout: 0,
      });
    };
    if (repair === null) {
      create.mutate(
        {
          priorityCode: request.priorityCode,
          estimate: request.estimate,
          typeCode: request.typeCode,
          quantity: request.quantity,
          description: request.description,
        },
        {
          onSuccess: (created) => {
            display({ kind: 'success', title: `Repair ${created.number} added`, timeout: 4000 });
            onClose();
          },
          onError: failed,
        },
      );
      return;
    }
    update.mutate(
      { repairId: repair.id, request },
      {
        onSuccess: () => {
          display({
            kind: 'success',
            title: `Repair ${number(repair.number)} saved`,
            timeout: 4000,
          });
          onClose();
        },
        onError: failed,
      },
    );
  };

  const amount = (
    field: 'estimate' | 'actualCost' | 'quantity',
    label: string,
    disabled = false,
  ) => (
    <TextInput
      id={`repair-${field}`}
      data-testid={`repair-${field}`}
      labelText={label}
      inputMode="numeric"
      value={values[field]}
      disabled={disabled}
      invalid={Boolean(shown[field])}
      invalidText={shown[field]}
      onChange={(event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value)}
    />
  );

  return (
    <Modal
      open
      modalHeading={repair === null ? 'Add repair' : `Edit repair ${number(repair.number)}`}
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="repair-dialog"
      className="structure-detail__repair-dialog"
    >
      <div className="structure-detail__repair-fields">
        {/* Edit shows the number; Add has none to show — the server gives the structure's next on
            save, and the toast names it. */}
        {repair !== null && <ReadOnlyField label="Repair Number" value={number(repair.number)} />}
        {repair === null ? (
          // Locked on Add, as legacy locks it: a new repair is a suggestion until someone requires it.
          <ReadOnlyField label="Repair Status" value="Suggested" />
        ) : (
          <Select
            id="repair-status"
            data-testid="repair-status"
            labelText={requiredLabel('Repair Status', true)}
            value={values.statusCode}
            disabled={statuses.isLoading}
            invalid={Boolean(shown.statusCode)}
            invalidText={shown.statusCode}
            onChange={(event) => change('statusCode', event.target.value)}
          >
            <SelectItem value="" text="Choose a status" />
            {offeredStatuses.map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        )}
        <Select
          id="repair-priority"
          data-testid="repair-priority"
          labelText={requiredLabel('Repair Priority', true)}
          value={values.priorityCode}
          disabled={priorities.isLoading}
          invalid={Boolean(shown.priorityCode)}
          invalidText={shown.priorityCode}
          onChange={(event) => change('priorityCode', event.target.value)}
        >
          <SelectItem value="" text="Choose a priority" />
          {priorityOptions.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>

        {/* Not on Add: a new repair is Suggested, and only a completed one has these. */}
        {repair !== null && (
          <>
            <DateField
              id="repair-completed-date"
              labelText={requiredLabel('Repair Completed Date', completed)}
              value={values.completedDate}
              disabled={!completed}
              invalidText={shown.completedDate}
              onChange={(value) => change('completedDate', value)}
            />
          </>
        )}
        {amount('estimate', 'Repair Estimate Cost ($)')}
        {repair !== null && amount('actualCost', 'Repair Actual Cost ($)', !completed)}
      </div>

      {/* Spaced by a wrapper: Carbon's fieldset rule resets its own margin. */}
      <div className="structure-detail__dialog-wide">
        <FormGroup legendText="Show repair types for" data-testid="repair-groups">
          <div className="structure-detail__repair-groups">
            {(groups.data ?? []).map((group) => (
              <Checkbox
                key={group.code}
                id={`repair-group-${group.code}`}
                data-testid={`repair-group-${group.code}`}
                labelText={group.description}
                checked={ticked.has(group.code)}
                onChange={(_, { checked }) => toggleGroup(group.code, checked)}
              />
            ))}
          </div>
        </FormGroup>
      </div>

      <div className="structure-detail__repair-fields structure-detail__dialog-wide">
        <div className="structure-detail__repair-type">
          <Select
            id="repair-type"
            data-testid="repair-type"
            labelText={requiredLabel('Repair Type', true)}
            value={values.typeCode}
            disabled={types.isLoading}
            invalid={Boolean(shown.typeCode)}
            invalidText={shown.typeCode}
            onChange={(event) => change('typeCode', event.target.value)}
          >
            <SelectItem value="" text="Choose a type" />
            {typeOptions.map((option) => (
              <SelectItem
                key={option.code}
                value={option.code}
                text={option.description ?? option.code}
              />
            ))}
          </Select>
        </div>
        <div className="structure-detail__quantity">
          {amount('quantity', 'Qty')}
          {unit && (
            <span className="structure-detail__unit" data-testid="repair-unit">
              {unit}
            </span>
          )}
        </div>
      </div>

      <div className="structure-detail__dialog-wide">
        <FieldWithCounter used={byteLength(values.description)} limit={TEXT_MAX}>
          <TextArea
            id="repair-description"
            data-testid="repair-description"
            labelText={requiredLabel('Repair Description', values.typeCode === DESCRIBED_TYPE)}
            rows={5}
            value={values.description}
            invalid={Boolean(shown.description)}
            invalidText={shown.description}
            onChange={(event) => change('description', event.target.value)}
          />
        </FieldWithCounter>
      </div>

      <div className="structure-detail__dialog-actions">
        <Button kind="tertiary" size="md" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button kind="primary" size="md" onClick={save} disabled={saving} data-testid="repair-save">
          {adding ? 'Add' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default RepairDialog;
