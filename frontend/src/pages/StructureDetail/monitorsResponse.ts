import type { CodeValue } from './structureResponse';
import type { Audited, ItemView } from './UserAudits';

/** Which items the Monitoring tab lists — legacy's "Choose Viewing Option". */
export type MonitorView = ItemView;

/**
 * One monitoring item — a row of `GET /v1/structures/{structureId}/monitors`, the backend's
 * `StructureMonitorsResponse.Monitor`, which arrives a page at a time.
 */
export type StructureMonitor = Audited & {
  id: string;
  number: number | null;
  status: CodeValue;
  inspectionId: string | null;
  inspectionDate: string | null;
  description: string | null;
  frequency: CodeValue;
  /** Free text alongside the frequency. */
  frequencyComment: string | null;
};
