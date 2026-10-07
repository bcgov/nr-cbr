import type { CodeValue } from './structureResponse';

/** One span: numbered left bank to right bank, looking downstream. */
export type BridgeSpan = {
  id: string;
  number: number | null;
  /** Centre of bearing to centre of bearing. */
  lengthMetres: number | null;
};

/** One pier and its type. */
export type BridgePier = {
  id: string;
  number: number | null;
  type: CodeValue;
};

/**
 * A bridge's spans and piers — `GET /v1/structures/{structureId}/spans-and-piers`, the backend's
 * `StructureSpansAndPiersResponse`. Both lists are empty for a structure with no bridge row.
 */
export type SpansAndPiersResponse = {
  /** By span number. */
  spans: BridgeSpan[];
  /** By pier number. */
  piers: BridgePier[];
};
