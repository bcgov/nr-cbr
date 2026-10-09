import { Button, Modal, TextArea } from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';

import type { StructureComment } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import {
  useAddPlannedInspectionComment,
  useUpdateStructureComment,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /**
   * The comment being edited, or null to add a planned-inspection one. The dialog is mounted only
   * while it is open, so it opens fresh each time.
   */
  comment: StructureComment | null;
  onClose: () => void;
};

/** The comment column, in bytes. */
const TEXT_MAX = 2000;

/**
 * Adds a planned-inspection comment, or changes a comment's text — legacy's add box and edit pencil
 * on the Inspections tab, as a dialog like the tab's other items'.
 *
 * <p>The error sits under the box — the form's own rule on Save, then whatever the server refused.
 * A save that fails for no field's sake is a toast, as the app's other actions report one.
 */
const CommentDialog: FC<Props> = ({ structureId, comment, onClose }) => {
  const add = useAddPlannedInspectionComment(structureId);
  const update = useUpdateStructureComment(structureId);
  const saving = add.isPending || update.isPending;
  const { display } = useNotification();

  const [text, setText] = useState(comment?.text ?? '');
  /** The form's own error, set on Save and cleared as the text changes. */
  const [error, setError] = useState<string>();
  const shown = error ?? (comment === null ? add.fieldErrors : update.fieldErrors).comment;

  const failed = (failure: unknown) => {
    // A refused text shows under the box; anything else is a toast.
    if (Object.keys(apiFieldErrors(failure)).length > 0) return;
    display({
      kind: 'error',
      title: comment === null ? 'The comment was not added' : 'The comment was not saved',
      subtitle: apiErrorMessage(failure, 'Try again, or contact support if this continues.'),
      timeout: 0,
    });
  };

  const save = () => {
    const found = text.trim() ? overLimitError(text, TEXT_MAX) : 'Comment is required.';
    setError(found || undefined);
    if (found) return;
    const done = (title: string) => () => {
      display({ kind: 'success', title, timeout: 4000 });
      onClose();
    };
    if (comment === null) {
      add.mutate({ comment: text }, { onSuccess: done('Comment added'), onError: failed });
      return;
    }
    update.mutate(
      { commentId: comment.id, request: { comment: text } },
      { onSuccess: done('Comment saved'), onError: failed },
    );
  };

  return (
    <Modal
      open
      modalHeading={
        comment === null ? 'Add planned inspection comment' : 'Edit planned inspection comment'
      }
      // Passive, with the buttons drawn below, as the app's other form dialogs close.
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="comment-dialog"
      className="structure-detail__comment-dialog"
    >
      <FieldWithCounter used={byteLength(text)} limit={TEXT_MAX}>
        <TextArea
          id="comment-text"
          data-testid="comment-text"
          labelText="Comment"
          rows={5}
          value={text}
          invalid={Boolean(shown)}
          invalidText={shown}
          onChange={(event) => {
            setText(event.target.value);
            setError(undefined);
          }}
        />
      </FieldWithCounter>

      <div className="structure-detail__dialog-actions">
        <Button kind="tertiary" size="md" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          onClick={save}
          disabled={saving}
          data-testid="comment-save"
        >
          {comment === null ? 'Add' : 'Save'}
        </Button>
      </div>
    </Modal>
  );
};

export default CommentDialog;
