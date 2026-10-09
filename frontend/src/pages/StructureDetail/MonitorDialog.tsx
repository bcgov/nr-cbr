import { Button, Modal, Select, SelectItem, TextArea } from '@carbon/react';
import { useEffect, useRef, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { number } from './format';

import type { MonitorUpdateRequest, StructureMonitor } from './monitorsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useMonitorFrequencyCodes, useMonitoringStatusCodes } from '@/hooks/useConfiguration';
import { useCreateStructureMonitor, useUpdateStructureMonitor } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /**
   * The item being edited, or null to add one. The dialog is mounted only while it is open, so it
   * opens fresh each time.
   */
  monitor: StructureMonitor | null;
  onClose: () => void;
};

/** The frequency that needs words: Other. */
const OTHER_FREQUENCY = 'OTH';
/** Legacy's frequency for a new item: at each inspection. Used only when the list offers it. */
const DEFAULT_FREQUENCY = 'INS';
/** The status every new item starts in — legacy locks it on Add. */
const SUGGESTED = 'SUG';
/** The description and frequency comment columns, in bytes. */
const TEXT_MAX = 2000;

type Errors = Partial<Record<keyof MonitorUpdateRequest, string>>;

/** The form's own rules — the server's, checked before the round trip. */
const validate = (values: MonitorUpdateRequest): Errors => {
  const errors: Errors = {};
  if (!values.statusCode) errors.statusCode = 'Monitoring Status is required.';
  if (values.frequencyCode === OTHER_FREQUENCY && !values.frequencyComment.trim()) {
    errors.frequencyComment = 'Monitor Freq. Comment is required when the frequency is Other.';
  } else if (overLimitError(values.frequencyComment, TEXT_MAX)) {
    errors.frequencyComment = overLimitError(values.frequencyComment, TEXT_MAX);
  }
  if (!values.description.trim()) {
    errors.description = 'Monitor Description is required.';
  } else if (overLimitError(values.description, TEXT_MAX)) {
    errors.description = overLimitError(values.description, TEXT_MAX);
  }
  return errors;
};

/**
 * Legacy's "Monitoring Item" dialog, for adding an item or editing one.
 *
 * <p>Editing: the number shown and not changed; the status, the frequency (with its comment when
 * Other) and the description edited. Adding: as legacy, the status is Suggested and cannot be
 * changed; the number is the structure's next, given when saved and not shown before.
 *
 * <p>Errors sit beside their fields — the form's own rules on Save, then whatever the server refused.
 * A save that fails for no field's sake is a toast, as the app's other actions report one.
 */
