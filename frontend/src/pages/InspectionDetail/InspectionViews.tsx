import {
  Forum,
  Inspection as InspectionIcon,
  Location,
  Report,
  UserCertification,
  WaveDirection,
} from '@carbon/icons-react';
import {
  Button,
  Modal,
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
import Card from '@/pages/StructureDetail/Card';
import SiteFields from '@/pages/StructureDetail/SiteFields';

import type { InspectionDetailResponse } from './inspectionDetail';
import type { CodeValue, StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
import type { FC, ReactNode } from 'react';

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

type CardProps = {
  inspection: InspectionDetailResponse;
  /** The card's Edit, when the user may change it. */
  action?: ReactNode;
  /** The card's form while it is being edited, in place of its values. */
  children?: ReactNode;
};

/** Every status the inspection has had, newest first — legacy's View History dialog. */
const StatusHistoryDialog: FC<{ inspection: InspectionDetailResponse; onClose: () => void }> = ({
  inspection,
  onClose,
}) => (
  <Modal
    open
    passiveModal
    size="sm"
    modalHeading="Status history"
    onRequestClose={onClose}
    data-testid="status-history-dialog"
  >
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
  </Modal>
);

/** The status, and legacy's View History beside it. */
const StatusWithHistory: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => {
  const [open, setOpen] = useState(false);
  return (
    <span className="inspection-detail__status">
      <StatusTag status={inspection.status} />
      <Button
        kind="ghost"
        size="sm"
        data-testid="status-history-open"
        onClick={() => setOpen(true)}
      >
        View history
      </Button>
      {open && <StatusHistoryDialog inspection={inspection} onClose={() => setOpen(false)} />}
    </span>
  );
};

/**
 * The Inspection card: type, status — with who reviewed it and its history — date, who inspected
 * and at what cost.
 */
export const InspectionCard: FC<CardProps> = ({ inspection, action, children }) => (
  <Card
    title="Inspection"
    icon={InspectionIcon}
    testId="inspection-section-inspection"
    action={action}
  >
    {children ?? (
      <div className="structure-detail__fields">
        <ReadOnlyField label="Inspection Type" value={describe(inspection.type)} />
        <ReadOnlyField
          label="Inspection Report Status"
          value={<StatusWithHistory inspection={inspection} />}
        />
        <ReadOnlyField label="Reviewed By" value={inspection.review.reviewedBy} />
        <ReadOnlyField
          label="Reviewed Date"
          value={formatShortDate(inspection.review.reviewedDate)}
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
    )}
  </Card>
);

/** The inspector's comments. */
export const InspectionCommentsCard: FC<CardProps> = ({ inspection, action, children }) => (
  <Card
    title="Inspection Comments"
    icon={Forum}
    testId="inspection-section-comments"
    action={action}
  >
    {children ?? <Text value={inspection.inspectionComment} testId="inspection-comment" />}
  </Card>
);

/** The Hydraulic Hazards tab: the channel's measurements, the hazards and their comments. */
export const HazardsCard: FC<{
  inspection: InspectionDetailResponse;
  /** The card's Edit, when the user may change the hazards. */
  action?: ReactNode;
}> = ({ inspection, action }) => {
  const { hazards } = inspection;
  return (
    <Card
      title="Hydraulic Hazards"
      icon={WaveDirection}
      testId="inspection-section-hazards"
      action={action}
    >
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

/** The load rating the P.Eng's review recorded, and its reason. */
export const LoadRatingCard: FC<{ inspection: InspectionDetailResponse }> = ({ inspection }) => {
  const { review } = inspection;
  return (
    <Card title="Load Rating" icon={Report} testId="inspection-section-load-rating">
      <div className="structure-detail__fields">
        <ReadOnlyField
          label="Load Rating of Inspection (GVW tonnes)"
          value={number(review.loadRating)}
        />
        <ReadOnlyField label="Load Rating Reason" value={describe(review.loadRatingReason)} />
        <ReadOnlyField label="Load Rating Reason Comment" value={review.loadRatingReasonComment} />
      </div>
    </Card>
  );
};

/** The P.Eng's comments. */
export const PengCommentsCard: FC<CardProps> = ({ inspection, action, children }) => (
  <Card
    title="PEng Comments"
    icon={UserCertification}
    testId="inspection-section-peng-comments"
    action={action}
  >
    {children ?? <Text value={inspection.review.pengComment} testId="inspection-peng-comment" />}
  </Card>
);
