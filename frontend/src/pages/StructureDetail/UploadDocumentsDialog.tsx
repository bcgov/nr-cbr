import { Close, Document as DocumentIcon } from '@carbon/icons-react';
import {
  Button,
  FileUploaderDropContainer,
  Modal,
  Select,
  SelectItem,
  Stack,
  TextArea,
} from '@carbon/react';
import { useState } from 'react';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import { requiredLabel } from '@/utils/requiredLabel';

import { toBox, toIso } from './dateBox';
import DateField from './DateField';

import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useDocumentExtensions, useFileAttachmentTypeCodes } from '@/hooks/useConfiguration';
import { holdFocus } from '@/hooks/useFieldErrors';
import { useAddDocuments, type DocumentUploadOutcome } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  onClose: () => void;
};

/**
 * Legacy's upload page: fifteen rows, 10 MB a file — here for every file. Legacy's 150 MB in all
 * follows from those two, so it is not checked on its own.
 */
const MAX_FILES = 15;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
/** `FILENAME VARCHAR2(255 BYTE)`. */
const NAME_MAX = 255;
/** The description column, in bytes. */
const TEXT_MAX = 2000;
/** The type whose file must be described; legacy also picks it for a PDF. */
const OTHER = 'OTH';

/** One file and its details as typed. */
type Row = {
  key: number;
  file: File;
  attachmentTypeCode: string;
  /** `yyyy/mm/dd` as the box holds it. */
  createdDate: string;
  description: string;
};

type Field = 'file' | 'attachmentTypeCode' | 'createdDate' | 'description';
type RowErrors = Partial<Record<Field, string>>;

const extensionOf = (name: string): string => {
  const dot = name.lastIndexOf('.');
  return dot < 0 || dot === name.length - 1 ? '' : name.slice(dot + 1).toUpperCase();
};

