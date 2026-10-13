import type { CodeValue } from './structureResponse';

/** One document's details; its bytes come from the file endpoint. */
export type StructureDocument = {
  id: string;
  /** The inspection it was attached to, or null for the structure's own. */
  inspectionId: string | null;
  inspectionDate: string | null;
  /** What the file shows or is — the link text. */
  attachmentType: CodeValue;
  /** When the photo was taken or the document made. */
  created: string | null;
  /** `EFILE_EXTENSION_CODE`, e.g. `JPG`. */
  extension: string | null;
  description: string | null;
  filename: string | null;
};

/**
 * A structure's documents and photos — `GET /v1/structures/{structureId}/documents`, the backend's
 * `StructureDocumentsResponse`. In legacy's order: the structure's own first, then each
 * inspection's, newest inspection first; within each, newest file first.
 */
export type DocumentsResponse = { documents: StructureDocument[] };

/** One file's details in an upload — the backend's `DocumentDetailsRequest`. */
export type DocumentDetailsRequest = {
  /** "Photo View" — `FILE_ATTACHMENT_TYPE_CODE`. */
  attachmentTypeCode: string;
  /** `yyyy-MM-dd`. */
  createdDate: string;
  description: string;
};

/** An edit to a document's details — the backend's `DocumentUpdateRequest`. */
export type DocumentUpdateRequest = { attachmentTypeCode: string; description: string };

/** The documents just added — the backend's `DocumentsCreatedResponse`. */
export type DocumentsCreatedResponse = { ids: string[] };
