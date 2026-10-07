import { Download, Folder, Launch } from '@carbon/icons-react';
import {
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

import Card from './Card';
import { opensInTab, openDocument } from './openDocument';

import type { StructureDocument } from './documentsResponse';
import type { FC } from 'react';

import { useStructureDocuments } from '@/hooks/useStructureSearch';
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
};

/**
 * One card's files, a page at a time. Paged here rather than by the server: a structure has tens of
 * files, not thousands, and the list arrives whole.
 */
const DocumentsTable: FC<TableProps> = ({ title, documents, opening, onOpen }) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Back to the last page there is when the list shrinks under it — when early files are hidden.
  const lastPage = Math.max(1, Math.ceil(documents.length / pageSize));
  const current = Math.min(page, lastPage);
  const rows = documents.slice((current - 1) * pageSize, current * pageSize);

  return (
    <>
      <Table size="md" useZebraStyles aria-label={title}>
        <TableHead>
          <TableRow>
            <TableHeader className="structure-detail__nowrap">Available Documents</TableHeader>
            <TableHeader className="structure-detail__nowrap">Photo/Doc Created</TableHeader>
            <TableHeader className="structure-detail__nowrap">Type</TableHeader>
            <TableHeader className="structure-detail__nowrap">Obtained From</TableHeader>
            <TableHeader>Description</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((document) => {
            const inTab = opensInTab(document);
            const Icon = inTab ? Launch : Download;
            return (
              <TableRow key={document.id} data-testid={`structure-document-${document.id}`}>
                <TableCell className="structure-detail__nowrap">
                  <button
                    type="button"
                    className="structure-detail__file-link"
                    disabled={opening === document.id}
                    aria-busy={opening === document.id}
                    onClick={() => onOpen(document)}
                  >
                    {document.attachmentType.description ||
                      document.attachmentType.code ||
                      document.filename}
                    <Icon size={16} aria-hidden="true" />
                    <span className="cds--visually-hidden">
                      {inTab ? ' (opens in a new tab)' : ' (downloads)'}
                    </span>
                  </button>
                </TableCell>
                <TableCell className="structure-detail__nowrap">
                  {formatShortDate(document.created)}
                </TableCell>
                <TableCell className="structure-detail__nowrap">{document.extension}</TableCell>
                <TableCell className="structure-detail__nowrap">
                  {document.inspectionId === null ? 'Structure' : 'Inspection'}
                </TableCell>
                <TableCell className="structure-detail__comment">{document.description}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
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
 * inspection's, a card for each of legacy's folders. The attachment type opens the file — an image
 * or a PDF in a new tab, anything else as a download. Read-only for now; legacy's Add (Level 2) and
 * Delete come with the page's editing.
 */
const DocumentsTab: FC<Props> = ({ structureId, opened, yearBuilt }) => {
  const loaded = useStructureDocuments(structureId, opened);
  const [showEarly, setShowEarly] = useState(false);
  /** The file being fetched, so its link can say so and not be pressed twice. */
  const [opening, setOpening] = useState<string | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);

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
        <Card title="Documents & Photos" icon={Folder} testId="structure-documents-empty">
          <p className="structure-detail__empty">No photos and/or documents have been loaded.</p>
        </Card>
      ) : (
        groups.map((group) => (
          <Card
            key={group.key}
            title={group.title}
            icon={Folder}
            testId={`structure-documents-${group.key}`}
          >
            <DocumentsTable
              title={group.title}
              documents={group.documents}
              opening={opening}
              onOpen={(document) => void open(document)}
            />
          </Card>
        ))
      )}
    </div>
  );
};

export default DocumentsTab;
