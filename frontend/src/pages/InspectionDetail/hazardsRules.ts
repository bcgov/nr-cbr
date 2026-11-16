import type { InspectionDetailResponse } from './inspectionDetail';
import type { HazardValues } from '@/pages/NewInspection/inspectionRules';

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
