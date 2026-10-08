import { Button, Modal, Select, SelectItem, TextArea } from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import { number } from './format';

import type { MonitorUpdateRequest, StructureMonitor } from './monitorsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useMonitorFrequencyCodes, useMonitoringStatusCodes } from '@/hooks/useConfiguration';
import { useUpdateStructureMonitor } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /** The item being edited. The dialog is mounted only while one is, so it opens fresh each time. */
  monitor: StructureMonitor;
  onClose: () => void;
};

/** The frequency that needs words: Other. */
const OTHER_FREQUENCY = 'OTH';
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
 * Legacy's "Monitoring Item" dialog, opened from the item's Edit action: the number shown, not
 * changed; the status, the frequency (with its comment when Other) and the description edited.
 *
 * <p>Errors sit beside their fields — the form's own rules on Save, then whatever the server refused.
 * A save that fails for no field's sake is a toast, as the app's other actions report one.
 */
const EditMonitorModal: FC<Props> = ({ structureId, monitor, onClose }) => {
  const statuses = useMonitoringStatusCodes();
  const frequencies = useMonitorFrequencyCodes();
  const update = useUpdateStructureMonitor(structureId);
  const { display } = useNotification();

  const [values, setValues] = useState<MonitorUpdateRequest>({
    statusCode: monitor.status.code ?? '',
    frequencyCode: monitor.frequency.code ?? '',
    frequencyComment: monitor.frequencyComment ?? '',
    description: monitor.description ?? '',
  });
  /** The form's own errors, set on Save and cleared field by field as each is changed. */
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...update.fieldErrors, ...errors };

  const change = <K extends keyof MonitorUpdateRequest>(field: K, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const save = () => {
    const found = validate(values);
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    update.mutate(
      {
        monitorId: monitor.id,
        // The comment goes only with Other, as the server keeps it only then.
        request: {
          ...values,
          frequencyComment: values.frequencyCode === OTHER_FREQUENCY ? values.frequencyComment : '',
        },
      },
      {
        onSuccess: () => {
          display({
            kind: 'success',
            title: `Monitoring item ${number(monitor.number)} saved`,
            timeout: 4000,
          });
          onClose();
        },
        onError: (error) => {
          // Field refusals show beside their fields (update.fieldErrors); anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The monitoring item was not saved',
            subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
      },
    );
  };

  return (
    <Modal
      open
      modalHeading={`Edit monitoring item ${number(monitor.number)}`}
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="edit-monitor-dialog"
      className="structure-detail__monitor-dialog"
    >
      <div className="structure-detail__dialog-fields">
        <ReadOnlyField label="Monitor Number" value={number(monitor.number)} />
        <Select
          id="edit-monitor-status"
          data-testid="edit-monitor-status"
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
        <Select
          id="edit-monitor-frequency"
          data-testid="edit-monitor-frequency"
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
              id="edit-monitor-frequency-comment"
              data-testid="edit-monitor-frequency-comment"
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
            id="edit-monitor-description"
            data-testid="edit-monitor-description"
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
        <Button kind="tertiary" size="md" onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={update.isPending}
          data-testid="edit-monitor-save"
        >
          Save
        </Button>
      </div>
    </Modal>
  );
};

export default EditMonitorModal;
