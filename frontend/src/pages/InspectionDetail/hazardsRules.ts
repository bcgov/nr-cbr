import type { InspectionDetailResponse } from './inspectionDetail';
import type { HazardValues } from '@/pages/NewInspection/inspectionRules';

const IN_PROGRESS = 'PRO';

export type Measurement = 'presentWaterDepth' | 'presentWaterWidth' | 'highWaterClearance';

export const LABELS: Record<Measurement, string> = {
  presentWaterDepth: 'Estimated Present Water Level Depth',
  presentWaterWidth: 'Estimated Present Water Level Width',
  highWaterClearance: 'Estimated High Water Level Clearance',
};

const text = (value: number | null) => (value === null ? '' : String(value));

/** The stored hazards, as the form types them. */
export const valuesOf = ({ hazards }: InspectionDetailResponse): HazardValues => ({
  presentWaterDepth: text(hazards.presentWaterDepth),
  presentWaterWidth: text(hazards.presentWaterWidth),
  highWaterClearance: text(hazards.highWaterClearance),
  highWater: hazards.highWater,
  debris: hazards.debris,
  scour: hazards.scour,
  ice: hazards.ice,
  aggradation: hazards.aggradation,
  hazardComment: hazards.comment ?? '',
});

/**
 * Which measurements legacy requires: all three past In Progress, and any already saved — so a
 * measurement once given cannot be blanked again.
 */
export const requiredMeasurementsOf = (
  inspection: InspectionDetailResponse,
): Record<Measurement, boolean> => {
  const status = inspection.status.code;
  const pastInProgress = Boolean(status) && status !== IN_PROGRESS;
  return {
    presentWaterDepth: pastInProgress || inspection.hazards.presentWaterDepth !== null,
    presentWaterWidth: pastInProgress || inspection.hazards.presentWaterWidth !== null,
    highWaterClearance: pastInProgress || inspection.hazards.highWaterClearance !== null,
  };
};
