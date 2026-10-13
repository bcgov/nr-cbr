import { Button, Modal, Stack, TextInput } from '@carbon/react';
import { useState } from 'react';

import { requiredLabel } from '@/utils/requiredLabel';

import { number } from './format';

import type { BridgeSpan, SpanRequest } from './spansAndPiersResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useCreateSpan, useUpdateSpan } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type Props = {
  structureId: string;
  /** The span being edited, or null to add one. Mounted only while open. */
  span: BridgeSpan | null;
  /** The bridge's spans now, for the next number and the more-than-ten warning. */
  spans: BridgeSpan[];
  /**
   * Whether the structure has its `FOREST_SERVICE_BRIDGE` row, which spans hang from. Without it
   * nothing can be added, and the dialog says so rather than letting the user fill it in.
   */
  hasBridgeRecord: boolean;
  onClose: () => void;
};

/** `SPAN_NUMBER NUMBER(5)`. */
const NUMBER_MAX = 99_999;
/** `SPAN_LENGTH NUMBER(8,3)`. */
const LENGTH_MAX = 99_999.999;
/** Legacy warns past this many spans, and blocks nothing. */
const SPANS_ADVISED = 10;
export const NO_BRIDGE_RECORD =
  'This structure has no bridge record, so no spans or piers can be added to it.';

type Values = { number: string; lengthMetres: string };
type Errors = Partial<Record<keyof Values, string>>;

/** The form's own rules — the server's, checked before the round trip. */
const validate = (values: Values): Errors => {
  const errors: Errors = {};
  const typedNumber = values.number.trim();
  if (!typedNumber) errors.number = 'Span Number is required.';
  else if (!/^-?\d+$/.test(typedNumber)) errors.number = 'Span Number must be a whole number.';
  else if (Number(typedNumber) <= 0) errors.number = 'Span Number must be greater than 0.';
  else if (Number(typedNumber) > NUMBER_MAX) {
    errors.number = `Span Number can be at most ${NUMBER_MAX}.`;
  }
  const typedLength = values.lengthMetres.trim();
  if (!typedLength) errors.lengthMetres = 'Length is required.';
  else if (!/^-?\d*\.?\d*$/.test(typedLength) || typedLength === '.' || typedLength === '-') {
    errors.lengthMetres = 'Length must be a number, e.g. 12.5.';
  } else if (Number(typedLength) < 0) errors.lengthMetres = 'Length cannot be negative.';
  else if ((typedLength.split('.')[1] ?? '').replace(/0+$/, '').length > 3) {
    errors.lengthMetres = 'Length can have at most 3 decimal places.';
  } else if (Number(typedLength) > LENGTH_MAX) {
    errors.lengthMetres = 'Length can be at most 99999.999 metres.';
  }
  return errors;
};

/**
 * Adds a span to the bridge or changes one — legacy's span form on the Spans &amp; Piers tab, as a
 * dialog. Adding starts at the bridge's next number (its highest plus one, as legacy) with the
 * length blank. From the eleventh span on, Span Number carries legacy's warning that a bridge
 * should not have more than ten; it does not stop the save.
 *
 * <p>The server also refuses a span that would make the spans longer than the Bridge Length; that
 * message shows beside Length. A structure with no bridge record can take no span: the dialog says
 * so above its fields, which stay disabled, as it does if the server refuses for that reason.
 */
const SpanDialog: FC<Props> = ({ structureId, span, spans, hasBridgeRecord, onClose }) => {
  const create = useCreateSpan(structureId);
  const update = useUpdateSpan(structureId);
  const adding = span === null;
  const saving = create.isPending || update.isPending;
  const { display } = useNotification();

  const nextNumber = Math.max(0, ...spans.map((each) => each.number ?? 0)) + 1;
  const [values, setValues] = useState<Values>({
    number: adding ? String(nextNumber) : number(span.number),
    lengthMetres: adding ? '' : number(span.lengthMetres),
  });
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...(adding ? create.fieldErrors : update.fieldErrors), ...errors };
  /** Why the span cannot be saved at all: no bridge record, known now or from the server's 409. */
  const [refusal, setRefusal] = useState(adding && !hasBridgeRecord ? NO_BRIDGE_RECORD : undefined);

  const change = (field: keyof Values, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const save = () => {
    const found = validate(values);
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    const request: SpanRequest = {
      number: Number(values.number.trim()),
      lengthMetres: Number(values.lengthMetres.trim()),
    };
    const done = (verb: string) => () => {
      display({ kind: 'success', title: `Span ${request.number} ${verb}`, timeout: 4000 });
      onClose();
    };
    const failed = (error: unknown) => {
      // Field refusals show beside their fields, a 409 above them; anything else is a toast.
      if (Object.keys(apiFieldErrors(error)).length > 0) return;
      if ((error as { status?: number }).status === 409) {
        setRefusal(apiErrorMessage(error, NO_BRIDGE_RECORD));
        return;
      }
      display({
        kind: 'error',
        title: adding ? 'The span was not added' : 'The span was not saved',
        subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
        timeout: 0,
      });
    };
    if (span === null) {
      create.mutate(request, { onSuccess: done('added'), onError: failed });
    } else {
      update.mutate({ spanId: span.id, request }, { onSuccess: done('saved'), onError: failed });
    }
  };

  const overAdvised = adding && spans.length >= SPANS_ADVISED;

  return (
    <Modal
      open
      modalHeading={adding ? 'Add span' : `Edit span ${number(span.number)}`}
      size="sm"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="span-dialog"
    >
      <Stack gap={6}>
        {refusal && (
          <p className="structure-detail__upload-error" role="alert" data-testid="span-refusal">
            {refusal}
          </p>
        )}
        <div className="structure-detail__dialog-row">
          <TextInput
            id="span-number"
            data-testid="span-number"
            labelText={requiredLabel('Span Number', true)}
            helperText="Numbered Left Bank to Right Bank (Looking Downstream)."
            inputMode="numeric"
            value={values.number}
            disabled={Boolean(refusal)}
            invalid={Boolean(shown.number)}
            invalidText={shown.number}
            warn={overAdvised && !shown.number}
            warnText={`This will be span ${spans.length + 1}. A bridge should not have more than ${SPANS_ADVISED} spans.`}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('number', event.target.value)
            }
          />
          <TextInput
            id="span-length"
            data-testid="span-length"
            labelText={requiredLabel('Length (metres)', true)}
            helperText="Centre of bearing to centre of bearing."
            inputMode="decimal"
            value={values.lengthMetres}
            disabled={Boolean(refusal)}
            invalid={Boolean(shown.lengthMetres)}
            invalidText={shown.lengthMetres}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('lengthMetres', event.target.value)
            }
          />
        </div>
      </Stack>

      <div className="structure-detail__dialog-actions">
        <Button kind="tertiary" size="md" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={saving || Boolean(refusal)}
          data-testid="span-save"
        >
          {adding ? 'Add span' : 'Save changes'}
        </Button>
      </div>
    </Modal>
  );
};

export default SpanDialog;
