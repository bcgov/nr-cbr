import type { CodeValue } from './structureResponse';
import type { Audited, ItemView } from './UserAudits';

/** Which repairs the Repairs tab lists — legacy's "Choose Viewing Option". */
export type RepairView = ItemView;

/**
 * One repair — a row of `GET /v1/structures/{structureId}/repairs`, the backend's
 * `StructureRepairsResponse.Repair`, which arrives a page at a time.
 */
export type StructureRepair = Audited & {
  id: string;
  number: number | null;
  status: CodeValue;
  type: CodeValue;
  inspectionId: string | null;
  inspectionDate: string | null;
  priority: CodeValue;
  completedDate: string | null;
  /** Whole dollars. */
  estimate: number | null;
  /** Whole dollars. */
  actualCost: number | null;
  quantity: number | null;
  /** What the quantity is counted in, from the repair type. */
  unit: string | null;
  description: string | null;
};

/**
 * An edit to a repair — the backend's `RepairUpdateRequest`. The number is not edited, nor the
 * carried-forward flag. A blank amount or quantity is null.
 */
export type RepairUpdateRequest = {
  statusCode: string;
  priorityCode: string;
  /** `yyyy-MM-dd`; kept only when the status is Completed (`COM`), where it is required. */
  completedDate: string | null;
  estimate: number | null;
  /** Kept only when the status is Completed. */
  actualCost: number | null;
  typeCode: string;
  quantity: number | null;
  description: string;
};

/**
 * One entry of the Repair Type list for one group it falls in — a row of
 * `GET /v1/structures/{structureId}/repair-types`, the backend's `RepairTypeOption`.
 */
export type RepairTypeOption = {
  code: string;
  description: string | null;
  /** What its quantity is counted in. */
  unit: string | null;
  groupCode: string;
};

/** A new repair — the backend's `RepairCreateRequest`. Always Suggested; numbered next. */
export type RepairCreateRequest = Pick<
  RepairUpdateRequest,
  'priorityCode' | 'estimate' | 'typeCode' | 'quantity' | 'description'
>;

/** The repair just added — the backend's `RepairCreatedResponse`. */
export type RepairCreatedResponse = { id: string; number: number };