const megabytes = (bytes: number) => `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

const today = () => {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return toBox(`${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`);
};

/**
 * One file's own problems: its name, size and type. The accepted types are `FILE_MIME_TYPE_XREF`'s,
 * as legacy's "File Type is invalid" — checked here as the file is picked, and again by the server,
 * but never listed, as legacy lists them nowhere. While the list has not arrived (or came back
 * empty) only the server checks.
 */
const fileError = (file: File, extensions: ReadonlySet<string>): string | undefined => {
  if (byteLength(file.name) > NAME_MAX) {
    return `The file name can be at most ${NAME_MAX} characters.`;
  }
  if (file.size === 0) return `${file.name} is empty.`;
  if (file.size > MAX_FILE_BYTES) return `${file.name} is larger than 10 MB.`;
  const extension = extensionOf(file.name);
  if (!extension) return `${file.name} has no file type.`;
  if (extensions.size > 0 && !extensions.has(extension)) {
    return `Files of type .${extension.toLowerCase()} cannot be added.`;
  }
  return undefined;
};

/** The form's own rules for one row — the server's, checked before the round trip. */
const rowErrors = (row: Row, extensions: ReadonlySet<string>): RowErrors => {
  const errors: RowErrors = { file: fileError(row.file, extensions) };
  if (!row.attachmentTypeCode) errors.attachmentTypeCode = 'Photo View is required.';
  if (!row.createdDate.trim()) errors.createdDate = 'Photo/Doc Created is required.';
  else if (toIso(row.createdDate) === null) {
    errors.createdDate = 'Photo/Doc Created must be a date, as yyyy/mm/dd.';
  }
  if (row.attachmentTypeCode === OTHER && !row.description.trim()) {
    errors.description = 'Description is required for this Photo View.';
  } else if (overLimitError(row.description, TEXT_MAX)) {
    errors.description = overLimitError(row.description, TEXT_MAX);
  }
  return errors;
};

const hasErrors = (errors: RowErrors) => Object.values(errors).some(Boolean);

/**
 * Adds documents and photos to the structure — legacy's three-step upload pop-up as one small,
 * stacked dialog with a dashed drop zone and file chips:
 * choose or drop up to fifteen files (10 MB each), then give each its Photo View, the date it was
 * made (today unless changed, as legacy) and a description, required for Other.
 *
 * <p>Add sends the files one at a time, each in its own request (see `useAddDocuments`), so some
 * can be stored and others not, as in legacy. Those stored leave the dialog; those not stay, with
 * the reason beside each, to be corrected and added again.
 *
 * <p>Every rule is checked here before anything is sent; whatever the server still refuses lands
 * beside the file's field.
 */
const UploadDocumentsDialog: FC<Props> = ({ structureId, onClose }) => {
  const types = useFileAttachmentTypeCodes();
  const extensionList = useDocumentExtensions();
  const extensions = new Set(extensionList.data ?? []);
  const add = useAddDocuments(structureId);
  const { display } = useNotification();

  const [rows, setRows] = useState<Row[]>([]);
  const [nextKey, setNextKey] = useState(0);
  /**
   * The row fields the user has left, as `key.field` — each one's error shows from then on. A
   * file's own problem shows as soon as it is picked; everything shows once Add is pressed.
   */
  const [visited, setVisited] = useState<ReadonlySet<string>>(() => new Set());
  const [submitted, setSubmitted] = useState(false);
  /** What the server refused, by row key, cleared field by field as each changes. */
  const [serverErrors, setServerErrors] = useState<Record<number, RowErrors>>({});
  /** A problem with the whole batch: none chosen, too many, or some not added. */
  const [batchError, setBatchError] = useState<string>();
  /** While adding: how many have been sent, of how many. */
  const [progress, setProgress] = useState<{ sent: number; total: number }>();

  const leave = (key: number, field: Field) =>
    setVisited((current) => new Set(current).add(`${key}.${field}`));

  // The rows checked as they change; the form's own message wins where it has one shown, and a
  // field it found nothing wrong with is left to the server's.
  const shownFor = (key: number): RowErrors => {
    const row = rows.find((each) => each.key === key);
    const live = row ? rowErrors(row, extensions) : {};
    const own = Object.entries(live).filter(
      ([field, message]) =>
        message && (submitted || field === 'file' || visited.has(`${key}.${field}`)),
    );
    return { ...serverErrors[key], ...Object.fromEntries(own) };
  };

  const addFiles = (files: File[]) => {
    const offersOther = (types.data ?? []).some((option) => option.code === OTHER);
    const added = files.map((file, index) => ({
      key: nextKey + index,
      file,
      // As legacy: a PDF starts as Other; anything else waits for a choice.
      attachmentTypeCode: offersOther && extensionOf(file.name) === 'PDF' ? OTHER : '',
      createdDate: today(),
      description: '',
    }));
    setNextKey(nextKey + files.length);
    setRows((current) => [...current, ...added]);
    setBatchError(undefined);
    // A file the server would refuse — wrong type, too big, empty — is flagged on its row as it is
    // picked, rather than after Add: `shownFor` shows a file's own problem at once.
  };

  const change = (key: number, field: Exclude<Field, 'file'>, value: string) => {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, [field]: value } : row)),
    );
    setServerErrors((current) => ({
      ...current,
      [key]: { ...current[key], [field]: undefined },
    }));
  };

  const remove = (key: number) => {
    setRows((current) => current.filter((row) => row.key !== key));
    setBatchError(undefined);
  };

  /** Closes when every file was stored; otherwise keeps the rest, each with its reason. */
  const settle = ({ added, failed }: DocumentUploadOutcome) => {
    setProgress(undefined);
    if (failed.length === 0) {
      display({
        kind: 'success',
        title: added.length === 1 ? '1 document added' : `${added.length} documents added`,
        timeout: 4000,
      });
      onClose();
      return;
    }
    setRows((current) => current.filter((row) => !added.includes(row.key)));
    setServerErrors(
      Object.fromEntries(
        failed.map(({ key, error }) => {
          const fields = apiFieldErrors(error) as RowErrors;
          return [
            key,
            Object.keys(fields).length > 0
              ? fields
              : {
                  file: `Not added: ${apiErrorMessage(error, 'try again, or contact support if this continues.')}`,
                },
          ];
        }),
      ),
    );
    setBatchError(
      added.length === 0
        ? 'No files were added. Correct the files below and add them again.'
        : `${added.length} of ${added.length + failed.length} files were added. ` +
            'Correct the files below and add them again.',
    );
  };

  const save = () => {
    let batch: string | undefined;
    if (rows.length === 0) batch = 'Choose at least one file.';
    else if (rows.length > MAX_FILES) {
      batch = `At most ${MAX_FILES} files can be added at a time.`;
    }
    const found = rows.map((row) => rowErrors(row, extensions));
    setBatchError(batch);
    setSubmitted(true);
    setServerErrors({});
    if (batch || found.some(hasErrors)) return;

    const uploads = rows.map((row) => ({
      key: row.key,
      file: row.file,
      details: {
        attachmentTypeCode: row.attachmentTypeCode,
        createdDate: toIso(row.createdDate) as string,
        description: row.description,
      },
    }));
    setProgress({ sent: 0, total: uploads.length });
    add.mutate(
      { uploads, onProgress: (sent) => setProgress({ sent, total: uploads.length }) },
      { onSuccess: settle },
    );
  };

  return (
    <Modal
      open
      modalHeading="Add documents and photos"
      // Small, its form stacked, the buttons drawn below.
      passiveModal
      preventCloseOnClickOutside
      size="sm"
      onRequestClose={onClose}
      data-testid="upload-documents-dialog"
      className="structure-detail__upload-dialog"
    >
      <Stack gap={5}>
        <div>
          <span className="structure-detail__upload-label">{requiredLabel('Files', true)}</span>
          <p className="structure-detail__upload-helper">Up to {MAX_FILES} files, 10 MB each.</p>
          <FileUploaderDropContainer
            labelText="Drag and drop files here or click to upload"
            multiple
            disabled={add.isPending}
            className="structure-detail__upload-drop"
            data-testid="upload-documents-drop"
            onAddFiles={(_, { addedFiles }) => addFiles(addedFiles)}
          />
          {batchError && (
            <p
              className="structure-detail__upload-error"
              role="alert"
              data-testid="upload-batch-error"
            >
              {batchError}
            </p>
          )}
        </div>

        {rows.map((row) => {
          const shown = shownFor(row.key);
          return (
            <section
              key={row.key}
              className="structure-detail__upload-row"
              aria-label={row.file.name}
              data-testid={`upload-row-${row.key}`}
            >
              {/* The file chip: a white card naming the file, with its remove button. */}
              <div
                className={
                  shown.file
                    ? 'structure-detail__upload-chip structure-detail__upload-chip--error'
                    : 'structure-detail__upload-chip'
                }
              >
                <DocumentIcon size={16} aria-hidden="true" />
                <span className="structure-detail__upload-name">{row.file.name}</span>
                <span className="structure-detail__upload-size">{megabytes(row.file.size)}</span>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  renderIcon={Close}
                  iconDescription={`Remove ${row.file.name}`}
                  tooltipPosition="left"
                  disabled={add.isPending}
                  data-testid={`upload-remove-${row.key}`}
                  onClick={() => remove(row.key)}
                />
              </div>
              {shown.file && (
                <p className="structure-detail__upload-error" role="alert">
                  {shown.file}
                </p>
              )}
              <Select
                id={`upload-type-${row.key}`}
                data-testid={`upload-type-${row.key}`}
                labelText={requiredLabel('Photo View', true)}
                value={row.attachmentTypeCode}
                disabled={types.isLoading}
                invalid={Boolean(shown.attachmentTypeCode)}
                invalidText={shown.attachmentTypeCode}
                onChange={(event) => change(row.key, 'attachmentTypeCode', event.target.value)}
                onBlur={() => leave(row.key, 'attachmentTypeCode')}
              >
                <SelectItem value="" text="— Select Photo View —" />
                {(types.data ?? []).map((option) => (
                  <SelectItem key={option.code} value={option.code} text={option.description} />
                ))}
              </Select>
              <DateField
                id={`upload-created-${row.key}`}
                labelText={requiredLabel('Photo/Doc Created', true)}
                value={row.createdDate}
                invalidText={shown.createdDate}
                onChange={(value) => change(row.key, 'createdDate', value)}
                onBlur={() => leave(row.key, 'createdDate')}
              />
              <FieldWithCounter used={byteLength(row.description)} limit={TEXT_MAX}>
                <TextArea
                  id={`upload-description-${row.key}`}
                  data-testid={`upload-description-${row.key}`}
                  labelText={requiredLabel('Description', row.attachmentTypeCode === OTHER)}
                  rows={3}
                  value={row.description}
                  invalid={Boolean(shown.description)}
                  invalidText={shown.description}
                  onChange={(event) => change(row.key, 'description', event.target.value)}
                  onBlur={() => leave(row.key, 'description')}
                />
              </FieldWithCounter>
            </section>
          );
        })}
      </Stack>

      <div className="structure-detail__upload-actions">
        <Button onMouseDown={holdFocus} kind="tertiary" onClick={onClose} disabled={add.isPending}>
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          onClick={save}
          disabled={add.isPending}
          data-testid="upload-save"
        >
          {progress ? `Adding ${progress.sent + 1} of ${progress.total}…` : 'Add documents'}
        </Button>
      </div>
    </Modal>
  );
};

export default UploadDocumentsDialog;
