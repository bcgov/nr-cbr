import { Button, Modal, Select, SelectItem, Stack, TextArea } from '@carbon/react';
import { useMemo, useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import ReadOnlyField from '@/components/core/ReadOnlyField';

import DocumentThumbnail from './DocumentThumbnail';

import type { StructureDocument } from './documentsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useFileAttachmentTypeCodes } from '@/hooks/useConfiguration';
import { useUpdateDocument } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  /** The document being edited. The dialog is mounted only while it is open. */
  document: StructureDocument;
  onClose: () => void;
};

/** The description column, in bytes. */
const TEXT_MAX = 2000;
/** The type whose file must be described. */
const OTHER = 'OTH';

type Errors = { attachmentTypeCode?: string; description?: string };

/**
 * Changes a document's Photo View and description — legacy's edit in its document viewer. The
 * file is shown — a thumbnail of an image, so the user knows which one they
 * are changing — with its name and date, which are not changed, as legacy's.
 *
 * <p>Laid out as the app's other dialogs: Carbon's grey dialog with its white fields.
 */
const EditDocumentDialog: FC<Props> = ({ structureId, document, onClose }) => {
  const types = useFileAttachmentTypeCodes();
  const update = useUpdateDocument(structureId);
  const { display } = useNotification();

  const [type, setType] = useState(document.attachmentType.code ?? '');
  const [description, setDescription] = useState(document.description ?? '');
  const [errors, setErrors] = useState<Errors>({});
  const shown: Errors = { ...update.fieldErrors, ...errors };

  // A stored type since expired is still the document's, so it stays in the list.
  const options = useMemo(() => {
    const listed = types.data ?? [];
    const stored = document.attachmentType;
    return stored.code && !listed.some((option) => option.code === stored.code)
      ? [{ code: stored.code, description: stored.description ?? stored.code }, ...listed]
      : listed;
  }, [types.data, document.attachmentType]);

  const save = () => {
    const found: Errors = {};
    if (!type) found.attachmentTypeCode = 'Photo View is required.';
    if (type === OTHER && !description.trim()) {
      found.description = 'Description is required for this Photo View.';
    } else if (overLimitError(description, TEXT_MAX)) {
      found.description = overLimitError(description, TEXT_MAX);
    }
    setErrors(found);
    if (found.attachmentTypeCode || found.description) return;
    update.mutate(
      { fileId: document.id, request: { attachmentTypeCode: type, description } },
      {
        onSuccess: () => {
          display({ kind: 'success', title: 'Document saved', timeout: 4000 });
          onClose();
        },
        onError: (error) => {
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The document was not saved',
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
      modalHeading="Edit document"
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="edit-document-dialog"
      size="sm"
      className="structure-detail__upload-dialog"
    >
      <Stack gap={6}>
        <div className="structure-detail__document-summary">
          <DocumentThumbnail structureId={structureId} document={document} />
          <div className="structure-detail__document-facts">
            <ReadOnlyField label="File" value={document.filename ?? ''} />
            <ReadOnlyField label="Photo/Doc Created" value={formatShortDate(document.created)} />
          </div>
        </div>
        <Select
          id="edit-document-type"
          data-testid="edit-document-type"
          labelText="Photo View"
          value={type}
          disabled={types.isLoading}
          invalid={Boolean(shown.attachmentTypeCode)}
          invalidText={shown.attachmentTypeCode}
          onChange={(event) => {
            setType(event.target.value);
            setErrors((current) => ({ ...current, attachmentTypeCode: undefined }));
          }}
        >
          <SelectItem value="" text="Choose a type" />
          {options.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
        <FieldWithCounter used={byteLength(description)} limit={TEXT_MAX}>
          <TextArea
            id="edit-document-description"
            data-testid="edit-document-description"
            labelText={type === OTHER ? 'Description' : 'Description (optional)'}
            rows={4}
            value={description}
            invalid={Boolean(shown.description)}
            invalidText={shown.description}
            onChange={(event) => {
              setDescription(event.target.value);
              setErrors((current) => ({ ...current, description: undefined }));
            }}
          />
        </FieldWithCounter>
      </Stack>
      <div className="structure-detail__upload-actions">
        <Button kind="tertiary" onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button
          kind="primary"
          onClick={save}
          disabled={update.isPending}
          data-testid="edit-document-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </Modal>
  );
};

export default EditDocumentDialog;
