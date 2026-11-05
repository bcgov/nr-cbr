import { Add, CheckmarkOutline, Inspection as InspectionIcon } from '@carbon/icons-react';
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
  TableRow,
  Tag,
} from '@carbon/react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import ExternalLink from '@/components/core/ExternalLink';
import SortableHeader, { ClearSort } from '@/components/core/SortableHeader';

import Card from './Card';
import OutstandingPanel from './OutstandingPanel';
import CloseProximityDialog from './CloseProximityDialog';
import CommentDialog from './CommentDialog';
import CommentsCard from './CommentsCard';
import { describe } from './format';
import ScheduleCard from './ScheduleCard';

import type {
  CompletedCloseProximity,
  InspectionScheduleResponse,
  StructureInspection,
  StructureInspectionSort,
  StructureInspectionSortColumn,
} from './inspectionsResponse';
import type { StructureComment } from './structureResponse';
import type { OutstandingGroup } from './format';
import type { SortOrder } from '@/utils/headerSort';
import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useStructureInspectionSchedule,
  useStructureInspections,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { sortRows } from '@/utils/headerSort';
import { inspectionStatusLabel, inspectionStatusTagType } from '@/utils/inspectionStatus';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
  /** True when the structure has nothing outstanding — legacy's `isComplete()`. */
  complete: boolean;
  /** What is missing in this tab, by section. */
  outstanding: OutstandingGroup[];
  /** `error` once adding an inspection has been refused for what is outstanding. */
  outstandingTone?: 'neutral' | 'error';
  /** Add Routine / Unplanned pressed while the structure is incomplete. */
  onAddRefused: () => void;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

/** The close proximity table's columns, each sorting by its header. */
type CloseProximityColumn = 'COMPLETED' | 'USER_ID';
type CloseProximitySort = SortOrder<CloseProximityColumn>;

const closeProximityValue = (done: CompletedCloseProximity, column: CloseProximityColumn) =>
  column === 'COMPLETED' ? done.completed : done.userId;

/**
 * The close proximity inspections already done, with "Add close proximity inspection" in the header
 * for a P.Eng — legacy's `/pEngAccess` on its add. Rows are neither edited nor deleted, as legacy's.
 */
const CloseProximityCard: FC<{
  schedule: InspectionScheduleResponse;
  /** Offered to a P.Eng only. */
  onAdd?: () => void;
  /** Held while another card on the tab is being edited. */
  addDisabled: boolean;
}> = ({ schedule, onAdd, addDisabled }) => {
  // In the browser: every completed inspection is here, so nothing is paged away from the sort.
  const [sort, setSort] = useState<CloseProximitySort>([]);
  const rows = sortRows(schedule.completedCloseProximity, sort, closeProximityValue);
  const header = (column: CloseProximityColumn, label: string, className?: string) => (
    <SortableHeader
      className={className}
      column={column}
      sort={sort}
      onSortChange={setSort}
      data-testid={`close-proximity-sort-${column}`}
    >
      {label}
    </SortableHeader>
  );
  return (
    <Card
      title="Completed Close Proximity Inspections"
      icon={CheckmarkOutline}
      testId="structure-section-close-proximity"
      action={
        onAdd && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Add}
            disabled={addDisabled}
            data-testid="close-proximity-add"
            onClick={onAdd}
          >
            Add close proximity inspection
          </Button>
        )
      }
    >
      {schedule.completedCloseProximity.length === 0 ? (
        <p className="structure-detail__empty">
          No close proximity inspections have been recorded.
        </p>
      ) : (
        <>
          <ClearSort sort={sort} onSortChange={setSort} data-testid="close-proximity-clear-sort" />
          <Table size="md" useZebraStyles aria-label="Completed close proximity inspections">
            <TableHead>
              <TableRow>
                {header('COMPLETED', 'Date', 'structure-detail__nowrap')}
                {header('USER_ID', 'IDIR ID')}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((done) => (
                <TableRow key={done.id} data-testid={`close-proximity-${done.id}`}>
                  <TableCell className="structure-detail__nowrap">
                    {formatShortDate(done.completed)}
                  </TableCell>
                  <TableCell>{done.userId}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </>
      )}
    </Card>
  );
};

/** Every column, in legacy's order — each sorts by its header. */
const INSPECTION_COLUMNS: {
  column: StructureInspectionSortColumn;
  label: string;
  nowrap?: boolean;
}[] = [
  { column: 'INSPECTION_DATE', label: 'Inspection Date', nowrap: true },
  { column: 'TYPE', label: 'Inspection Type', nowrap: true },
  { column: 'SITE_ID', label: 'Site #', nowrap: true },
  { column: 'STATUS', label: 'Inspection Report Status', nowrap: true },
  { column: 'REVIEWED_DATE', label: 'Reviewed Date', nowrap: true },
  { column: 'REVIEWED_BY', label: 'Reviewed By' },
  { column: 'INSPECTOR_NAME', label: 'Inspector Name' },
];

