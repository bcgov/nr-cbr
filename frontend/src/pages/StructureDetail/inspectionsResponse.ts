import type { CodeValue, StructureComment } from './structureResponse';
import type { PagedResponse } from '@/types/api';

/** A close proximity inspection that was done, and who recorded it. */
export type CompletedCloseProximity = {
  id: string;
  completed: string | null;
  userId: string | null;
};

/**
 * The top of the Inspections tab — `GET /v1/structures/{structureId}/inspection-schedule`, the
 * backend's `StructureInspectionScheduleResponse`.
 */
export type InspectionScheduleResponse = {
  /** Newest first. */
  plannedInspectionComments: StructureComment[];
  closeProximityRequired: boolean;
  closeProximityEquipment: CodeValue;
  nextCloseProximityDate: string | null;
  nextRoutineDate: string | null;
  /** Years between routine inspections, 1 to 6. */
  routineFrequencyYears: number | null;
  /** Newest first. */
  completedCloseProximity: CompletedCloseProximity[];
  /**
   * The latest reviewed or accepted inspection's date, from which a new frequency sets the next
   * planned routine inspection; null when there is none.
   */
  latestReviewedInspectionDate: string | null;
};

/** A completed close proximity inspection to record — the backend's `CloseProximityInspectionRequest`. */
export type CloseProximityInspectionRequest = {
  /** `yyyy-MM-dd`; required. */
  completedDate: string;
};

/**
 * An edit to the schedule — the backend's `InspectionScheduleRequest`. A Level 1 user's close
 * proximity and next routine date are ignored: the server keeps them, and moves the date itself
 * when the frequency changes.
 */
export type InspectionScheduleRequest = {
  closeProximityRequired: boolean;
  /** Blank for none; kept as stored while no close proximity inspection is required. */
  closeProximityEquipmentCode: string;
  /** `yyyy-MM-dd`; kept as stored while none is required. */
  nextCloseProximityDate: string | null;
  /** `yyyy-MM-dd`; required once the structure has one. */
  nextRoutineDate: string | null;
  /** 1 to 6. */
  routineFrequencyYears: number | null;
};

/** One row of the inspection table. */
export type StructureInspection = {
  id: string;
  type: CodeValue;
  inspectionDate: string | null;
  /** The site the structure stood on when inspected. */
  siteId: string | null;
  status: CodeValue;
  reviewedDate: string | null;
  reviewedBy: string | null;
  inspectorName: string | null;
  /** False while the inspection is out on the offline client, when it has no link. */
  viewable: boolean;
};

/**
 * A page of the inspection table — `GET /v1/structures/{structureId}/inspections`, the backend's
 * `StructureInspectionsResponse`.
 */
export type InspectionsResponse = {
  page: PagedResponse<StructureInspection>;
  /** Inspections from before the superstructure went in, left out unless asked for. */
  beforeInstallCount: number;
};
