import { Calendar, CheckmarkOutline, Inspection as InspectionIcon } from '@carbon/icons-react';
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
  Tag,
} from '@carbon/react';
import { useState } from 'react';

import ExternalLink from '@/components/core/ExternalLink';
import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import CommentsCard from './CommentsCard';
import { describe, number, yesNo } from './format';

import type { InspectionScheduleResponse, StructureInspection } from './inspectionsResponse';
import type { FC } from 'react';

import {
  useStructureInspectionSchedule,
  useStructureInspections,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';
import { inspectionStatusLabel, inspectionStatusTagType } from '@/utils/inspectionStatus';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

/** When the structure is next to be inspected, and how often — legacy's fields above its table. */
const ScheduleCard: FC<{ schedule: InspectionScheduleResponse }> = ({ schedule }) => (
  <Card title="Inspection Schedule" icon={Calendar} testId="structure-section-schedule">
    <div className="structure-detail__fields">
      <ReadOnlyField
        label="Close Proximity Inspection Required?"
        value={yesNo(schedule.closeProximityRequired)}
      />
      {/* Legacy shows these two only when a close proximity inspection is required. */}
      {schedule.closeProximityRequired && (
        <>
          <ReadOnlyField
            label="Close Proximity Special Equipment Requirements"
            value={describe(schedule.closeProximityEquipment)}
          />
          <ReadOnlyField
            label="Next Planned Close Proximity Inspection"
            value={formatShortDate(schedule.nextCloseProximityDate)}
          />
        </>
      )}
      <ReadOnlyField
        label="Next Planned Routine Inspection"
        value={formatShortDate(schedule.nextRoutineDate)}
      />
      <ReadOnlyField
        label="Routine Inspection Frequency (years)"
        value={number(schedule.routineFrequencyYears)}
      />
    </div>
  </Card>
);

/** The close proximity inspections already done. */
const CloseProximityCard: FC<{ schedule: InspectionScheduleResponse }> = ({ schedule }) => (
  <Card
    title="Completed Close Proximity Inspections"
    icon={CheckmarkOutline}
    testId="structure-section-close-proximity"
  >
    {schedule.completedCloseProximity.length === 0 ? (
      <p className="structure-detail__empty">No close proximity inspections have been recorded.</p>
    ) : (
      <Table size="md" useZebraStyles aria-label="Completed close proximity inspections">
        <TableHead>
          <TableRow>
            <TableHeader className="structure-detail__nowrap">Date</TableHeader>
            <TableHeader>IDIR ID</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {schedule.completedCloseProximity.map((done) => (
            <TableRow key={done.id}>
              <TableCell className="structure-detail__nowrap">
                {formatShortDate(done.completed)}
              </TableCell>
              <TableCell>{done.userId}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    )}
  </Card>
);

/** The page of inspections, or legacy's words when there are none. */
const InspectionsTable: FC<{ rows: StructureInspection[] }> = ({ rows }) => {
  if (rows.length === 0) {
    return (
      <p className="structure-detail__empty">There have been no inspections for this structure.</p>
    );
  }
  return (
    <Table size="md" useZebraStyles aria-label="Inspections">
      <TableHead>
        <TableRow>
          <TableHeader className="structure-detail__nowrap">Inspection Date</TableHeader>
          <TableHeader className="structure-detail__nowrap">Inspection Type</TableHeader>
          <TableHeader className="structure-detail__nowrap">Site #</TableHeader>
          <TableHeader className="structure-detail__nowrap">Inspection Report Status</TableHeader>
          <TableHeader className="structure-detail__nowrap">Reviewed Date</TableHeader>
          <TableHeader>Reviewed By</TableHeader>
          <TableHeader>Inspector Name</TableHeader>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((inspection) => {
          const date = formatShortDate(inspection.inspectionDate) || inspection.id;
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
  );
};

/**
 * The structure's inspections, newest first, a page at a time from the server. Those from before
 * the superstructure went in are left out unless asked for, as legacy's table leaves them.
 */
const InspectionsCard: FC<Props> = ({ structureId, opened }) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [includeBeforeInstall, setIncludeBeforeInstall] = useState(false);
  // Carbon's Pagination is one-based; the backend is zero-based.
  const loaded = useStructureInspections(
    structureId,
    opened,
    page - 1,
    pageSize,
    includeBeforeInstall,
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

  return (
    <Card title="Inspections" icon={InspectionIcon} testId="structure-section-inspections">
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
          <InspectionsTable rows={rows} />
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
 * legacy has them first, as the Details tab ends on its comments. Read-only for now;
 * legacy's Add Routine and Add Unplanned Inspection, and its comment and close proximity Adds, come
 * with the page's editing.
 */
const InspectionsTab: FC<Props> = ({ structureId, opened }) => {
  const schedule = useStructureInspectionSchedule(structureId, opened);

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-inspections-tab">
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
          <ScheduleCard schedule={schedule.data} />
          <CloseProximityCard schedule={schedule.data} />
        </>
      )}
      <InspectionsCard structureId={structureId} opened={opened} />
      {/* Last, as the Details tab ends on its comments. */}
      {schedule.data && (
        <CommentsCard
          comments={schedule.data.plannedInspectionComments}
          title="Planned Inspection Comments"
          testId="structure-section-planned-comments"
        />
      )}
    </div>
  );
};

export default InspectionsTab;
