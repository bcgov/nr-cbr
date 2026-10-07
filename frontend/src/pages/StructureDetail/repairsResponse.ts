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
