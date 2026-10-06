import DestructiveModal from '@/components/core/DestructiveModal';

import { blockedLine, selectedStructures, splitForDelete, structures } from './selection';

import type { SelectedStructure } from './types';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useDeleteStructures } from '@/hooks/useStructureSearch';

type Props = {
  open: boolean;
  /** Every ticked structure, on every page. */
  selected: ReadonlyMap<string, SelectedStructure>;
  onClose: () => void;
  /** The ids that are gone, for the page to untick. Everything else stays ticked. */
  onDeleted: (ids: string[]) => void;
};

/** How many skipped structures the dialog names before summing up the rest. */
const LISTED = 10;

/**
 * The confirmation behind Structure Search's Delete, and the deletes themselves.
 *
 * <p>Legacy confirms with a fixed paragraph listing what a structure must not have, then tells the
 * user afterwards which ones it refused. This says beforehand which of the ticked structures will
 * be skipped and why, from what the search reported, so the count the user confirms is the count
 * that will be deleted — unless something changed in between, which the server catches and the
 * outcome reports.
 *
 * <p>The page opens this only when at least one ticked structure can be deleted.
 */
const DeleteStructuresDialog: FC<Props> = ({ open, selected, onClose, onDeleted }) => {
  const { display } = useNotification();
  const remove = useDeleteStructures();
  const { deletable, blocked } = splitForDelete(selected);

  // Spans rather than paragraphs and a list: the modal already wraps its message in a <p>.
  const message =
    blocked.length === 0 ? (
      `Are you sure you would like to delete ${selectedStructures(deletable.length)}? ` +
      'Deleted structures cannot be recovered.'
    ) : (
      <>
        <span className="structure-search__dialog-line">
          {deletable.length} of {selectedStructures(selected.size)} will be deleted. These cannot be
          deleted and will be skipped:
        </span>
        <span className="structure-search__skipped" data-testid="structure-delete-skipped">
          {blocked.slice(0, LISTED).map((structure) => (
            <span key={structure.id} className="structure-search__dialog-line">
              • {blockedLine(structure)}
            </span>
          ))}
          {blocked.length > LISTED && (
            <span className="structure-search__dialog-line">
              • and {structures(blocked.length - LISTED)} more
            </span>
          )}
        </span>
        <span className="structure-search__dialog-line">
          Deleted structures cannot be recovered.
        </span>
      </>
    );

  const confirm = () => {
    remove.mutate(deletable, {
      onSuccess: ({ deleted, failed }) => {
        onClose();
        onDeleted(deleted.map(({ id }) => id));
        if (deleted.length > 0) {
          display({
            kind: 'success',
            title: `${structures(deleted.length)} deleted`,
            subtitle:
              blocked.length > 0
                ? `${structures(blocked.length)} skipped, and still ticked.`
                : undefined,
            timeout: 4000,
          });
        }
        if (failed.length > 0) {
          // The server's own sentence for each: "Structure B100 has inspections and cannot be
          // deleted." Pinned until dismissed — an error toast's timeout is ignored.
          display({
            kind: 'error',
            title: `${structures(failed.length)} not deleted`,
            subtitle: failed.map(({ reason }) => reason).join(' '),
            timeout: 0,
          });
        }
      },
    });
  };

  return (
    <DestructiveModal
      open={open}
      title="Delete structures"
      message={message}
      confirmButtonText="Delete"
      loading={remove.isPending}
      onCancel={onClose}
      onConfirm={confirm}
    />
  );
};

export default DeleteStructuresDialog;
