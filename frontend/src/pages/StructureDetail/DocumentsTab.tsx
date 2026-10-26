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
  /**
   * On an inspection's page: the inspection. Add then sits on its folder and files go to it;
   * there is no Edit, as legacy's inspection tab has none.
   */
  inspection?: { id: string; date: string | null };
  /** On an inspection's page: Reviewed or Accepted, so no Add — Delete stays, as legacy. */
  addLocked?: boolean;
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
            : `Inspection: ${formatShortDate(document.inspectionDate) || 'No date yet'}`,
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
                    {onEdit && !document.auditRecord && (
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
                    {/* An audit record is kept as made: View alone, as the server insists.
                        danger--ghost, as every Delete in the app: red at rest and on hover. */}
                    {onDelete && !document.auditRecord && (
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
 *
 * <p>On an inspection's page, legacy's inspection tab: Add (Level 1 up) on the inspection's folder,
 * shown first, its files the inspection's; Delete (Level 2 up) on any file, at any status; no Edit. Add goes
 * once the inspection is Reviewed or Accepted, as legacy disables its button.
 *
 * <p>An inspection's audit records — the report PDF stored on each save — are View alone, and only
 * the latest of each inspection is shown until "Show earlier audit records" is ticked.
 */
/**
 * Each inspection's latest audit record, by id: a save makes one, and the ids rise with each, where
 * the created date — a day — would tie. The rest are shown only when asked; none is ever removed.
 */
const latestAuditRecords = (documents: StructureDocument[]): Set<string> => {
  const latest = new Map<string, StructureDocument>();
  for (const document of documents) {
    if (!document.auditRecord) continue;
    const key = document.inspectionId ?? '';
    const current = latest.get(key);
    if (!current || Number(document.id) > Number(current.id)) latest.set(key, document);
  }
  return new Set([...latest.values()].map((document) => document.id));
};

const DocumentsTab: FC<Props> = ({
  structureId,
  opened,
  yearBuilt,
  inspection,
  addLocked = false,
}) => {
  const loaded = useStructureDocuments(structureId, opened);
  const [showEarly, setShowEarly] = useState(false);
  const [showEarlierAudits, setShowEarlierAudits] = useState(false);
  /** The file being fetched, so its link can say so and not be pressed twice. */
  const [opening, setOpening] = useState<string | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);
  const authorization = useAuthorization();
  // On an inspection's page, legacy's: Add from Level 1 (its uploader), Delete Level 2, no Edit.
  // On the structure page: Add and Delete Level 2, Edit Level 1.
  const canAdd =
    !addLocked && (inspection === undefined ? authorization.canDelete : authorization.canEdit);
  const canEdit = inspection === undefined && authorization.canEdit;
  const canDelete = authorization.canDelete;
  const { display } = useNotification();
  const deleteDocument = useDeleteDocument(structureId, inspection?.id);
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
  const afterInstall = showEarly
    ? all
    : all.filter((document) => !beforeInstall(document, yearBuilt));
  const latest = latestAuditRecords(afterInstall);
  const earlierAudits = afterInstall.filter(
    (document) => document.auditRecord && !latest.has(document.id),
  ).length;
  const shown = showEarlierAudits
    ? afterInstall
    : afterInstall.filter((document) => !document.auditRecord || latest.has(document.id));
  const groups = groupDocuments(shown);
  // Add belongs to the folder files are added to — the structure's own, or on an inspection's page
  // the inspection's — so that card is there to hold it even before it has a file.
  const addKey = inspection?.id ?? 'structure';
  if (canAdd && groups.length > 0 && !groups.some((group) => group.key === addKey)) {
    groups.unshift(
      inspection
        ? {
            key: inspection.id,
            title: `Inspection: ${formatShortDate(inspection.date) || 'No date yet'}`,
            documents: [],
          }
        : { key: 'structure', title: 'Structure Documents & Photos', documents: [] },
    );
  }
  // On an inspection's page, its own folder first — the one its files go into — then legacy's
  // order: the structure's, then each other inspection's.
  if (inspection) {
    const own = groups.findIndex((group) => group.key === inspection.id);
    if (own > 0) groups.unshift(...groups.splice(own, 1));
  }
  const addButton = canAdd && (
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
      {/* Why there is no Add, where its absence alone would not say. */}
      {addLocked && authorization.canEdit && (
        <p className="structure-detail__card-note" data-testid="structure-documents-locked">
          This inspection has been reviewed: documents and photos can no longer be added to it.
        </p>
      )}
      {hidden > 0 && (
        <Checkbox
          id="structure-show-early-documents"
          labelText={`Show documents from before the superstructure was installed (${hidden})`}
          checked={showEarly}
          onChange={(_, { checked }) => setShowEarly(checked)}
        />
      )}
      {earlierAudits > 0 && (
        <Checkbox
          id="structure-show-earlier-audit-records"
          labelText={`Show earlier audit records (${earlierAudits})`}
          checked={showEarlierAudits}
          onChange={(_, { checked }) => setShowEarlierAudits(checked)}
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
            action={group.key === addKey ? addButton : undefined}
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
        <UploadDocumentsDialog
          structureId={structureId}
          inspectionId={inspection?.id}
          onClose={() => setUploading(false)}
        />
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