/** The page of inspections, or legacy's words when there are none. */
const InspectionsTable: FC<{
  rows: StructureInspection[];
  /** The header the inspections are sorted by, or `null` for newest first. */
  sort: StructureInspectionSort;
  onSortChange: (next: StructureInspectionSort) => void;
}> = ({ rows, sort, onSortChange }) => {
  if (rows.length === 0) {
    return (
      <p className="structure-detail__empty">There have been no inspections for this structure.</p>
    );
  }
  return (
    <>
      <ClearSort
        sort={sort}
        onSortChange={onSortChange}
        data-testid="structure-inspections-clear-sort"
      />
      <Table size="md" useZebraStyles aria-label="Inspections">
        <TableHead>
          <TableRow>
            {/* On the server, not the page: the inspections are paged there, so sorting only the
                rows on screen would order ten of them and call it sorted. */}
            {INSPECTION_COLUMNS.map(({ column, label, nowrap }) => (
              <SortableHeader
                key={column}
                className={nowrap ? 'structure-detail__nowrap' : undefined}
                column={column}
                sort={sort}
                onSortChange={onSortChange}
                data-testid={`structure-inspections-sort-${column}`}
              >
                {label}
              </SortableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((inspection) => {
            // An In Progress inspection may wait for its date; it is listed first, and says so.
            const date = formatShortDate(inspection.inspectionDate) || 'No date yet';
            return (
              <TableRow key={inspection.id} data-testid={`structure-inspection-${inspection.id}`}>
                <TableCell className="structure-detail__nowrap">
                  {/* Its own tab, as the site and replaced structures open; none while the
                              inspection is out on the offline client, as legacy offers none. */}
                  {inspection.viewable ? (
                    <ExternalLink to={`/inspection/${inspection.id}`}>{date}</ExternalLink>
                  ) : (
                    date
                  )}
                </TableCell>
                <TableCell className="structure-detail__nowrap">
                  {describe(inspection.type)}
                </TableCell>
                <TableCell className="structure-detail__nowrap">
                  {inspection.siteId && (
                    <ExternalLink to={`/inventory/site/${inspection.siteId}`}>
                      {inspection.siteId}
                    </ExternalLink>
                  )}
                </TableCell>
                <TableCell className="structure-detail__nowrap">
                  {(inspection.status.code || inspection.status.description) && (
                    <Tag type={inspectionStatusTagType(inspection.status.code)} size="sm">
                      {inspectionStatusLabel(inspection.status.code, inspection.status.description)}
                    </Tag>
                  )}
                </TableCell>
                <TableCell className="structure-detail__nowrap">
                  {formatShortDate(inspection.reviewedDate)}
                </TableCell>
                <TableCell>{inspection.reviewedBy}</TableCell>
                <TableCell>{inspection.inspectorName}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </>
  );
};

/**
 * The structure's inspections, newest first, a page at a time from the server. Those from before
 * the superstructure went in are left out unless asked for, as legacy's table leaves them.
 */
/**
 * The structure's inspections, with legacy's Add Routine Inspection (edit capability) and Add
 * Unplanned Inspection (inspection-write) in the header — both Level 1 and up today. As legacy, both wait until the structure is complete — pressed
 * before then, the page's outstanding items turn red; once complete, they open Add Inspection.
 */
const InspectionsCard: FC<{
  structureId: string;
  opened: boolean;
  complete: boolean;
  /** Held while another card on the tab is being edited. */
  addDisabled: boolean;
  onAddRefused: () => void;
}> = ({ structureId, opened, complete, addDisabled, onAddRefused }) => {
  const { canEdit, canWriteInspection } = useAuthorization();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [includeBeforeInstall, setIncludeBeforeInstall] = useState(false);
  /** The header sorted by — none at first, so newest first. */
  const [sort, setSort] = useState<StructureInspectionSort>([]);
  // Carbon's Pagination is one-based; the backend is zero-based.
  const loaded = useStructureInspections(
    structureId,
    opened,
    page - 1,
    pageSize,
    includeBeforeInstall,
    sort,
  );

  if (loaded.isError) {
    return (
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Inspections could not be loaded"
        subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
        data-testid="structure-inspections-error"
      />
    );
  }

  const response = loaded.data;
  const rows = response?.page.content ?? [];
  const hidden = response?.beforeInstallCount ?? 0;

  // As legacy, a new inspection waits until the structure is complete. The buttons stay live, as
  // nr-frep's submit does: pressed too early, the page's outstanding banner, counts and lists turn
  // red and say why, where a disabled button would say nothing.
  const addInspection = (type: 'ROUT' | 'UNP') => {
    if (!complete) {
      onAddRefused();
      return;
    }
    void navigate(`/inspection/new?structureId=${encodeURIComponent(structureId)}&type=${type}`);
  };
  const actions = (canEdit || canWriteInspection) && (
    <div className="structure-detail__card-actions">
      {canEdit && (
        <Button
          kind="tertiary"
          size="sm"
          renderIcon={Add}
          disabled={addDisabled}
          data-testid="inspection-add-routine"
          onClick={() => addInspection('ROUT')}
        >
          Add routine inspection
        </Button>
      )}
      <Button
        kind="tertiary"
        size="sm"
        renderIcon={Add}
        disabled={addDisabled}
        data-testid="inspection-add-unplanned"
        onClick={() => addInspection('UNP')}
      >
        Add unplanned inspection
      </Button>
    </div>
  );

  return (
    <Card
      title="Inspections"
      icon={InspectionIcon}
      testId="structure-section-inspections"
      action={actions}
    >
      {/* Why the buttons wait, where a disabled button alone would not say. */}
      {actions && !complete && (
        <p className="structure-detail__card-note" data-testid="inspection-add-note">
          A new inspection can be added once the structure has nothing outstanding — see the Details
          tab.
        </p>
      )}
      {(hidden > 0 || includeBeforeInstall) && (
        <div className="structure-detail__history-toggle">
          <Checkbox
            id="structure-show-early-inspections"
            labelText={`Show inspections from before the superstructure was installed (${hidden})`}
            checked={includeBeforeInstall}
            onChange={(_, { checked }) => {
              setIncludeBeforeInstall(checked);
              setPage(1);
            }}
          />
        </div>
      )}
      {!response ? (
        <div data-testid="structure-inspections-loading">
          <SkeletonText paragraph lineCount={4} />
        </div>
      ) : (
        <>
          <InspectionsTable
            rows={rows}
            sort={sort}
            onSortChange={(next) => {
              setSort(next);
              setPage(1);
            }}
          />
          <Pagination
            page={page}
            pageSize={pageSize}
            pageSizes={PAGE_SIZES}
            totalItems={response.page.totalElements}
            onChange={({ page: nextPage, pageSize: nextPageSize }) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            }}
          />
        </>
      )}
    </Card>
  );
};

/**
 * Legacy's Inspections tab (`inspectionTab.jsp`): the schedule, the completed close proximity
 * inspections, the inspections themselves, then the planned-inspection comments — last here, where
 * legacy has them first, as the Details tab ends on its comments. Each card carries its own edit:
 * the schedule's Edit, the completed close proximity inspections' Add, and
 * the planned-inspection comments' Add and Edit, and the inspections' Add Routine and Add Unplanned
 * Inspection, which open Add Inspection for this structure and type.
 */
const InspectionsTab: FC<Props> = ({
  structureId,
  opened,
  complete,
  outstanding,
  outstandingTone,
  onAddRefused,
}) => {
  const schedule = useStructureInspectionSchedule(structureId, opened);
  /** The card being edited, if any — one at a time. */
  const [editing, setEditing] = useState<'schedule' | null>(null);
  /** The planned-inspection comment in the dialog: one being edited, `'new'` to add, or null. */
  const [comment, setComment] = useState<StructureComment | 'new' | null>(null);
  // Add is Level 2 (legacy's /level2Access on its add box); edit is anyone who can save (Level 1).
  const { canEdit, canDelete: canAddComment, isPeng } = useAuthorization();
  /** True while the close proximity dialog is open. */
  const [addingCloseProximity, setAddingCloseProximity] = useState(false);

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-inspections-tab">
      <OutstandingPanel
        groups={outstanding}
        tone={outstandingTone}
        blocking="adding an inspection"
      />
      {schedule.isError && (
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="The inspection schedule could not be loaded"
          subtitle={apiErrorMessage(schedule.error, 'Try again in a moment.')}
          data-testid="structure-schedule-error"
        />
      )}
      {!schedule.data && !schedule.isError && (
        <div data-testid="structure-schedule-loading">
          <SkeletonText paragraph lineCount={6} />
        </div>
      )}
      {schedule.data && (
        <>
          <ScheduleCard
            structureId={structureId}
            schedule={schedule.data}
            editing={editing === 'schedule'}
            otherEditing={editing !== null && editing !== 'schedule'}
            onEdit={() => setEditing('schedule')}
            onDone={() => setEditing(null)}
          />
          <CloseProximityCard
            schedule={schedule.data}
            onAdd={isPeng ? () => setAddingCloseProximity(true) : undefined}
            addDisabled={editing !== null}
          />
        </>
      )}
      <InspectionsCard
        structureId={structureId}
        opened={opened}
        complete={complete}
        addDisabled={editing !== null}
        onAddRefused={onAddRefused}
      />
      {/* Last, as the Details tab ends on its comments. */}
      {schedule.data && (
        <CommentsCard
          comments={schedule.data.plannedInspectionComments}
          title="Planned Inspection Comments"
          testId="structure-section-planned-comments"
          onAdd={canAddComment ? () => setComment('new') : undefined}
          onEdit={canEdit ? setComment : undefined}
          addDisabled={editing !== null}
        />
      )}
      {addingCloseProximity && (
        <CloseProximityDialog
          structureId={structureId}
          onClose={() => setAddingCloseProximity(false)}
        />
      )}
      {comment !== null && (
        <CommentDialog
          key={comment === 'new' ? 'new' : comment.id}
          structureId={structureId}
          comment={comment === 'new' ? null : comment}
          onClose={() => setComment(null)}
        />
      )}
    </div>
  );
};

export default InspectionsTab;
