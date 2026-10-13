import { Add, Edit, Folder, Launch, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  InlineNotification,
  Pagination,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';

import Card from './Card';
import EditDocumentDialog from './EditDocumentDialog';
import { opensInTab, openDocument } from './openDocument';
import UploadDocumentsDialog from './UploadDocumentsDialog';

import type { StructureDocument } from './documentsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useDeleteDocument, useStructureDocuments } from '@/hooks/useStructureSearch';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
  /** The year the superstructure was installed; files from before it are hidden until asked. */
  yearBuilt: number | null;
};

/** The documents of one folder — the structure's own, or one inspection's. */
type Group = { key: string; title: string; documents: StructureDocument[] };

/**
 * Whether a file predates the superstructure — legacy hides those unless asked, as it does load
 * ratings: a file made before 1 January of the year the superstructure was installed.
 */
const beforeInstall = (document: StructureDocument, yearBuilt: number | null): boolean =>
  yearBuilt !== null && document.created !== null && document.created < `${yearBuilt}-01-01`;

/** Legacy's folders, in the server's order: the structure's own first, then each inspection's. */
const groupDocuments = (documents: StructureDocument[]): Group[] => {
  const groups = new Map<string, Group>();
  for (const document of documents) {
    const key = document.inspectionId ?? 'structure';
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        title:
          document.inspectionId === null
            ? 'Structure Documents & Photos'
            : `Inspection: ${formatShortDate(document.inspectionDate) || document.inspectionId}`,
        documents: [],
      });
    }
    groups.get(key)!.documents.push(document);
  }
  return [...groups.values()];
};

/** Rows a card shows at first; the user can choose more. */
const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

type TableProps = {
  title: string;
  documents: StructureDocument[];
  /** The file being fetched, if any. */
  opening: string | null;
  onOpen: (document: StructureDocument) => void;
  /** Offered from Level 1 up — legacy's edit in its viewer. */
  onEdit?: (document: StructureDocument) => void;
  /** Offered from Level 2 up — legacy's delete icon. */
  onDelete?: (document: StructureDocument) => void;
};

/**
 * One card's files, a page at a time. Paged here rather than by the server: a structure has tens of
 * files, not thousands, and the list arrives whole.
 */
