import type { InspectionDetailResponse, InspectionUpdateRequest } from './inspectionDetail';
import type { InspectionValues } from '@/pages/NewInspection/inspectionRules';

import { inspectionErrorsOf } from '@/pages/NewInspection/inspectionRules';
import { toBox, toIso } from '@/pages/StructureDetail/dateBox';

export const IN_PROGRESS = 'PRO';
const REJECTED = 'REJ';
/** The statuses this card moves between; Reviewed and Rejected are the Review tab's. */
export const CARD_STATUSES = new Set([IN_PROGRESS, 'SUB']);
/** The statuses after which legacy's page is disabled — Reviewed and Accepted, not Rejected. */
export const LOCKING_STATUSES = new Set(['RVD', 'ACC']);

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
 * only a P.Eng, as legacy.
 */
export const canChangeStatus = (current: string | null, isPeng: boolean): boolean =>
  CARD_STATUSES.has(current ?? '') || (current === REJECTED && isPeng);

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
