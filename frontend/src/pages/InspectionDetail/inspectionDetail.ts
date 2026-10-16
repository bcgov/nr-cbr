import type { CodeValue } from '@/pages/StructureDetail/structureResponse';
import type { UserAudit } from '@/pages/StructureDetail/UserAudits';

/** One row of an inspection's status history: the status, who set it and when. */
export type StatusChange = {
  status: CodeValue;
  changedBy: string | null;
  /** ISO date-time. */
  changedAt: string | null;
};

/** The P.Eng's part: the review, the load rating recorded with it, and the P.Eng's comments. */
export type InspectionReview = {
  reviewedBy: string | null;
  /** ISO date. */
  reviewedDate: string | null;
  /** GVW tonnes. */
  loadRating: number | null;
  loadRatingReason: CodeValue;
  loadRatingReasonComment: string | null;
  pengComment: string | null;
};

/** The Hydraulic Hazards tab: metres, and each hazard. */
export type InspectionHazards = {
  presentWaterDepth: number | null;
  presentWaterWidth: number | null;
  highWaterClearance: number | null;
  highWater: boolean;
  debris: boolean;
  scour: boolean;
  ice: boolean;
  aggradation: boolean;
  comment: string | null;
};

/** One answered question, where it was asked: a span, a pier, a stringer. */
export type InspectionItem = {
  subsectionCode: string;
  questionCode: string;
  spanId: string | null;
  pierId: string | null;
  stringerNumber: number | null;
  answer: string | null;
  comment: string | null;
};

/** One inspection — the backend's `InspectionDetailResponse`. */
export type InspectionDetailResponse = {
  id: string;
  structureId: string | null;
  structureName: string | null;
  siteAtTimeOfInspection: string | null;
  type: CodeValue;
  status: CodeValue;
  /** Newest first. */
  statusHistory: StatusChange[];
  /** ISO date; none while In Progress, if not yet entered. */
  inspectionDate: string | null;
  inspectorName: string | null;
  inspectorCompanyName: string | null;
  cost: number | null;
  professionalInspectionRequired: boolean;
  inspectionComment: string | null;
  review: InspectionReview;
  hazards: InspectionHazards;
  items: InspectionItem[];
  entered: UserAudit | null;
  updated: UserAudit | null;
  /** Out on the offline client. */
  offline: boolean;
};