const DocumentsTable: FC<TableProps> = ({
  title,
  documents,
  opening,
  onOpen,
  onEdit,
  onDelete,
}) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Back to the last page there is when the list shrinks under it — when early files are hidden.
  const lastPage = Math.max(1, Math.ceil(documents.length / pageSize));
  const current = Math.min(page, lastPage);
  const rows = documents.slice((current - 1) * pageSize, current * pageSize);

  return (
    <>
      {/* Six columns and three actions: anything wider than the card scrolls inside it. */}
      <div className="structure-detail__table-scroll">
        <Table size="md" useZebraStyles aria-label={title}>
          <TableHead>
            <TableRow>
              <TableHeader className="structure-detail__nowrap">Available Documents</TableHeader>
              <TableHeader className="structure-detail__nowrap">Photo/Doc Created</TableHeader>
              <TableHeader className="structure-detail__nowrap">Type</TableHeader>
              <TableHeader className="structure-detail__nowrap">Obtained From</TableHeader>
              <TableHeader>Description</TableHeader>
              {/* Always: View is everyone's. */}
              <TableHeader>Actions</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((document) => {
              const inTab = opensInTab(document);
              const name = document.filename ?? 'document';
              return (
                <TableRow key={document.id} data-testid={`structure-document-${document.id}`}>
                  <TableCell>
                    {document.attachmentType.description ||
                      document.attachmentType.code ||
                      document.filename}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {formatShortDate(document.created)}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">{document.extension}</TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {document.inspectionId === null ? 'Structure' : 'Inspection'}
                  </TableCell>
                  <TableCell className="structure-detail__comment">
                    {document.description}
                  </TableCell>
                  <TableCell className="structure-detail__nowrap">
                    {/* View, on every row alike: an image or a PDF opens in a new tab,
                        anything else downloads — a browser cannot show it, and one from this origin
                        must not run. The hidden text says which. */}
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Launch}
                      disabled={opening === document.id}
                      aria-busy={opening === document.id}
                      data-testid={`structure-document-view-${document.id}`}
                      onClick={() => onOpen(document)}
                    >
                      View{' '}
                      <span className="cds--visually-hidden">
                        {name}
                        {inTab ? ' (opens in a new tab)' : ' (downloads)'}
                      </span>
                    </Button>
                    {onEdit && (
                      <Button
                        kind="ghost"
                        size="sm"
                        renderIcon={Edit}
                        data-testid={`structure-document-edit-${document.id}`}
                        onClick={() => onEdit(document)}
                      >
                        Edit <span className="cds--visually-hidden">{name}</span>
                      </Button>
                    )}
                    {/* danger--ghost, as every Delete in the app: red at rest and on hover. */}
                    {onDelete && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        data-testid={`structure-document-delete-${document.id}`}
                        onClick={() => onDelete(document)}
                      >
                        Delete <span className="cds--visually-hidden">{name}</span>
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <Pagination
        page={current}
        pageSize={pageSize}
        pageSizes={PAGE_SIZES}
        totalItems={documents.length}
        onChange={({ page: nextPage, pageSize: nextPageSize }) => {
          setPage(nextPage);
          setPageSize(nextPageSize);
        }}
      />
    </>
  );
};

/**
 * Legacy's Documents & Photos tab (`documentTab.jsp`): the structure's files, then each
 * inspection's, a card for each of legacy's folders. Each row's View opens its file — an image or a
 * PDF in a new tab — or, for anything else, downloads it.
 *
 * <p>"Add documents" sits in the structure folder's header, from Level 2 up, as legacy's button —
 * files added here are the structure's own. Each row offers Edit (its Photo View and description,
 * Level 1 up) and Delete (Level 2 up), an inspection's files too, as legacy's tab did.
 */
const DocumentsTab: FC<Props> = ({ structureId, opened, yearBuilt }) => {
  const loaded = useStructureDocuments(structureId, opened);
  const [showEarly, setShowEarly] = useState(false);
  /** The file being fetched, so its link can say so and not be pressed twice. */
  const [opening, setOpening] = useState<string | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);
  const { canEdit, canDelete } = useAuthorization();
  const { display } = useNotification();
  const deleteDocument = useDeleteDocument(structureId);
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<StructureDocument | null>(null);
  const [pendingDelete, setPendingDelete] = useState<StructureDocument | null>(null);

  if (loaded.isError) {
    return (
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Documents and photos could not be loaded"
        subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
        data-testid="structure-documents-error"
      />
    );
  }

  if (!loaded.data) {
    // The test id on a wrapper: Carbon repeats a paragraph skeleton's props on every line.
    return (
      <div data-testid="structure-documents-loading">
        <SkeletonText paragraph lineCount={6} />
      </div>
    );
  }

  const all = loaded.data.documents;
  const hidden = all.filter((document) => beforeInstall(document, yearBuilt)).length;
  const shown = showEarly ? all : all.filter((document) => !beforeInstall(document, yearBuilt));
  const groups = groupDocuments(shown);
  // Add belongs to the structure's own folder, so that card is there to hold it even before the
  // structure has a file of its own.
  if (canDelete && groups.length > 0 && groups[0].key !== 'structure') {
    groups.unshift({ key: 'structure', title: 'Structure Documents & Photos', documents: [] });
  }
  const addButton = canDelete && (
    <Button
      kind="tertiary"
      size="sm"
      renderIcon={Add}
      data-testid="structure-documents-add"
      onClick={() => setUploading(true)}
    >
      Add documents
    </Button>
  );

  const open = async (document: StructureDocument) => {
    setOpening(document.id);
    setOpenError(null);
    try {
      await openDocument(document, () =>
        API.structureSearch.getDocumentFile(structureId, document.id),
      );
    } catch (error) {
      setOpenError(apiErrorMessage(error, 'Try again in a moment.'));
    } finally {
      setOpening(null);
    }
  };

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-documents-tab">
      {hidden > 0 && (
        <Checkbox
          id="structure-show-early-documents"
          labelText={`Show documents from before the superstructure was installed (${hidden})`}
          checked={showEarly}
          onChange={(_, { checked }) => setShowEarly(checked)}
        />
      )}

      {openError && (
        <InlineNotification
          kind="error"
          lowContrast
          title="The file could not be opened"
          subtitle={openError}
          onCloseButtonClick={() => setOpenError(null)}
          data-testid="structure-document-open-error"
        />
      )}

      {groups.length === 0 ? (
        <Card
          title="Documents & Photos"
          icon={Folder}
          testId="structure-documents-empty"
          action={addButton}
        >
          <p className="structure-detail__empty">No photos and/or documents have been loaded.</p>
        </Card>
      ) : (
        groups.map((group) => (
          <Card
            key={group.key}
            title={group.title}
            icon={Folder}
            testId={`structure-documents-${group.key}`}
            action={group.key === 'structure' ? addButton : undefined}
          >
            {group.documents.length === 0 ? (
              <p className="structure-detail__empty">
                No photos and/or documents have been loaded.
              </p>
            ) : (
              <DocumentsTable
                title={group.title}
                documents={group.documents}
                opening={opening}
                onOpen={(document) => void open(document)}
                onEdit={canEdit ? setEditing : undefined}
                onDelete={canDelete ? setPendingDelete : undefined}
              />
            )}
          </Card>
        ))
      )}

      {uploading && (
        <UploadDocumentsDialog structureId={structureId} onClose={() => setUploading(false)} />
      )}
      {editing !== null && (
        <EditDocumentDialog
          key={editing.id}
          structureId={structureId}
          document={editing}
          onClose={() => setEditing(null)}
        />
      )}
      {/* Legacy's window.confirm(), as the app's other deletes ask. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete document"
        message={`Are you sure you would like to delete ${
          pendingDelete?.filename ?? 'this document'
        }? This cannot be undone.`}
        confirmButtonText="Delete"
        loading={deleteDocument.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const document = pendingDelete;
          if (document === null) return;
          deleteDocument.mutate(document.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({
                kind: 'success',
                title: `${document.filename ?? 'Document'} deleted`,
                timeout: 4000,
              });
            },
            onError: (error) => {
              setPendingDelete(null);
              display({
                kind: 'error',
                title: 'The document was not deleted',
                subtitle: apiErrorMessage(
                  error,
                  'Try again, or contact support if this continues.',
                ),
                timeout: 0,
              });
            },
          });
        }}
      />
    </div>
  );
};

export default DocumentsTab;
