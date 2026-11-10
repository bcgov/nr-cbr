import type { InspectionDetailResponse, InspectionUpdateRequest } from './inspectionDetail';
import type { InspectionValues } from '@/pages/NewInspection/inspectionRules';

import { inspectionErrorsOf } from '@/pages/NewInspection/inspectionRules';
import { toBox, toIso } from '@/pages/StructureDetail/dateBox';

export const IN_PROGRESS = 'PRO';
/** Rejected — an inspection reviewer's, from In Progress or Submitted, picked on this card. */
export const REJECTED = 'REJ';
/**
 * The statuses this card moves between; an inspection reviewer may also pick Rejected here, and
 * Reviewed through the Review dialog.
 */
export const CARD_STATUSES = new Set([IN_PROGRESS, 'SUB']);
/**
 * The statuses after which the page is locked — Reviewed and Accepted, not Rejected — except that
 * the Inspection card may move the status back to In Progress or Submitted (`isReopening`).
 */
export const LOCKING_STATUSES = new Set(['RVD', 'ACC']);

/**
 * Whether the card is reopening a Reviewed or Accepted inspection: only its status changes, and
 * only to In Progress or Submitted — any user who records inspections, a business request
 * (2026-11-10; legacy allowed a P.Eng only).
 */
export const isReopening = (current: string | null): boolean => LOCKING_STATUSES.has(current ?? '');

/** The card's rule when reopening: the status must move. */
export const reopenErrorsOf = (values: InspectionValues): Record<string, string | undefined> => ({
  statusCode: CARD_STATUSES.has(values.statusCode)
    ? undefined
    : 'Choose In Progress or Submitted to reopen this inspection.',
});

/** The stored inspection, as the card's form types it. */
export const valuesOf = (inspection: InspectionDetailResponse): InspectionValues => ({
  typeCode: inspection.type.code ?? '',
  statusCode: inspection.status.code ?? IN_PROGRESS,
  inspectionDate: toBox(inspection.inspectionDate),
  inspectorName: inspection.inspectorName ?? '',
  inspectorCompanyName: inspection.inspectorCompanyName ?? '',
  cost: inspection.cost === null ? '' : String(inspection.cost),
  professionalInspectionRequired: inspection.professionalInspectionRequired,
  inspectionComment: inspection.inspectionComment ?? '',
});

/**
 * Whether the user may move the status here: between In Progress and Submitted; out of Rejected
 * only a P.Eng, as legacy; out of Reviewed or Accepted, back to one of the two.
 */
export const canChangeStatus = (current: string | null, isPeng: boolean): boolean =>
  CARD_STATUSES.has(current ?? '') || (current === REJECTED && isPeng) || isReopening(current);

/**
 * The card's rules — Add Inspection's, and legacy's on an update: the date, inspector and company
 * are required once given, as well as past In Progress.
 */
export const cardErrorsOf = (
  values: InspectionValues,
  inspection: InspectionDetailResponse,
): Record<string, string | undefined> => {
  const errors = inspectionErrorsOf(values);
  const onceGiven: [keyof InspectionValues, string, unknown][] = [
    ['inspectionDate', 'Date of Inspection', inspection.inspectionDate],
    ['inspectorName', 'Inspector Name', inspection.inspectorName],
    ['inspectorCompanyName', 'Inspection Company Name', inspection.inspectorCompanyName],
  ];
  for (const [field, label, stored] of onceGiven) {
    if (stored && !String(values[field]).trim()) errors[field] = `${label} is required.`;
  }
  return errors;
};

/** The card's values as the server takes them. */
export const toUpdateRequest = (values: InspectionValues): InspectionUpdateRequest => ({
  statusCode: values.statusCode,
  inspectionDate: values.inspectionDate.trim() ? toIso(values.inspectionDate) : null,
  inspectorName: values.inspectorName,
  inspectorCompanyName: values.inspectorCompanyName,
  cost: values.cost.trim() ? Number(values.cost.trim()) : null,
  professionalInspectionRequired: values.professionalInspectionRequired,
});

/** Which of the three are required now: past In Progress, or once given. */
export const requiredOf = (values: InspectionValues, inspection: InspectionDetailResponse) => {
  const pastInProgress = values.statusCode !== IN_PROGRESS;
  return {
    inspectionDate: pastInProgress || Boolean(inspection.inspectionDate),
    inspectorName: pastInProgress || Boolean(inspection.inspectorName),
    inspectorCompanyName: pastInProgress || Boolean(inspection.inspectorCompanyName),
  };
};
