import {
  Button,
  Checkbox,
  DatePicker,
  DatePickerInput,
  FormGroup,
  Modal,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMemo, useRef, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

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
import { useStructureRepairTypes, useUpdateStructureRepair } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /** The repair being edited. The dialog is mounted only while it is open, so it opens fresh. */
  repair: StructureRepair;
  onClose: () => void;
};

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
/** Legacy's date format, `yyyy/MM/dd`, in flatpickr's tokens. */
const DATE_FORMAT = 'Y/m/d';
const DATE_PATTERN = /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/;

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

const pad = (value: number) => String(value).padStart(2, '0');

/** `2026-09-01` as the box shows it, `2026/09/01`. */
const toBox = (iso: string | null): string => (iso ? iso.replaceAll('-', '/') : '');

/** A typed `yyyy/mm/dd` as the server takes it, or null when it is not a real day. */
const toIso = (typed: string): string | null => {
  const match = DATE_PATTERN.exec(typed.trim());
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return `${year}-${pad(month)}-${pad(day)}`;
};

const fromCalendar = (date: Date): string =>
  `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`;

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
 * Legacy's "Repair Item" dialog, for editing a repair.
 *
 * <p>As legacy: the number shown and not changed; Required and Not Required offered to a P.Eng only
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
  const update = useUpdateStructureRepair(structureId);
  const { display } = useNotification();

  const offeredStatuses = (statuses.data ?? []).filter(
    (option) => isPeng || !ENGINEER_STATUSES.has(option.code),
  );
  const storedStatus = repair.status.code ?? '';

  const [values, setValues] = useState<Values>({
    // A status this user may not set opens blank, as legacy's list opens without it.
    statusCode: !isPeng && ENGINEER_STATUSES.has(storedStatus) ? '' : storedStatus,
    priorityCode: repair.priority.code ?? '',
    completedDate: toBox(repair.completedDate),
    estimate: number(repair.estimate),
    actualCost: number(repair.actualCost),
    typeCode: repair.type.code ?? '',
    quantity: number(repair.quantity),
    description: repair.description ?? '',
  });
  const [ticked, setTicked] = useState<ReadonlySet<string>>(new Set());
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...update.fieldErrors, ...errors };
  const completed = values.statusCode === COMPLETED;
  // Only a whole date (or none) goes back to the picker: flatpickr parses the value it is handed,
  // and handing it each half-typed keystroke would rewrite the box under the user's cursor.
  const pickerDate = useRef('');
  if (!values.completedDate || toIso(values.completedDate) !== null) {
    pickerDate.current = values.completedDate;
  }

  const typeOptions = useMemo(() => {
    const listed = typesFor(types.data ?? [], ticked);
    // The stored type stays choosable while nothing narrows the list, even when the structure's
    // types no longer include it — legacy showed it blank, and a save then asked for it again.
    const stored = repair.type.code;
    if (stored && ticked.size === 0 && !listed.some((option) => option.code === stored)) {
      return [
        { code: stored, description: repair.type.description, unit: repair.unit, groupCode: '' },
        ...listed,
      ];
    }
    return listed;
  }, [types.data, ticked, repair.type, repair.unit]);
  const unit = typeOptions.find((option) => option.code === values.typeCode)?.unit ?? '';

  // A stored priority no longer current is still the repair's, so it stays in the list.
  const priorityOptions = useMemo(() => {
    const listed = priorities.data ?? [];
    const stored = repair.priority.code;
    return stored && !listed.some((option) => option.code === stored)
      ? [{ code: stored, description: repair.priority.description ?? stored }, ...listed]
      : listed;
  }, [priorities.data, repair.priority]);

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
        onError: (error) => {
          // Field refusals show beside their fields; anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The repair was not saved',
            subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
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
      modalHeading={`Edit repair ${number(repair.number)}`}
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="repair-dialog"
      className="structure-detail__repair-dialog"
    >
      <div className="structure-detail__repair-fields">
        <ReadOnlyField label="Repair Number" value={number(repair.number)} />
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

        <DatePicker
          datePickerType="single"
          dateFormat={DATE_FORMAT}
          value={pickerDate.current}
          // The shared fix in `_overrides.scss`: Carbon hard-codes a single picker to 18rem.
          className="cbr-date-picker"
          onChange={(dates: Date[]) =>
            change('completedDate', dates[0] ? fromCalendar(dates[0]) : '')
          }
        >
          <DatePickerInput
            id="repair-completed-date"
            data-testid="repair-completed-date"
            // One span, because Carbon lays a date picker's label out as flex: the asterisk on its
            // own would be a flex item, lifted off the text's baseline.
            labelText={<span>{requiredLabel('Repair Completed Date', completed)}</span>}
            placeholder="yyyy/mm/dd"
            disabled={!completed}
            invalid={Boolean(shown.completedDate)}
            invalidText={shown.completedDate}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('completedDate', event.target.value)
            }
          />
        </DatePicker>
        {amount('estimate', 'Repair Estimate Cost ($)')}
        {amount('actualCost', 'Repair Actual Cost ($)', !completed)}
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
        <Button kind="tertiary" size="md" onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={update.isPending}
          data-testid="repair-save"
        >
          Save
        </Button>
      </div>
    </Modal>
  );
};

export default RepairDialog;
