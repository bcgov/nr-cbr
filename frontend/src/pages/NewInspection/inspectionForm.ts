import type { CodeOption } from '@/types/configuration';

/** Whom a group's questions are asked of — the backend's `InspectionFormResponse.Scope`. */
export type QuestionScope = 'STRUCTURE' | 'SPAN' | 'PIER';

/** One inspection question. `answers` is empty when the answer is typed. */
export type InspectionQuestion = {
  code: string;
  description: string;
  /** Asked of each stringer — Stringers' and the wood box culvert's stringer questions. */
  perStringer: boolean;
  answers: CodeOption[];
};

/** A group of questions — legacy's subsection: Approaches, Deck, Piers, … */
export type InspectionSubsection = {
  code: string;
  description: string;
  scope: QuestionScope;
  questions: InspectionQuestion[];
};

/** A bridge span or pier: its id, and the number the user knows it by. */
export type BridgePart = { id: string; number: number | null };

/** `GET /v1/inspections/form?structureId=` — the questions an inspection of the structure asks. */
export type InspectionFormResponse = {
  structureId: string;
  subsections: InspectionSubsection[];
  spans: BridgePart[];
  piers: BridgePart[];
};

/** One answered question — the backend's `InspectionCreateRequest.Item`. */
export type InspectionItemRequest = {
  subsectionCode: string;
  questionCode: string;
  spanId: number | null;
  pierId: number | null;
  stringerNumber: number | null;
  answer: string;
  comment: string;
};

/** A new inspection, saved In Progress or Submitted — the backend's `InspectionCreateRequest`. */
export type InspectionCreateRequest = {
  structureId: number | null;
  typeCode: string;
  /** PRO In Progress or SUB Submitted. */
  statusCode: string;
  /** `yyyy-MM-dd`. */
  inspectionDate: string | null;
  inspectorName: string;
  inspectorCompanyName: string;
  cost: number | null;
  professionalInspectionRequired: boolean;
  inspectionComment: string;
  presentWaterDepth: number | null;
  presentWaterWidth: number | null;
  highWaterClearance: number | null;
  highWater: boolean;
  debris: boolean;
  scour: boolean;
  ice: boolean;
  aggradation: boolean;
  hazardComment: string;
  items: InspectionItemRequest[];
};
