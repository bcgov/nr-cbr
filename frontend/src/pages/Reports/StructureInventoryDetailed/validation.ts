import { commonCriteriaErrors } from '../validation';

import type { StructureInventoryDetailedCriteria } from './types';

export type CriteriaErrors = Partial<Record<keyof StructureInventoryDetailedCriteria, string>>;

/**
 * Field-level messages for whatever is wrong — one per offending box, each judged on its own.
 * The Detailed has the shared boxes alone, so the shared rules are its rules.
 */
export const criteriaErrors = (criteria: StructureInventoryDetailedCriteria): CriteriaErrors =>
  commonCriteriaErrors(criteria);