const MonitorDialog: FC<Props> = ({ structureId, monitor, onClose }) => {
  const adding = monitor === null;
  const statuses = useMonitoringStatusCodes();
  const frequencies = useMonitorFrequencyCodes();
  const create = useCreateStructureMonitor(structureId);
  const update = useUpdateStructureMonitor(structureId);
  const saving = create.isPending || update.isPending;
  const { display } = useNotification();

  const [values, setValues] = useState<MonitorUpdateRequest>({
    statusCode: monitor?.status.code ?? SUGGESTED,
    frequencyCode: monitor?.frequency.code ?? '',
    frequencyComment: monitor?.frequencyComment ?? '',
    description: monitor?.description ?? '',
  });
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...(adding ? create.fieldErrors : update.fieldErrors), ...errors };

  // A new item starts at legacy's default frequency, once the list shows it exists — a code the
  // list does not offer would be refused on save, and the select could not show it.
  const frequencyTouched = useRef(false);
  useEffect(() => {
    if (!adding || frequencyTouched.current) return;
    if ((frequencies.data ?? []).some((option) => option.code === DEFAULT_FREQUENCY)) {
      setValues((current) => ({ ...current, frequencyCode: DEFAULT_FREQUENCY }));
    }
  }, [adding, frequencies.data]);

  const change = <K extends keyof MonitorUpdateRequest>(field: K, value: string) => {
    if (field === 'frequencyCode') frequencyTouched.current = true;
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const failed = (error: unknown) => {
    // Field refusals show beside their fields; anything else is a toast.
    if (Object.keys(apiFieldErrors(error)).length > 0) return;
    display({
      kind: 'error',
      title: adding ? 'The monitoring item was not added' : 'The monitoring item was not saved',
      subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
      timeout: 0,
    });
  };

  const save = () => {
    const found = validate(values);
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    // The comment goes only with Other, as the server keeps it only then.
    const frequencyComment =
      values.frequencyCode === OTHER_FREQUENCY ? values.frequencyComment : '';

    if (monitor === null) {
      create.mutate(
        {
          frequencyCode: values.frequencyCode,
          frequencyComment,
          description: values.description,
        },
        {
          onSuccess: (created) => {
            display({
              kind: 'success',
              title: `Monitoring item ${created.number} added`,
              timeout: 4000,
            });
            onClose();
          },
          onError: failed,
        },
      );
      return;
    }
    update.mutate(
      { monitorId: monitor.id, request: { ...values, frequencyComment } },
      {
        onSuccess: () => {
          display({
            kind: 'success',
            title: `Monitoring item ${number(monitor.number)} saved`,
            timeout: 4000,
          });
          onClose();
        },
        onError: failed,
      },
    );
  };

  return (
    <Modal
      open
      modalHeading={
        monitor === null ? 'Add monitoring item' : `Edit monitoring item ${number(monitor.number)}`
      }
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="monitor-dialog"
      className="structure-detail__monitor-dialog"
    >
      <div
        className={
          monitor === null
            ? 'structure-detail__dialog-fields structure-detail__dialog-fields--add'
            : 'structure-detail__dialog-fields'
        }
      >
        {/* Edit shows the number; Add has none to show — the server gives the structure's next
            on save, and the toast names it. */}
        {monitor !== null && (
          <ReadOnlyField label="Monitor Number" value={number(monitor.number)} />
        )}
        {adding ? (
          // Locked on Add, as legacy locks it: a new item is a suggestion until someone requires it.
          <ReadOnlyField label="Monitoring Status" value="Suggested" />
        ) : (
          <Select
            id="monitor-status"
            data-testid="monitor-status"
            labelText={requiredLabel('Monitoring Status', true)}
            value={values.statusCode}
            disabled={statuses.isLoading}
            invalid={Boolean(shown.statusCode)}
            invalidText={shown.statusCode}
            onChange={(event) => change('statusCode', event.target.value)}
          >
            {(statuses.data ?? []).map((option) => (
              <SelectItem key={option.code} value={option.code} text={option.description} />
            ))}
          </Select>
        )}
        <Select
          id="monitor-frequency"
          data-testid="monitor-frequency"
          labelText="Monitoring Frequency"
          value={values.frequencyCode}
          disabled={frequencies.isLoading}
          invalid={Boolean(shown.frequencyCode)}
          invalidText={shown.frequencyCode}
          onChange={(event) => change('frequencyCode', event.target.value)}
        >
          <SelectItem value="" text="None" />
          {(frequencies.data ?? []).map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
      </div>

      {values.frequencyCode === OTHER_FREQUENCY && (
        <div className="structure-detail__dialog-wide">
          <FieldWithCounter used={byteLength(values.frequencyComment)} limit={TEXT_MAX}>
            <TextArea
              id="monitor-frequency-comment"
              data-testid="monitor-frequency-comment"
              labelText={requiredLabel('Monitor Freq. Comment', true)}
              rows={2}
              value={values.frequencyComment}
              invalid={Boolean(shown.frequencyComment)}
              invalidText={shown.frequencyComment}
              onChange={(event) => change('frequencyComment', event.target.value)}
            />
          </FieldWithCounter>
        </div>
      )}

      <div className="structure-detail__dialog-wide">
        <FieldWithCounter used={byteLength(values.description)} limit={TEXT_MAX}>
          <TextArea
            id="monitor-description"
            data-testid="monitor-description"
            labelText={requiredLabel('Monitor Description', true)}
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
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={saving}
          data-testid="monitor-save"
        >
          {adding ? 'Add' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default MonitorDialog;
