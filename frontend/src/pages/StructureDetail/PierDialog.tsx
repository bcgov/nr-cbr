import { Button, Modal, Select, SelectItem, Stack, TextInput } from '@carbon/react';
import { useMemo, useState } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import { number } from './format';
import { NO_BRIDGE_RECORD } from './SpanDialog';

import type { BridgePier, PierRequest } from './spansAndPiersResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { usePierTypeCodes } from '@/hooks/useConfiguration';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useCreatePier, useUpdatePier } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type Props = {
  structureId: string;
  /** The pier being edited, or null to add one. Mounted only while open. */
  pier: BridgePier | null;
  /** The bridge's piers now, for the next number. */
  piers: BridgePier[];
  /** How many spans the bridge has: a pier may be added only while they will still outnumber the piers. */
  spanCount: number;
  /** Whether the structure has its `FOREST_SERVICE_BRIDGE` row, which piers hang from. */
  hasBridgeRecord: boolean;
  onClose: () => void;
  /**
   * Hands the pier to the page instead of saving it — Add Structure keeps its piers until the
   * structure is saved. A number already on the list is refused here, as the server would.
   */
  onSubmit?: (request: PierRequest) => void;
};

/** `PIER_NUMBER NUMBER(10)`. */
const NUMBER_MAX = 9_999_999_999;
const SPANS_OUTNUMBER_PIERS = 'Number of spans must be greater than number of piers.';

type Values = { number: string; typeCode: string };
type Errors = Partial<Record<keyof Values | 'pier', string>>;

/** The form's own rules — the server's, checked before the round trip. */
const validate = (values: Values): Errors => {
  const errors: Errors = {};
  const typed = values.number.trim();
  if (!typed) errors.number = 'Pier Number is required.';
  else if (!/^-?\d+$/.test(typed)) errors.number = 'Pier Number must be a whole number.';
  else if (Number(typed) <= 0) errors.number = 'Pier Number must be greater than 0.';
  else if (Number(typed) > NUMBER_MAX) errors.number = `Pier Number can be at most ${NUMBER_MAX}.`;
  if (!values.typeCode) errors.typeCode = 'Type is required.';
  return errors;
};

/**
 * Adds a pier to the bridge or changes one — legacy's pier form on the Spans &amp; Piers tab, as a
 * dialog. Adding starts at the bridge's next number (its highest plus one, as legacy) with no type
 * chosen, and is refused, as legacy, unless the spans will still outnumber the piers.
 *
 * <p>Type lists the current types. A pier whose type has since expired keeps it in the list, so it
 * can be saved unchanged; legacy showed it blank and asked for another.
 *
 * <p>A structure with no bridge record can take no pier: the dialog says so above its fields, which
 * stay disabled, as it does if the server refuses for that reason.
 */
const PierDialog: FC<Props> = ({
  structureId,
  pier,
  piers,
  spanCount,
  hasBridgeRecord,
  onClose,
  onSubmit,
}) => {
  const types = usePierTypeCodes();
  const create = useCreatePier(structureId);
  const update = useUpdatePier(structureId);
  const adding = pier === null;
  const saving = create.isPending || update.isPending;
  const { display } = useNotification();

  const nextNumber = Math.max(0, ...piers.map((each) => each.number ?? 0)) + 1;
  const [values, setValues] = useState<Values>({
    number: adding ? String(nextNumber) : number(pier.number),
    typeCode: adding ? '' : (pier.type.code ?? ''),
  });
  // Checked as the values change; each shown once its field is left, or at Save. Too few spans
  // for another pier is about the pier as a whole, so it waits for Save.
  const form = useFieldErrors<keyof Errors>(
    {
      ...validate(values),
      ...(onSubmit !== undefined &&
      piers.some((each) => each !== pier && String(each.number) === values.number.trim())
        ? { number: 'Pier Number already exists.' }
        : {}),
      pier: adding && spanCount <= piers.length + 1 ? SPANS_OUTNUMBER_PIERS : undefined,
    },
    adding ? create.fieldErrors : update.fieldErrors,
  );
  const shown: Errors = form.shown;
  /** Why the pier cannot be saved at all: no bridge record, known now or from the server's 409. */
  const [refusal, setRefusal] = useState(adding && !hasBridgeRecord ? NO_BRIDGE_RECORD : undefined);
  const disabled = Boolean(refusal);

  const options = useMemo(() => {
    const listed = types.data ?? [];
    const stored = pier?.type;
    return stored?.code && !listed.some((option) => option.code === stored.code)
      ? [{ code: stored.code, description: stored.description ?? stored.code }, ...listed]
      : listed;
  }, [types.data, pier]);

  const change = (field: keyof Values, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit()) return;
    const request: PierRequest = {
      number: Number(values.number.trim()),
      typeCode: values.typeCode,
    };
    if (onSubmit) {
      onSubmit(request);
      onClose();
      return;
    }
    const done = (verb: string) => () => {
      display({ kind: 'success', title: `Pier ${request.number} ${verb}`, timeout: 4000 });
      onClose();
    };
    const failed = (error: unknown) => {
      // Refusals show in the form, a 409 above the fields; anything else is a toast.
      if (Object.keys(apiFieldErrors(error)).length > 0) return;
      if ((error as { status?: number }).status === 409) {
        setRefusal(apiErrorMessage(error, NO_BRIDGE_RECORD));
        return;
      }
      display({
        kind: 'error',
        title: adding ? 'The pier was not added' : 'The pier was not saved',
        subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
        timeout: 0,
      });
    };
    if (pier === null) {
      create.mutate(request, { onSuccess: done('added'), onError: failed });
    } else {
      update.mutate({ pierId: pier.id, request }, { onSuccess: done('saved'), onError: failed });
    }
  };

  return (
    <Modal
      open
      modalHeading={adding ? 'Add pier' : `Edit pier ${number(pier.number)}`}
      size="sm"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="pier-dialog"
    >
      <Stack gap={6}>
        {/* A refusal of the pier as a whole — too few spans for another — above its fields. */}
        {(refusal ?? shown.pier) && (
          <p className="structure-detail__upload-error" role="alert" data-testid="pier-error">
            {refusal ?? shown.pier}
          </p>
        )}
        <div className="structure-detail__dialog-row">
          <TextInput
            id="pier-number"
            data-testid="pier-number"
            // As legacy's box: PIER_NUMBER is NUMBER(10).
            maxLength={10}
            labelText={requiredLabel('Pier Number', true)}
            inputMode="numeric"
            value={values.number}
            disabled={disabled}
            invalid={Boolean(shown.number)}
            invalidText={shown.number}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('number', event.target.value)
            }
            onBlur={() => form.leave('number')}
          />
          <Select
            id="pier-type"
            data-testid="pier-type"
            labelText={requiredLabel('Type', true)}
            value={values.typeCode}
            disabled={types.isLoading || disabled}
            invalid={Boolean(shown.typeCode)}
            invalidText={shown.typeCode}
            onChange={(event) => change('typeCode', event.target.value)}
            onBlur={() => form.leave('typeCode')}
          >
            <SelectItem value="" text="Choose a type" />
            {options.map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        </div>
      </Stack>

      <div className="structure-detail__dialog-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="md"
          onClick={onClose}
          disabled={saving}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="md"
          onClick={save}
          disabled={saving || disabled}
          data-testid="pier-save"
        >
          {adding ? 'Add pier' : 'Save changes'}
        </Button>
      </div>
    </Modal>
  );
};

export default PierDialog;
