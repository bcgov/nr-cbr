import {
  Forum,
  Inspection as InspectionIcon,
  Location,
  Report,
  UserCertification,
  WaveDirection,
} from '@carbon/icons-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow, Tag } from '@carbon/react';

import ExternalLink from '@/components/core/ExternalLink';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import Card from '@/pages/StructureDetail/Card';
import SiteFields from '@/pages/StructureDetail/SiteFields';

import type { InspectionDetailResponse } from './inspectionDetail';
import type { CodeValue, StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
import type { FC } from 'react';

import { describe, money, number, yesNo } from '@/pages/StructureDetail/format';
import { formatDateTime, formatShortDate } from '@/utils/date';
import { inspectionStatusLabel, inspectionStatusTagType } from '@/utils/inspectionStatus';

/** An inspection status as the app shows one: a coloured pill, or nothing when none is stored. */
export const StatusTag: FC<{ status: CodeValue; testId?: string }> = ({ status, testId }) =>
  status.code || status.description ? (
    <Tag type={inspectionStatusTagType(status.code)} size="sm" data-testid={testId}>
      {inspectionStatusLabel(status.code, status.description)}
    </Tag>
  ) : null;

/** Free text as written, its line breaks kept; "—" when there is none. */
const Text: FC<{ value: string | null | undefined; testId: string }> = ({ value, testId }) => (
  <p className="inspection-detail__text" data-testid={testId}>
    {value?.trim() ? value : '—'}
  </p>
);

/** Metres as stored, with the unit; nothing when none is stored. */
const metres = (value: number | null) => (value === null ? '' : `${number(value)} m`);

/**
 * The structure inspected and its site, as the structure page's Information tab shows them. The
 * site # is the one it sat at when inspected; the rest is the structure's site now.
 */
export const StructureCard: FC<{
  inspection: InspectionDetailResponse;
  structure: StructureDetailResponse | undefined;
}> = ({ inspection, structure }) => (
  <Card title="Structure and site" icon={Location} testId="inspection-section-structure">
    <div className="structure-detail__fields">
      <ReadOnlyField
        label="Structure #"
        value={
          inspection.structureId ? (
            <ExternalLink to={`/inventory/structure/${inspection.structureId}`}>
              {inspection.structureName ?? inspection.structureId}
            </ExternalLink>
          ) : null
        }
      />
      <ReadOnlyField label="Type/Class" value={structure ? describe(structure.typeClass) : null} />
      <ReadOnlyField
        label="Site # at Inspection"
        value={
          inspection.siteAtTimeOfInspection ? (
            <ExternalLink to={`/inventory/site/${inspection.siteAtTimeOfInspection}`}>
              {inspection.siteAtTimeOfInspection}
            </ExternalLink>
          ) : null
        }
      />
      <SiteFields site={structure?.site ?? null} />
    </div>
  </Card>
);

/** The Inspection tab's own cards: the inspection, then the inspector's comments. */
export const InspectionCards: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => (
  <>
    <Card title="Inspection" icon={InspectionIcon} testId="inspection-section-inspection">
      <div className="structure-detail__fields">
        <ReadOnlyField label="Inspection Type" value={describe(inspection.type)} />
        <ReadOnlyField
          label="Inspection Report Status"
          value={<StatusTag status={inspection.status} />}
        />
        <ReadOnlyField
          label="Date of Inspection"
          value={formatShortDate(inspection.inspectionDate)}
          emptyText="No date yet"
        />
        <ReadOnlyField label="Inspector Name" value={inspection.inspectorName} />
        <ReadOnlyField label="Inspection Company Name" value={inspection.inspectorCompanyName} />
        <ReadOnlyField label="Cost of Inspection" value={money(inspection.cost)} />
        <ReadOnlyField
          label="Professional Inspection Required?"
          value={yesNo(inspection.professionalInspectionRequired)}
        />
      </div>
    </Card>
    <Card title="Inspection Comments" icon={Forum} testId="inspection-section-comments">
      <Text value={inspection.inspectionComment} testId="inspection-comment" />
    </Card>
  </>
);

/** The Hydraulic Hazards tab: the channel's measurements, the hazards and their comments. */
export const HazardsCard: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => {
  const { hazards } = inspection;
  return (
    <Card title="Hydraulic Hazards" icon={WaveDirection} testId="inspection-section-hazards">
      <div className="structure-detail__fields">
        <ReadOnlyField
          label="Estimated Present Water Level Depth"
          value={metres(hazards.presentWaterDepth)}
        />
        <ReadOnlyField
          label="Estimated Present Water Level Width"
          value={metres(hazards.presentWaterWidth)}
        />
        <ReadOnlyField
          label="Estimated High Water Level Clearance"
          value={metres(hazards.highWaterClearance)}
        />
        <ReadOnlyField label="High Water?" value={yesNo(hazards.highWater)} />
        <ReadOnlyField label="Debris?" value={yesNo(hazards.debris)} />
        <ReadOnlyField label="Scour?" value={yesNo(hazards.scour)} />
        <ReadOnlyField label="Ice?" value={yesNo(hazards.ice)} />
        <ReadOnlyField label="Aggradation?" value={yesNo(hazards.aggradation)} />
      </div>
      <h3 className="inspection-detail__subheading">Comments</h3>
      <Text value={hazards.comment} testId="hazard-comment" />
    </Card>
  );
};

/**
 * The Review tab — legacy's Accept dialog and status history, read only: the status and who
 * reviewed it, every status it has had, the load rating recorded with the review, and the P.Eng's
 * comments.
 */
export const ReviewCards: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => {
  const { review } = inspection;
  return (
    <>
      <Card title="Status" icon={UserCertification} testId="inspection-section-status">
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="Inspection Report Status"
            value={<StatusTag status={inspection.status} />}
          />
          <ReadOnlyField label="Reviewed By" value={review.reviewedBy} />
          <ReadOnlyField label="Reviewed Date" value={formatShortDate(review.reviewedDate)} />
        </div>
        <h3 className="inspection-detail__subheading">Status History</h3>
        {inspection.statusHistory.length === 0 ? (
          <p className="structure-detail__empty">No status has been recorded.</p>
        ) : (
          <Table size="md" useZebraStyles aria-label="Status History">
            <TableHead>
              <TableRow>
                <TableHeader>Status</TableHeader>
                <TableHeader>Changed By</TableHeader>
                <TableHeader>Date</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {inspection.statusHistory.map((change, index) => (
                <TableRow
                  // Newest first; a status may recur, so its place keeps the row's key unique.
                  key={`${index}-${change.status.code}`}
                  data-testid={`status-history-${index}`}
                >
                  <TableCell>
                    <StatusTag status={change.status} />
                  </TableCell>
                  <TableCell>{change.changedBy}</TableCell>
                  <TableCell>{formatDateTime(change.changedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
      <Card title="Load Rating" icon={Report} testId="inspection-section-load-rating">
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="Load Rating of Inspection (GVW tonnes)"
            value={number(review.loadRating)}
          />
          <ReadOnlyField label="Load Rating Reason" value={describe(review.loadRatingReason)} />
          <ReadOnlyField
            label="Load Rating Reason Comment"
            value={review.loadRatingReasonComment}
          />
        </div>
      </Card>
      <Card title="PEng Comments" icon={Forum} testId="inspection-section-peng-comments">
        <Text value={review.pengComment} testId="inspection-peng-comment" />
      </Card>
    </>
  );
};
