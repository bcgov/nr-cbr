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

/** An edit to a monitoring item — the backend's `MonitorUpdateRequest`. The number is not edited. */
export type MonitorUpdateRequest = {
  statusCode: string;
  /** Blank for none. */
  frequencyCode: string;
  /** Kept only with Other (`OTH`), where it is required. */
  frequencyComment: string;
  description: string;
};

/** A new monitoring item — the backend's `MonitorCreateRequest`. Always Suggested; numbered next. */
export type MonitorCreateRequest = Omit<MonitorUpdateRequest, 'statusCode'>;

/** The item just added — the backend's `MonitorCreatedResponse`. */
export type MonitorCreatedResponse = { id: string; number: number };
