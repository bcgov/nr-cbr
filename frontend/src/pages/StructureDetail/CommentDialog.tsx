import { Button, Modal, TextArea } from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import type { StructureComment } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import {
  useAddPlannedInspectionComment,
  useAddStructureComment,
  useUpdateStructureComment,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /**
   * The comment being edited, or null to add one. The dialog is mounted only while it is open, so
   * it opens fresh each time.
   */
  comment: StructureComment | null;
  /** Which comments: the Details tab's general ones, or the Inspections tab's planned-inspection. */
  kind?: 'general' | 'planned';
  onClose: () => void;
};

/** The comment column, in bytes. */
const TEXT_MAX = 2000;

/**
 * Adds a comment, or changes a comment's text — legacy's add box and edit pencil on the Details tab
 * (general comments) and the Inspections tab (planned-inspection ones), as a dialog like the tabs'
 * other items'.
 *
 * <p>The error sits under the box — the form's own rule on Save, then whatever the server refused.
 * A save that fails for no field's sake is a toast, as the app's other actions report one.
 */
const CommentDialog: FC<Props> = ({ structureId, comment, kind = 'planned', onClose }) => {
  const addGeneral = useAddStructureComment(structureId);
  const addPlanned = useAddPlannedInspectionComment(structureId);
  const add = kind === 'general' ? addGeneral : addPlanned;
  const noun = kind === 'general' ? 'comment' : 'planned inspection comment';
  const update = useUpdateStructureComment(structureId);
  const saving = add.isPending || update.isPending;
  const { display } = useNotification();

  const [text, setText] = useState(comment?.text ?? '');
  // Checked as the text changes; shown once the box is left, or at Save.
  const form = useFieldErrors<'comment'>(
    { comment: text.trim() ? overLimitError(text, TEXT_MAX) || undefined : 'Comment is required.' },
    comment === null ? add.fieldErrors : update.fieldErrors,
  );
  const shown = form.shown.comment;

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
    if (!form.submit()) return;
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
      modalHeading={comment === null ? `Add ${noun}` : `Edit ${noun}`}
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
          labelText={requiredLabel('Comment', true)}
          rows={5}
          value={text}
          invalid={Boolean(shown)}
          invalidText={shown}
          onChange={(event) => {
            setText(event.target.value);
            form.change('comment');
          }}
          onBlur={() => form.leave('comment')}
        />
      </FieldWithCounter>

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
