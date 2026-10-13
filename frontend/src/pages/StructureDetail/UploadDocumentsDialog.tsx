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

import { toBox, toIso } from './dateBox';
import DateField from './DateField';

import type { DocumentDetailsRequest } from './documentsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useDocumentExtensions, useFileAttachmentTypeCodes } from '@/hooks/useConfiguration';
import { useAddDocuments } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

type Props = {
  structureId: string;
  onClose: () => void;
};

/** Legacy's upload page: fifteen rows, 150 MB in all, 10 MB a file — here for every file. */
const MAX_FILES = 15;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_BYTES = 150 * 1024 * 1024;
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
 * choose or drop up to fifteen files (150 MB in all, 10 MB each), then give each its Photo View,
 * the date it was made (today unless changed, as legacy) and a description, required for Other.
 * One Add stores them all, or none.
 *
 * <p>Every rule is checked here before anything is sent; whatever the server still refuses lands
 * beside the file's field. Unlike legacy, a refusal keeps the chosen files.
 */
const UploadDocumentsDialog: FC<Props> = ({ structureId, onClose }) => {
  const types = useFileAttachmentTypeCodes();
  const extensionList = useDocumentExtensions();
  const extensions = new Set(extensionList.data ?? []);
  const add = useAddDocuments(structureId);
  const { display } = useNotification();

  const [rows, setRows] = useState<Row[]>([]);
  const [nextKey, setNextKey] = useState(0);
  /** The form's own errors, by row key, set on Add and cleared field by field as each changes. */
  const [errors, setErrors] = useState<Record<number, RowErrors>>({});
  /** A problem with the whole batch: too many files, or too much in all. */
  const [batchError, setBatchError] = useState<string>();
  /** The rows as last sent, so the server's `documents[i]` errors find their row. */
  const [sentKeys, setSentKeys] = useState<number[]>([]);

  const serverErrors = (key: number): RowErrors => {
    const index = sentKeys.indexOf(key);
    if (index < 0) return {};
    const found: RowErrors = {};
    for (const [field, message] of Object.entries(add.fieldErrors)) {
      const match = /^documents\[(\d+)]\.(\w+)$/.exec(field);
      if (match && Number(match[1]) === index) found[match[2] as Field] = message;
    }
    return found;
  };
  const shownFor = (key: number): RowErrors => ({ ...serverErrors(key), ...errors[key] });
  const shownBatch = batchError ?? add.fieldErrors.files;

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
    // A file the server would refuse — wrong type, too big, empty — is flagged on its row now, as
    // it is picked, rather than after Add.
    setErrors((current) => ({
      ...current,
      ...Object.fromEntries(
        added.map((row) => [row.key, { file: fileError(row.file, extensions) }]),
      ),
    }));
  };

  const change = (key: number, field: Exclude<Field, 'file'>, value: string) => {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, [field]: value } : row)),
    );
    setErrors((current) => ({ ...current, [key]: { ...current[key], [field]: undefined } }));
  };

  const remove = (key: number) => {
    setRows((current) => current.filter((row) => row.key !== key));
    setBatchError(undefined);
  };

  const save = () => {
    let batch: string | undefined;
    const total = rows.reduce((sum, row) => sum + row.file.size, 0);
    if (rows.length === 0) batch = 'Choose at least one file.';
    else if (rows.length > MAX_FILES) {
      batch = `At most ${MAX_FILES} files can be added at a time.`;
    } else if (total > MAX_TOTAL_BYTES) {
      batch = `The files come to ${megabytes(total)}; at most 150 MB can be added at a time.`;
    }
    const found = Object.fromEntries(rows.map((row) => [row.key, rowErrors(row, extensions)]));
    setBatchError(batch);
    setErrors(found);
    if (batch || Object.values(found).some(hasErrors)) return;

    const details: DocumentDetailsRequest[] = rows.map((row) => ({
      attachmentTypeCode: row.attachmentTypeCode,
      createdDate: toIso(row.createdDate) as string,
      description: row.description,
    }));
    setSentKeys(rows.map((row) => row.key));
    add.mutate(
      { files: rows.map((row) => row.file), details },
      {
        onSuccess: ({ ids }) => {
          display({
            kind: 'success',
            title: ids.length === 1 ? '1 document added' : `${ids.length} documents added`,
            timeout: 4000,
          });
          onClose();
        },
        onError: (error) => {
          // Refusals show beside their files; anything else is a toast.
          if (Object.keys(apiFieldErrors(error)).length > 0) return;
          display({
            kind: 'error',
            title: 'The documents were not added',
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
        <p className="structure-detail__upload-intro">
          All fields are required unless marked optional.
        </p>

        <div>
          <span className="structure-detail__upload-label">Files</span>
          <p className="structure-detail__upload-helper">
            Up to {MAX_FILES} files, 10 MB each and 150 MB in all.
          </p>
          <FileUploaderDropContainer
            labelText="Drag and drop files here or click to upload"
            multiple
            disabled={add.isPending}
            className="structure-detail__upload-drop"
            data-testid="upload-documents-drop"
            onAddFiles={(_, { addedFiles }) => addFiles(addedFiles)}
          />
          {shownBatch && (
            <p
              className="structure-detail__upload-error"
              role="alert"
              data-testid="upload-batch-error"
            >
              {shownBatch}
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
                labelText="Photo View"
                value={row.attachmentTypeCode}
                disabled={types.isLoading}
                invalid={Boolean(shown.attachmentTypeCode)}
                invalidText={shown.attachmentTypeCode}
                onChange={(event) => change(row.key, 'attachmentTypeCode', event.target.value)}
              >
                <SelectItem value="" text="— Select Photo View —" />
                {(types.data ?? []).map((option) => (
                  <SelectItem key={option.code} value={option.code} text={option.description} />
                ))}
              </Select>
              <DateField
                id={`upload-created-${row.key}`}
                labelText="Photo/Doc Created"
                value={row.createdDate}
                invalidText={shown.createdDate}
                onChange={(value) => change(row.key, 'createdDate', value)}
              />
              <FieldWithCounter used={byteLength(row.description)} limit={TEXT_MAX}>
                <TextArea
                  id={`upload-description-${row.key}`}
                  data-testid={`upload-description-${row.key}`}
                  labelText={
                    row.attachmentTypeCode === OTHER ? 'Description' : 'Description (optional)'
                  }
                  rows={3}
                  value={row.description}
                  invalid={Boolean(shown.description)}
                  invalidText={shown.description}
                  onChange={(event) => change(row.key, 'description', event.target.value)}
                />
              </FieldWithCounter>
            </section>
          );
        })}
      </Stack>

      <div className="structure-detail__upload-actions">
        <Button kind="tertiary" onClick={onClose} disabled={add.isPending}>
          Cancel
        </Button>
        <Button kind="primary" onClick={save} disabled={add.isPending} data-testid="upload-save">
          {add.isPending ? 'Adding…' : 'Add documents'}
        </Button>
      </div>
    </Modal>
  );
};

export default UploadDocumentsDialog;
