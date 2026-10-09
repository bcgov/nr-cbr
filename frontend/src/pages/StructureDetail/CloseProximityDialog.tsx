import { Button, Modal } from '@carbon/react';
import { useState } from 'react';

import { toIso } from './dateBox';
import DateField from './DateField';

import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAddCloseProximityInspection } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

type Props = {
  structureId: string;
  onClose: () => void;
};

/**
 * Records a close proximity inspection that was done — legacy's P.Eng date box and add icon on the
 * Inspections tab, as a dialog like the tab's comments. Any real date; the schedule's next planned
 * close proximity inspection is left as it is, as legacy leaves it.
 */
const CloseProximityDialog: FC<Props> = ({ structureId, onClose }) => {
  const add = useAddCloseProximityInspection(structureId);
  const { display } = useNotification();
  const [date, setDate] = useState('');
  /** The form's own error, set on Save and cleared as the date changes. */
  const [error, setError] = useState<string>();
  const shown = error ?? add.fieldErrors.completedDate;

  const save = () => {
    const completedDate = toIso(date);
    let found: string | undefined;
    if (!date.trim()) found = 'Date is required.';
    else if (completedDate === null) found = 'Date must be a date, as yyyy/mm/dd.';
    setError(found);
    if (completedDate === null || found) return;
    add.mutate(
      { completedDate },
      {
        onSuccess: () => {
          display({
            kind: 'success',
            title: 'Close proximity inspection recorded',
            timeout: 4000,
          });
          onClose();
        },
        onError: (failure) => {
          // A refused date shows under the box; anything else is a toast.
          if (Object.keys(apiFieldErrors(failure)).length > 0) return;
          display({
            kind: 'error',
            title: 'The close proximity inspection was not recorded',
            subtitle: apiErrorMessage(failure, 'Try again, or contact support if this continues.'),
            timeout: 0,
          });
        },
      },
    );
  };

  return (
    <Modal
      open
      modalHeading="Add close proximity inspection"
      // Small: the dialog holds one date, so Carbon's default width leaves it adrift.
      size="sm"
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="close-proximity-dialog"
      className="structure-detail__close-proximity-dialog"
    >
      <div className="structure-detail__edit-cell">
        <DateField
          id="close-proximity-date"
          labelText="Date completed"
          value={date}
          invalidText={shown}
          onChange={(value) => {
            setDate(value);
            setError(undefined);
          }}
        />
      </div>

      <div className="structure-detail__dialog-actions">
        <Button kind="tertiary" size="md" onClick={onClose} disabled={add.isPending}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={add.isPending}
          data-testid="close-proximity-save"
        >
          Add
        </Button>
      </div>
    </Modal>
  );
};

export default CloseProximityDialog;
